package com.capstoneecho.echo_back.legal;

// 약관 종류. 파일명 슬러그 (service-{version}.md, privacy-{version}.md) 와 짝지어 둔다.
public enum TermsKind {
    SERVICE("service"),
    PRIVACY("privacy");

    private final String fileSlug;

    TermsKind(String fileSlug) {
        this.fileSlug = fileSlug;
    }

    public String fileSlug() {
        return fileSlug;
    }
}
