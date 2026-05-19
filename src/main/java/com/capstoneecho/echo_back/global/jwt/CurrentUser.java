package com.capstoneecho.echo_back.global.jwt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 컨트롤러 메서드 파라미터에 붙이면 SecurityContext 의 JwtPrincipal 이 주입된다.
// 인증되지 않은 호출은 SecurityFilterChain 단계에서 401 처리되므로 컨트롤러는
// 항상 유효한 principal 만 다룬다.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
