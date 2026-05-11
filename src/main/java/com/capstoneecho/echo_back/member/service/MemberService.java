package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.member.dto.MemberProfileResponse;
import com.capstoneecho.echo_back.member.dto.NicknameUpdateRequest;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final UserRepository userRepository;
    private final Validator validator;

    public MemberService(UserRepository userRepository, Validator validator) {
        this.userRepository = userRepository;
        this.validator = validator;
    }

    public MemberProfileResponse findMe(Long userId) {
        User user = loadUser(userId);
        return MemberProfileResponse.from(user);
    }

    @Transactional
    public MemberProfileResponse updateNickname(Long userId, NicknameUpdateRequest request) {
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
        return MemberProfileResponse.from(user);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
