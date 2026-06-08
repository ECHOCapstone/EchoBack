package com.capstoneecho.echo_back.external.llm.canonical;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// (사용자, 발화 단위) 한 쌍에 대해 첫 시도에서 만든 canonicalWords 를 그대로 고정한다.
// 이후 같은 학습자의 동일 발화 단위 시도는 lock 을 채점 LLM 의 canonical 입력으로 그대로 사용한다.
//
// lock 쓰기는 호출 트랜잭션과 무관하게 REQUIRES_NEW 로 분리해, attempt 흐름의 트랜잭션 롤백과
// 별개로 lock 은 보존된다. 동시 시도로 인한 UNIQUE 위반은 잡아 기존 lock 을 다시 조회해 돌려준다.
@Service
public class UserCanonicalLockService {

    private static final Logger log = LoggerFactory.getLogger(UserCanonicalLockService.class);

    private final UserCanonicalLockRepository repository;
    private final CanonicalJson canonicalJson;

    public UserCanonicalLockService(
            UserCanonicalLockRepository repository, CanonicalJson canonicalJson) {
        this.repository = repository;
        this.canonicalJson = canonicalJson;
    }

    // 이미 lock 이 있으면 그 canonicalWords 를 돌려준다. 없으면 Optional.empty().
    @Transactional(readOnly = true)
    public Optional<List<CanonicalWord>> findLocked(
            Long userId, CanonicalTargetType targetType, Long targetId) {
        return repository
                .findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)
                .map(lock -> canonicalJson.deserialize(lock.getCanonicalJson()));
    }

    // 새 lock 을 영속화한다. 호출 트랜잭션이 롤백돼도 lock 은 보존된다 (REQUIRES_NEW).
    // 이미 존재하면 (race) noop — 한 쪽 INSERT 만 통과하고 나머지는 기존 lock 을 그대로 둔다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void lock(
            Long userId,
            CanonicalTargetType targetType,
            Long targetId,
            List<CanonicalWord> words) {
        if (words == null || words.isEmpty()) {
            return;
        }
        if (repository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)) {
            return;
        }
        String json = canonicalJson.serialize(words);
        try {
            repository.save(UserCanonicalLock.create(userId, targetType, targetId, json));
        } catch (DataIntegrityViolationException race) {
            log.debug("canonical lock 경쟁 — 기존 lock 유지 user={} type={} id={}",
                    userId, targetType, targetId);
        }
    }
}
