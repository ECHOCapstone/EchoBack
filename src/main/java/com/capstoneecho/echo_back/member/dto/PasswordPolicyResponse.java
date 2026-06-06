package com.capstoneecho.echo_back.member.dto;

// 회원가입 폼에 안내할 비밀번호 정책. PasswordPolicyEvaluator 와 같은 출처 (AppProperties) 에서 만들어진다.
public record PasswordPolicyResponse(int minLength, int maxLength, int requireCategories) {
}
