package com.capstoneecho.echo_back.app.member;

// 외부에서 사용자 도메인을 다루기 위한 인터페이스. 컨트롤러는 이 추상화에만 의존한다.
public interface MemberService {

    User getById(Long userId);
}
