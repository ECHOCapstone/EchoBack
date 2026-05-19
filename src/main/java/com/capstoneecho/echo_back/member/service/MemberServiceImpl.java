package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;

import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
@Service
@Transactional(readOnly = true)
class MemberServiceImpl implements MemberService {

    private final UserRepository repository;
    private final ZoneId learningZone;

    MemberServiceImpl(UserRepository repository, ZoneId learningZoneId) {
        this.repository = repository;
        this.learningZone = learningZoneId;
    }

    @Override
    public User getById(Long userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public User awardCompletionRewards(Long userId, int expReward) {
        var user = repository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.recordCompletion(Instant.now(), expReward, learningZone);
        return user;
    }

    @Override
    @Transactional
    public User updateNickname(Long userId, String nickname) {
        var user = repository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.updateNickname(nickname);
        return user;
    }
}
