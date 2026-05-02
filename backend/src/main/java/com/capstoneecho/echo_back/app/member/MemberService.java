package com.capstoneecho.echo_back.app.member;

// 외부에서 사용자 도메인을 다루기 위한 인터페이스. 컨트롤러는 이 추상화에만 의존한다.
public interface MemberService {

    User getById(Long userId);

    // 한 학습 단위 완료 시 EXP 보상과 streak 갱신을 한 번에 적용하고 갱신된 사용자를 돌려준다.
    // 정책 자체는 User.recordCompletion 안에 캡슐화되어 있다.
    User awardCompletionRewards(Long userId, int expReward);
}
