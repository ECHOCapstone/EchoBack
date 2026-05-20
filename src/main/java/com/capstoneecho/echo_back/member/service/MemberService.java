package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.common.RequestValidator;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.config.StatsZoneProvider;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.dto.NicknameUpdateRequest;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final UserRepository userRepository;
    private final RequestValidator requestValidator;
    private final StatsZoneProvider statsZoneProvider;
    private final int streakCap;

    public MemberService(
            UserRepository userRepository,
            RequestValidator requestValidator,
            StatsZoneProvider statsZoneProvider,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.requestValidator = requestValidator;
        this.statsZoneProvider = statsZoneProvider;
        AppProperties.Gamification g = appProperties.gamification();
        this.streakCap = g == null ? 7 : g.streakCap();
    }

    public UserResponse findMe(Long userId) {
        User user = loadUser(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateNickname(Long userId, NicknameUpdateRequest request) {
        requestValidator.validate(request);
        User user = loadUser(userId);
        user.updateNickname(request.nickname());
        return UserResponse.from(user);
    }

    // 학습 완료 보상을 적용한다. EXP 가산 + streak 갱신은 도메인 객체 (User) 에 위임한다.
    @Transactional
    public UserResponse awardCompletionRewards(Long userId, int expReward) {
        if (expReward < 0) {
            throw new IllegalArgumentException("expReward must be >= 0");
        }
        User user = loadUser(userId);
        user.recordCompletion(Instant.now(), expReward, streakCap, statsZoneProvider.zone());
        return UserResponse.from(user);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
