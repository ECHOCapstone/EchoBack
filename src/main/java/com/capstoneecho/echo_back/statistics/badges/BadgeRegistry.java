package com.capstoneecho.echo_back.statistics.badges;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.content.PersistableContentStore;
import com.capstoneecho.echo_back.global.content.PersistableSeed;
import com.capstoneecho.echo_back.global.content.SeedFileLocations;
import com.capstoneecho.echo_back.global.content.SeedFileStatus;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

// 시스템 배지 정의의 단일 출처.
// 부팅 시 영구 저장본(badges.yaml) 이 있으면 우선 로드, 없으면 application.yaml 의 app.stats.badges 로 폴백한다.
// 어드민이 add/update/delete 하는 모든 변경은 in-memory 리스트에 즉시 반영되고, "영구 저장" 누르면 외부 파일로 dump.
// "공장 기본값 복원" 은 yaml 의 초기 정의로 되돌린다.
@Component
public class BadgeRegistry implements PersistableSeed {

    private static final Logger log = LoggerFactory.getLogger(BadgeRegistry.class);
    private static final String DOMAIN = "badges";

    private final List<BadgeDef> factoryDefaults;
    private final PersistableContentStore contentStore;
    private final CopyOnWriteArrayList<BadgeDef> store = new CopyOnWriteArrayList<>();
    private final Object writeLock = new Object();

    public BadgeRegistry(AppProperties appProperties, PersistableContentStore contentStore) {
        this.contentStore = contentStore;
        AppProperties.Stats stats = appProperties.stats();
        List<AppProperties.Stats.Badge> ymlBadges = stats == null ? List.of() : stats.safeBadges();
        List<BadgeDef> defaults = new ArrayList<>(ymlBadges.size());
        for (AppProperties.Stats.Badge b : ymlBadges) {
            // condition 이 알 수 없는 식별자면 skip. application.yaml 에 오타가 있어도 부팅이 죽지 않는다.
            if (BadgeCondition.isKnown(b.condition())) {
                defaults.add(new BadgeDef(b.id(), b.name(), b.condition(), b.threshold()));
            } else {
                log.warn("배지 '{}' 의 condition '{}' 가 알 수 없어 무시합니다.", b.id(), b.condition());
            }
        }
        this.factoryDefaults = List.copyOf(defaults);
    }

    @PostConstruct
    void init() {
        if (contentStore.exists(SeedFileLocations.BADGES)) {
            List<BadgeDef> persisted = readPersisted();
            if (persisted.isEmpty()) {
                store.addAll(factoryDefaults);
                log.info("영구 저장본이 비어 공장 기본값 {}개로 시작합니다.", factoryDefaults.size());
            } else {
                store.addAll(persisted);
                log.info("badges.yaml 영구 저장본 {}개를 로드했습니다.", persisted.size());
            }
        } else {
            store.addAll(factoryDefaults);
            log.info("영구 저장본 없음 — application.yaml 의 기본 배지 {}개로 시작합니다.", factoryDefaults.size());
        }
    }

    public List<BadgeDef> list() {
        return List.copyOf(store);
    }

    public BadgeDef get(String id) {
        return store.stream().filter(b -> b.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "존재하지 않는 배지: " + id));
    }

    public BadgeDef add(BadgeDef def) {
        Objects.requireNonNull(def);
        if (!BadgeCondition.isKnown(def.condition())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 condition: " + def.condition());
        }
        synchronized (writeLock) {
            if (store.stream().anyMatch(b -> b.id().equals(def.id()))) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 존재하는 배지 id: " + def.id());
            }
            store.add(def);
        }
        return def;
    }

    public BadgeDef update(String id, BadgeDef def) {
        Objects.requireNonNull(def);
        if (!BadgeCondition.isKnown(def.condition())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 condition: " + def.condition());
        }
        // PATCH 의미 — id 변경은 허용하지 않는다 (URI 의 id 가 단일 출처).
        BadgeDef updated = new BadgeDef(id, def.name(), def.condition(), def.threshold());
        synchronized (writeLock) {
            int idx = indexOf(id);
            if (idx < 0) throw new BusinessException(ErrorCode.INVALID_REQUEST, "존재하지 않는 배지: " + id);
            store.set(idx, updated);
        }
        return updated;
    }

    public void delete(String id) {
        synchronized (writeLock) {
            int idx = indexOf(id);
            if (idx < 0) throw new BusinessException(ErrorCode.INVALID_REQUEST, "존재하지 않는 배지: " + id);
            store.remove(idx);
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < store.size(); i++) {
            if (store.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    // ----- PersistableSeed -----

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public boolean supportsExplicitPersist() {
        return true;
    }

    @Override
    public void persistCurrentStateToFile() {
        contentStore.writeText(SeedFileLocations.BADGES, toYaml(list()));
    }

    @Override
    public void resetToDefaults() {
        synchronized (writeLock) {
            store.clear();
            store.addAll(factoryDefaults);
        }
    }

    @Override
    public SeedFileStatus fileStatus() {
        return contentStore.statusOf(SeedFileLocations.BADGES);
    }

    // ----- 직렬화 / 역직렬화 -----

    @SuppressWarnings("unchecked")
    private List<BadgeDef> readPersisted() {
        try (InputStream in = Files.newInputStream(contentStore.resolve(SeedFileLocations.BADGES))) {
            Object tree = new Yaml().load(in);
            if (!(tree instanceof List<?> rawList)) {
                return List.of();
            }
            List<BadgeDef> defs = new ArrayList<>(rawList.size());
            for (Object item : rawList) {
                if (!(item instanceof Map<?, ?> map)) continue;
                String id = stringOf(map.get("id"));
                String name = stringOf(map.get("name"));
                String condition = stringOf(map.get("condition"));
                int threshold = intOf(map.get("threshold"));
                if (id == null || name == null || !BadgeCondition.isKnown(condition)) continue;
                defs.add(new BadgeDef(id, name, condition, threshold));
            }
            return defs;
        } catch (IOException e) {
            throw new IllegalStateException("배지 영구 저장본을 읽을 수 없습니다.", e);
        }
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intOf(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String toYaml(List<BadgeDef> defs) {
        // SnakeYAML 이 BadgeDef record 자체를 잘 dump 못하므로 List<Map> 로 변환해 출력한다.
        List<Map<String, Object>> raw = new ArrayList<>(defs.size());
        for (BadgeDef def : defs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", def.id());
            m.put("name", def.name());
            m.put("condition", def.condition());
            m.put("threshold", def.threshold());
            raw.add(m);
        }
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        return new Yaml(options).dump(raw);
    }

    // BadgePolicy 가 iteration 용으로 직접 받을 수 있게 read-only Iterator 도 제공한다.
    public Iterator<BadgeDef> iterator() {
        return list().iterator();
    }
}
