package com.capstoneecho.echo_back.global.content;

// 외부 콘텐츠 디렉터리(app.content.dir) 안에서 각 도메인의 영구 저장본이 사용하는 상대 경로.
// 새 도메인을 추가할 때는 여기 상수만 늘리면 PersistableContentStore 가 그대로 받는다.
public final class SeedFileLocations {

    public static final String TRACKS = "tracks.yaml";
    public static final String SETTINGS_OVERRIDES = "settings-overrides.yaml";
    public static final String PROMPTS_DIR = "prompts";
    public static final String BADGES = "badges.yaml";

    private SeedFileLocations() {}

    public static String promptFile(String key) {
        return PROMPTS_DIR + "/" + key + ".md";
    }
}
