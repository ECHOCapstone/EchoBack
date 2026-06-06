package com.capstoneecho.echo_back.global.security.password;

import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import org.springframework.stereotype.Component;

// 비밀번호가 정책을 만족하는지 단일 출처에서 평가한다. 정책값은 RuntimeSettings 를 통해 읽으므로
// 어드민이 "설정" 탭에서 길이/카테고리 수를 변경하면 다음 호출부터 즉시 반영된다.
// 잘못된 비밀번호의 사유(짧음 / 카테고리 부족)를 함께 돌려준다.
@Component
public class PasswordPolicyEvaluator {

    private final RuntimeSettings settings;

    public PasswordPolicyEvaluator(RuntimeSettings settings) {
        this.settings = settings;
    }

    // 현재 적용 중인 정책 스냅샷. /api/auth/password-policy 응답이 사용한다.
    public Snapshot snapshot() {
        return new Snapshot(
                settings.passwordMinLength(),
                settings.passwordMaxLength(),
                settings.passwordRequireCategories());
    }

    // null 안전. 짧음 / 카테고리 부족 시 한국어 사유와 함께 false 반환.
    public Result evaluate(String password) {
        Snapshot policy = snapshot();
        if (password == null || password.isBlank()) {
            return Result.invalid("비밀번호를 입력해 주세요.");
        }
        int length = password.length();
        if (length < policy.minLength()) {
            return Result.invalid("비밀번호는 " + policy.minLength() + "자 이상이어야 합니다.");
        }
        if (length > policy.maxLength()) {
            return Result.invalid("비밀번호는 " + policy.maxLength() + "자 이하여야 합니다.");
        }
        int categories = countCategories(password);
        if (categories < policy.requireCategories()) {
            return Result.invalid("비밀번호는 영문 소문자 / 대문자 / 숫자 / 특수문자 중 "
                    + policy.requireCategories() + "가지 이상을 포함해야 합니다.");
        }
        return Result.ok();
    }

    private static int countCategories(String password) {
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isWhitespace(c)) hasSymbol = true;
        }
        int count = 0;
        if (hasLower) count++;
        if (hasUpper) count++;
        if (hasDigit) count++;
        if (hasSymbol) count++;
        return count;
    }

    // 정책 스냅샷. yaml 기본값 위에 SettingsService 오버라이드를 덮은 값.
    public record Snapshot(int minLength, int maxLength, int requireCategories) {}

    // valid=true 면 비밀번호가 정책을 통과. valid=false 면 한국어 사유가 채워진다.
    public record Result(boolean valid, String reason) {
        public static Result ok() { return new Result(true, null); }
        public static Result invalid(String reason) { return new Result(false, reason); }
    }
}
