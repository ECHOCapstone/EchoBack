package com.capstoneecho.echo_back.challenge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.challenge.entity.DailyChallenge;
import com.capstoneecho.echo_back.challenge.repository.DailyChallengeRepository;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

// ChallengeService 의 생성 시 canonical 주입 / 활성-비활성 라이프사이클 / requireActive / requireById 검증.
class ChallengeServiceTest {

    private DailyChallengeRepository challengeRepository;
    private ChallengeRewardService rewardService;
    private LlmCanonicalGenerator canonicalGenerator;
    private CanonicalJson canonicalJson;
    private ChallengeService service;

    @BeforeEach
    void setUp() {
        challengeRepository = mock(DailyChallengeRepository.class);
        rewardService = mock(ChallengeRewardService.class);
        canonicalGenerator = mock(LlmCanonicalGenerator.class);
        canonicalJson = new CanonicalJson(new ObjectMapper());
        service = new ChallengeService(
                challengeRepository, rewardService, canonicalGenerator, canonicalJson);
    }

    @Test
    @DisplayName("create: canonical 생성 성공 → 엔티티에 JSON 채워서 저장")
    void createSavesWithCanonical() {
        when(canonicalGenerator.generate("the event")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("the", List.of("DH", "IY")),
                        new CanonicalWord("event", List.of("IH", "V", "EH", "N", "T")))));
        when(challengeRepository.save(any(DailyChallenge.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyChallenge saved = service.create("the event", "그 행사", false);

        assertThat(saved.getTargetText()).isEqualTo("the event");
        assertThat(saved.getCanonicalCachedJson()).contains("the").contains("DH").contains("IY");
        verify(canonicalGenerator).generate("the event");
    }

    @Test
    @DisplayName("create: canonical 응답이 비면 CANONICAL_GENERATION_FAILED — challenge 저장 자체 거부")
    void createWithEmptyCanonicalFails() {
        when(canonicalGenerator.generate(anyString())).thenReturn(new CanonicalResult(List.of()));

        assertThatThrownBy(() -> service.create("the", "그", false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CANONICAL_GENERATION_FAILED));
        verify(challengeRepository, never()).save(any(DailyChallenge.class));
    }

    @Test
    @DisplayName("create(activate=true): 저장 후 activate 까지 호출")
    void createWithActivateTrue() {
        when(canonicalGenerator.generate(anyString())).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("hi", List.of("HH", "AY")))));
        DailyChallenge saved = challengeMock(11L, false);
        when(challengeRepository.save(any(DailyChallenge.class))).thenReturn(saved);
        when(challengeRepository.findById(11L)).thenReturn(Optional.of(saved));
        when(challengeRepository.findAllByActiveTrue()).thenReturn(List.of());

        DailyChallenge result = service.create("hi", "안녕", true);

        verify(saved).activate();
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("activate: 이미 활성이면 그대로 돌려준다 (no-op)")
    void activateAlreadyActiveIsNoop() {
        DailyChallenge active = challengeMock(11L, true);
        when(challengeRepository.findById(11L)).thenReturn(Optional.of(active));

        DailyChallenge result = service.activate(11L);

        assertThat(result).isSameAs(active);
        verify(active, never()).activate();
        verify(challengeRepository, never()).findAllByActiveTrue();
    }

    @Test
    @DisplayName("activate: 기존 활성 챌린지가 있으면 닫고 reward 발급 후 새 챌린지 활성화")
    void activateClosesExistingActiveAndAwards() {
        DailyChallenge target = challengeMock(11L, false);
        DailyChallenge prev = challengeMock(10L, true);
        when(challengeRepository.findById(11L)).thenReturn(Optional.of(target));
        when(challengeRepository.findAllByActiveTrue()).thenReturn(List.of(prev));

        service.activate(11L);

        verify(prev).deactivate();
        verify(rewardService).awardRanksForChallenge(prev);
        verify(target).activate();
    }

    @Test
    @DisplayName("activate: 존재하지 않으면 CHALLENGE_NOT_FOUND")
    void activateMissingThrows() {
        when(challengeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CHALLENGE_NOT_FOUND));
    }

    @Test
    @DisplayName("deactivate: 활성이면 닫고 reward 발급")
    void deactivateActiveAwards() {
        DailyChallenge active = challengeMock(11L, true);
        when(challengeRepository.findById(11L)).thenReturn(Optional.of(active));

        service.deactivate(11L);

        verify(active).deactivate();
        verify(rewardService).awardRanksForChallenge(active);
    }

    @Test
    @DisplayName("deactivate: 이미 비활성이면 noop")
    void deactivateInactiveIsNoop() {
        DailyChallenge inactive = challengeMock(11L, false);
        when(challengeRepository.findById(11L)).thenReturn(Optional.of(inactive));

        service.deactivate(11L);

        verify(inactive, never()).deactivate();
        verify(rewardService, never()).awardRanksForChallenge(any());
    }

    @Test
    @DisplayName("deactivate: 존재하지 않으면 CHALLENGE_NOT_FOUND")
    void deactivateMissingThrows() {
        when(challengeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("findActive / requireActive 동작")
    void activeQuery() {
        DailyChallenge active = challengeMock(11L, true);
        when(challengeRepository.findFirstByActiveTrueOrderByActivatedAtDesc())
                .thenReturn(Optional.of(active));

        assertThat(service.findActive()).contains(active);
        assertThat(service.requireActive()).isSameAs(active);
    }

    @Test
    @DisplayName("requireActive: 활성 챌린지가 없으면 CHALLENGE_INACTIVE")
    void requireActiveWhenNoneThrows() {
        when(challengeRepository.findFirstByActiveTrueOrderByActivatedAtDesc())
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActive())
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CHALLENGE_INACTIVE));
    }

    @Test
    @DisplayName("requireById: 존재 → 그대로, 미존재 → CHALLENGE_NOT_FOUND")
    void requireByIdBranches() {
        DailyChallenge c = challengeMock(11L, false);
        when(challengeRepository.findById(11L)).thenReturn(Optional.of(c));

        assertThat(service.requireById(11L)).isSameAs(c);

        when(challengeRepository.findById(12L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireById(12L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("findAllOrderedByCreatedDesc: limit >= 1 강제, repository 로 위임")
    void findAllOrderedDelegates() {
        org.springframework.data.domain.Page<DailyChallenge> page =
                new org.springframework.data.domain.PageImpl<>(List.of(
                        challengeMock(1L, false), challengeMock(2L, false)));
        when(challengeRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        List<DailyChallenge> result = service.findAllOrderedByCreatedDesc(10);

        assertThat(result).hasSize(2);
        // limit 0 / 음수도 1 로 clamp 되어 page request 가 깨지지 않는다.
        service.findAllOrderedByCreatedDesc(0);
        service.findAllOrderedByCreatedDesc(-3);
        verify(challengeRepository, times(3)).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    private static DailyChallenge challengeMock(long id, boolean active) {
        DailyChallenge c = mock(DailyChallenge.class);
        when(c.getId()).thenReturn(id);
        when(c.isActive()).thenReturn(active);
        return c;
    }
}
