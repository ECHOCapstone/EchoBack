package com.capstoneecho.echo_back.pronunciation.recording.service;

import com.capstoneecho.echo_back.external.llm.WrongWord;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PriorAttemptAssembler;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingHistoryResponse;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// "이어서 학습" 진입 시 학습자에게 자신의 이전 시도를 채팅 카드로 다시 보여주기 위한 응답을 만든다.
// 한 step / sentence 마다 가장 최근 녹음 한 건을 골라 압축 피드백 데이터로 변환한다.
// RecordingService 와 책임이 분리되어 있다 (SRP) — 이 서비스는 읽기 전용이며 외부 호출이 없다.
@Service
@Transactional(readOnly = true)
public class RecordingHistoryService {

    // wrongWords JSON 역직렬화 대상 타입.
    private static final TypeReference<List<WrongWord>> WRONG_WORDS_TYPE = new TypeReference<>() {};

    private final RecordingRepository recordingRepository;
    private final ScriptRepository scriptRepository;
    private final SessionRepository sessionRepository;
    private final RuntimeSettings settings;
    private final PriorAttemptAssembler priorAttemptAssembler;
    private final ObjectMapper objectMapper;

    public RecordingHistoryService(
            RecordingRepository recordingRepository,
            ScriptRepository scriptRepository,
            SessionRepository sessionRepository,
            RuntimeSettings settings,
            PriorAttemptAssembler priorAttemptAssembler,
            ObjectMapper objectMapper) {
        this.recordingRepository = recordingRepository;
        this.scriptRepository = scriptRepository;
        this.sessionRepository = sessionRepository;
        this.settings = settings;
        this.priorAttemptAssembler = priorAttemptAssembler;
        this.objectMapper = objectMapper;
    }

    public RecordingHistoryResponse forScript(Long userId, Long scriptId) {
        if (!scriptRepository.existsById(scriptId)) {
            throw new BusinessException(ErrorCode.SCRIPT_NOT_FOUND);
        }
        List<Recording> recordings =
                recordingRepository.findAllChapterRecordingsWithStep(userId, scriptId);
        return buildResponse(recordings, r -> r.getStep().getId());
    }

    public RecordingHistoryResponse forSession(Long userId, Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        List<Recording> recordings =
                recordingRepository.findAllSessionRecordingsWithSentence(userId, sessionId);
        return buildResponse(recordings, r -> r.getSessionSentence().getId());
    }

    // 같은 step / sentence 의 여러 시도 중 가장 최근 것 하나씩만 남겨 응답 Item 으로 변환한다.
    // 입력은 createdAt ASC 라 LinkedHashMap.put 이 자연스럽게 같은 키의 옛 값을 최신으로 덮어쓴다.
    private RecordingHistoryResponse buildResponse(List<Recording> recordings, StepKeyExtractor keyExtractor) {
        Map<Long, Recording> latestByStepKey = new LinkedHashMap<>();
        for (Recording r : recordings) {
            Long key = keyExtractor.extract(r);
            if (key == null) continue;
            latestByStepKey.put(key, r);
        }
        List<RecordingHistoryResponse.Item> items = new ArrayList<>(latestByStepKey.size());
        for (Recording r : latestByStepKey.values()) {
            items.add(toItem(r, keyExtractor));
        }
        return new RecordingHistoryResponse(settings.passThreshold(), items);
    }

    private RecordingHistoryResponse.Item toItem(Recording r, StepKeyExtractor keyExtractor) {
        return new RecordingHistoryResponse.Item(
                r.getId(),
                keyExtractor.extract(r),
                r.getStepScore(),
                r.getGuidanceKr() == null ? "" : r.getGuidanceKr(),
                splitTokens(r.getPerceived()),
                splitTokens(r.getCanonical()),
                priorAttemptAssembler.parseErrors(r.getErrorsJson()),
                parseWrongWords(r.getWrongWordsJson()),
                r.getCreatedAt());
    }

    // 공백으로 잇어 저장한 음소 시퀀스를 다시 토큰 리스트로 풀어낸다. null / blank 입력은 빈 리스트.
    private static List<String> splitTokens(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return List.of(joined.trim().split("\\s+"));
    }

    // wrongWords JSON 캐시를 복원한다. 손상된 입력은 빈 리스트로 폴백 — 학습 흐름이 막히지 않게 한다.
    private List<WrongWord> parseWrongWords(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, WRONG_WORDS_TYPE);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    @FunctionalInterface
    private interface StepKeyExtractor {
        Long extract(Recording recording);
    }
}
