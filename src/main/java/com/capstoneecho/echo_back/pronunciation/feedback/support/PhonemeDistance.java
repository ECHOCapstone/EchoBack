package com.capstoneecho.echo_back.pronunciation.feedback.support;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// ARPABET 음소 쌍의 음성학적 자질 거리(0~1)를 계산한다. 채점에서 치환(SUBSTITUTION)의 심각도를
// 변별하는 데 쓴다: 가까운 치환(예: 모음 AE→EH)은 작게, 먼 치환(예: R→T, 모음↔자음)은 크게.
//
// 자질:
//   자음 = (유성성, 조음위치, 조음방법)
//   모음 = (높이, 전후, 원순, r성)
// 거리: 같은 음소 0, 자음↔모음 1.0, 같은 부류는 자질 차이를 정규화해 평균.
//
// 한국인 학습자 약점(R/L, F/V, TH 등)은 자질상 가까워도 교육적으로 중요하므로, 가중치(weakMultiplier)
// 는 ScoringService 가 별도로 곱한다 — 본 거리표는 순수 음성학적 차이만 담당한다.
public final class PhonemeDistance {

    private PhonemeDistance() {}

    private enum Kind { VOWEL, CONSONANT }

    // 자음: f1=유성성(0/1), f2=조음위치(0~7), f3=조음방법(0~5), f4 미사용.
    //   위치: 0 양순 1 순치 2 치 3 치경 4 후치경 5 경구개 6 연구개 7 성문
    //   방법: 0 파열 1 파찰 2 마찰 3 비음 4 유음 5 활음
    // 모음: f1=높이(0 저/1 중/2 고), f2=전후(0 전/1 중/2 후), f3=원순(0/1), f4=r성(0/1).
    private record Phon(Kind kind, int f1, int f2, int f3, int f4) {}

    private static final Map<String, Phon> FEATURES = new HashMap<>();

    private static void cons(String p, int voicing, int place, int manner) {
        FEATURES.put(p, new Phon(Kind.CONSONANT, voicing, place, manner, 0));
    }

    private static void vowel(String p, int height, int backness, int rounded, int rhotic) {
        FEATURES.put(p, new Phon(Kind.VOWEL, height, backness, rounded, rhotic));
    }

    static {
        // ----- 자음 (유성성, 위치, 방법) -----
        cons("B", 1, 0, 0);  cons("P", 0, 0, 0);
        cons("D", 1, 3, 0);  cons("T", 0, 3, 0);
        cons("G", 1, 6, 0);  cons("K", 0, 6, 0);
        cons("M", 1, 0, 3);  cons("N", 1, 3, 3);  cons("NG", 1, 6, 3);
        cons("F", 0, 1, 2);  cons("V", 1, 1, 2);
        cons("TH", 0, 2, 2); cons("DH", 1, 2, 2);
        cons("S", 0, 3, 2);  cons("Z", 1, 3, 2);
        cons("SH", 0, 4, 2); cons("ZH", 1, 4, 2);
        cons("HH", 0, 7, 2);
        cons("CH", 0, 4, 1); cons("JH", 1, 4, 1);
        cons("L", 1, 3, 4);  cons("R", 1, 3, 4);
        cons("W", 1, 6, 5);  cons("Y", 1, 5, 5);

        // ----- 모음 (높이, 전후, 원순, r성) -----
        vowel("IY", 2, 0, 0, 0); vowel("IH", 2, 0, 0, 0);
        vowel("EY", 1, 0, 0, 0); vowel("EH", 1, 0, 0, 0);
        vowel("AE", 0, 0, 0, 0);
        vowel("AA", 0, 2, 0, 0);
        vowel("AH", 1, 1, 0, 0);
        vowel("AO", 1, 2, 1, 0); vowel("OW", 1, 2, 1, 0);
        vowel("UH", 2, 2, 1, 0); vowel("UW", 2, 2, 1, 0);
        vowel("ER", 1, 1, 0, 1);
        // 이중모음은 대표 자질로 근사한다.
        vowel("AW", 0, 2, 1, 0); vowel("AY", 0, 0, 0, 0); vowel("OY", 1, 2, 1, 0);
    }

    // canonical↔perceived 음소의 자질 거리 (0~1). 미상 음소는 안전하게 최대(1.0)로 본다.
    public static double distance(String a, String b) {
        if (a == null || b == null) {
            return 1.0;
        }
        String x = a.trim().toUpperCase(Locale.ROOT);
        String y = b.trim().toUpperCase(Locale.ROOT);
        if (x.equals(y)) {
            return 0.0;
        }
        Phon pa = FEATURES.get(x);
        Phon pb = FEATURES.get(y);
        if (pa == null || pb == null || pa.kind() != pb.kind()) {
            return 1.0;
        }
        double d;
        if (pa.kind() == Kind.CONSONANT) {
            d = (Math.abs(pa.f1() - pb.f1())
                    + Math.abs(pa.f2() - pb.f2()) / 7.0
                    + Math.abs(pa.f3() - pb.f3()) / 5.0) / 3.0;
        } else {
            d = (Math.abs(pa.f1() - pb.f1()) / 2.0
                    + Math.abs(pa.f2() - pb.f2()) / 2.0
                    + Math.abs(pa.f3() - pb.f3())
                    + Math.abs(pa.f4() - pb.f4())) / 4.0;
        }
        return Math.max(0.0, Math.min(1.0, d));
    }
}
