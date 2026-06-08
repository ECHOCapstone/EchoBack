package com.capstoneecho.echo_back.external.llm.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

// UserCanonicalLockService 의 lock 조회 / lock 저장 / race 처리 동작을 mock repo 로 검증한다.
class UserCanonicalLockServiceTest {

    private UserCanonicalLockRepository repository;
    private UserCanonicalLockService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(UserCanonicalLockRepository.class);
        service = new UserCanonicalLockService(repository, new CanonicalJson(new ObjectMapper()));
    }

    @Test
    @DisplayName("findLocked: 존재하면 canonicalWords 를 역직렬화해 돌려준다")
    void findLockedDeserializesJson() {
        String json = "[{\"word\":\"the\",\"phonemes\":[\"DH\",\"IY\"]}]";
        UserCanonicalLock lock =
                UserCanonicalLock.create(1L, CanonicalTargetType.STEP, 10L, json);
        when(repository.findByUserIdAndTargetTypeAndTargetId(1L, CanonicalTargetType.STEP, 10L))
                .thenReturn(Optional.of(lock));

        Optional<List<CanonicalWord>> found = service.findLocked(1L, CanonicalTargetType.STEP, 10L);

        assertThat(found).isPresent();
        assertThat(found.get()).hasSize(1);
        assertThat(found.get().get(0).word()).isEqualTo("the");
        assertThat(found.get().get(0).phonemes()).containsExactly("DH", "IY");
    }

    @Test
    @DisplayName("findLocked: 없으면 Optional.empty()")
    void findLockedMissingReturnsEmpty() {
        when(repository.findByUserIdAndTargetTypeAndTargetId(1L, CanonicalTargetType.STEP, 10L))
                .thenReturn(Optional.empty());

        assertThat(service.findLocked(1L, CanonicalTargetType.STEP, 10L)).isEmpty();
    }

    @Test
    @DisplayName("lock: 빈 canonical 입력은 영속화하지 않는다")
    void lockEmptyIsNoop() {
        service.lock(1L, CanonicalTargetType.STEP, 10L, List.of());
        verify(repository, never()).save(any(UserCanonicalLock.class));
    }

    @Test
    @DisplayName("lock: 이미 존재하면 저장하지 않는다 (existsBy 로 사전 차단)")
    void lockExistingIsNoop() {
        when(repository.existsByUserIdAndTargetTypeAndTargetId(1L, CanonicalTargetType.STEP, 10L))
                .thenReturn(true);

        service.lock(1L, CanonicalTargetType.STEP, 10L,
                List.of(new CanonicalWord("the", List.of("DH", "IY"))));

        verify(repository, never()).save(any(UserCanonicalLock.class));
    }

    @Test
    @DisplayName("lock: race 로 UNIQUE 위반이 나도 예외를 삼키고 다음 호출이 정상 진행된다")
    void lockRaceIsAbsorbed() {
        when(repository.existsByUserIdAndTargetTypeAndTargetId(1L, CanonicalTargetType.STEP, 10L))
                .thenReturn(false);
        when(repository.save(any(UserCanonicalLock.class)))
                .thenThrow(new DataIntegrityViolationException("uk_ucl_user_target"));

        service.lock(1L, CanonicalTargetType.STEP, 10L,
                List.of(new CanonicalWord("the", List.of("DH", "IY"))));

        verify(repository, times(1)).save(any(UserCanonicalLock.class));
    }

    @Test
    @DisplayName("lock: 새 lock 은 canonicalWords 를 JSON 으로 직렬화해 저장한다")
    void lockSerializesAndSaves() {
        when(repository.existsByUserIdAndTargetTypeAndTargetId(2L, CanonicalTargetType.CHALLENGE, 99L))
                .thenReturn(false);

        service.lock(2L, CanonicalTargetType.CHALLENGE, 99L,
                List.of(new CanonicalWord("hi", List.of("HH", "AY"))));

        verify(repository, times(1)).save(any(UserCanonicalLock.class));
    }
}
