package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.dto.UserResponse;
import com.capstoneecho.echo_back.member.dto.NicknameUpdateRequest;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final UserRepository userRepository;
    private final Validator validator;
    private final AppProperties appProperties;

    public MemberService(
            UserRepository userRepository, Validator validator, AppProperties appProperties) {
        this.userRepository = userRepository;
        this.validator = validator;
        this.appProperties = appProperties;
    }

    public UserResponse findMe(Long userId) {
        User user = loadUser(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateNickname(Long userId, NicknameUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Set<ConstraintViolation<NicknameUpdateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }

        User user = loadUser(userId);
        user.updateNickname(request.nickname());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse awardCompletionRewards(Long userId, int expReward) {
        if (expReward < 0) {
            throw new IllegalArgumentException("expReward must be >= 0");
        }
        User user = loadUser(userId);
        user.recordCompletion(Instant.now(), expReward, statsZone());
        return UserResponse.from(user);
    }

    private ZoneId statsZone() {
        String zone = appProperties.stats() == null ? null : appProperties.stats().zone();
        return (zone == null || zone.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(zone);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
