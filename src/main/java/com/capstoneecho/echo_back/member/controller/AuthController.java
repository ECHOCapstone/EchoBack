package com.capstoneecho.echo_back.member.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.security.password.PasswordPolicyEvaluator;
import com.capstoneecho.echo_back.member.dto.AuthTokenResponse;
import com.capstoneecho.echo_back.member.dto.AvailabilityResponse;
import com.capstoneecho.echo_back.member.dto.EmailCheckRequest;
import com.capstoneecho.echo_back.member.dto.LoginRequest;
import com.capstoneecho.echo_back.member.dto.OAuth2SignupCompleteRequest;
import com.capstoneecho.echo_back.member.dto.PasswordPolicyResponse;
import com.capstoneecho.echo_back.member.dto.SignupRequest;
import com.capstoneecho.echo_back.member.dto.UsernameCheckRequest;
import com.capstoneecho.echo_back.member.service.AuthService;
import com.capstoneecho.echo_back.member.service.OAuth2SignupService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // OAuth providers 응답 — provider id 와 사용자 친화적 라벨, 가용 여부.
    public record OAuth2ProviderInfo(String id, String label, boolean available) {}
    public record OAuth2ProvidersResponse(List<OAuth2ProviderInfo> providers) {}

    // 백엔드가 알고 있는 모든 provider 목록의 단일 출처. 라벨은 사용자 친화 한국어.
    private static final List<OAuth2ProviderInfo> KNOWN_PROVIDERS = List.of(
            new OAuth2ProviderInfo("google", "Google", false),
            new OAuth2ProviderInfo("kakao", "Kakao", false)
    );

    private final AuthService authService;
    private final OAuth2SignupService oauth2SignupService;
    private final PasswordPolicyEvaluator passwordPolicyEvaluator;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public AuthController(
            AuthService authService,
            OAuth2SignupService oauth2SignupService,
            PasswordPolicyEvaluator passwordPolicyEvaluator,
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        this.authService = authService;
        this.oauth2SignupService = oauth2SignupService;
        this.passwordPolicyEvaluator = passwordPolicyEvaluator;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        AuthTokenResponse token = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(token));
    }

    // OAuth2 신규 사용자가 가입 폼을 완료할 때 호출한다. pendingToken 으로 신원을 복원하고
    // 사용자가 직접 입력한 username/nickname/agreedTerms 를 합쳐 User + SocialAccount 를 생성한다.
    @PostMapping("/oauth2/signup-complete")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> oauth2SignupComplete(
            @Valid @RequestBody OAuth2SignupCompleteRequest request) {
        AuthTokenResponse token = oauth2SignupService.complete(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(token));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/check-username")
    public ApiResponse<AvailabilityResponse> checkUsername(
            @Valid @RequestBody UsernameCheckRequest request) {
        return ApiResponse.success(authService.checkUsername(request.value()));
    }

    @PostMapping("/check-email")
    public ApiResponse<AvailabilityResponse> checkEmail(
            @Valid @RequestBody EmailCheckRequest request) {
        return ApiResponse.success(authService.checkEmail(request.value()));
    }

    // 회원가입 폼이 비밀번호 정책을 안내할 때 호출한다 — 정책 변경 시 BE/FE 가 자동으로 일관 동작한다.
    @GetMapping("/password-policy")
    public ApiResponse<PasswordPolicyResponse> passwordPolicy() {
        var policy = passwordPolicyEvaluator.snapshot();
        return ApiResponse.success(new PasswordPolicyResponse(
                policy.minLength(), policy.maxLength(), policy.requireCategories()));
    }

    // 사용 가능한 OAuth provider 목록. application.yaml 에 client-id 가 채워진 것만 available=true.
    // 프론트가 이 응답대로 소셜 로그인 버튼을 활성/비활성한다.
    @GetMapping("/oauth2/providers")
    public ApiResponse<OAuth2ProvidersResponse> oauth2Providers() {
        List<OAuth2ProviderInfo> out = new ArrayList<>(KNOWN_PROVIDERS.size());
        for (OAuth2ProviderInfo info : KNOWN_PROVIDERS) {
            ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(info.id());
            boolean ready = registration != null
                    && registration.getClientId() != null
                    && !registration.getClientId().isBlank();
            out.add(new OAuth2ProviderInfo(info.id(), info.label(), ready));
        }
        return ApiResponse.success(new OAuth2ProvidersResponse(out));
    }

    // stateless JWT 라 서버 측 세션을 무효화할 게 없다. 프론트가 토큰을 정리하면 그것이 곧 로그아웃.
    // 표준 인터페이스로 두어 향후 refresh token / token blacklist 도입 시 동일 엔드포인트를 확장한다.
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success(null);
    }
}
