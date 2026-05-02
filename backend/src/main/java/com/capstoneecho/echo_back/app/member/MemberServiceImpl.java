package com.capstoneecho.echo_back.app.member;

import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class MemberServiceImpl implements MemberService {

    private final UserRepository repository;

    MemberServiceImpl(UserRepository repository) {
        this.repository = repository;
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
        user.recordCompletion(expReward);
        return user;
    }

    @Override
    @Transactional
    public User updateNickname(Long userId, String nickname) {
        var user = repository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeNickname(nickname);
        return user;
    }
}
