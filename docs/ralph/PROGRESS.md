# ECHO Backend — Ralph Loop Progress

Last updated: 2026-05-11T06:55:00Z

| ID  | Title                                         | Phase | Status | Depends         | Commit |
|-----|-----------------------------------------------|-------|--------|-----------------|--------|
| 000 | foundation-app-properties                     | 0     | DONE   | -               | 2c900b1 |
| 001 | foundation-api-response-errors                | 0     | TODO   | 000             | -      |
| 002 | foundation-jwt-provider                       | 0     | TODO   | 001             | -      |
| 003 | foundation-jwt-auth-filter                    | 0     | TODO   | 002             | -      |
| 004 | foundation-security-config                    | 0     | TODO   | 003             | -      |
| 005 | foundation-http-client-config                 | 0     | TODO   | 000             | -      |
| 006 | foundation-health-controller-restdocs         | 0     | TODO   | 004             | -      |
| 007 | member-user-entity-repository                 | 1     | TODO   | 001             | -      |
| 008 | member-service-profile-nickname               | 1     | TODO   | 007             | -      |
| 009 | auth-service-signup-login-duplicates          | 1     | TODO   | 007,002         | -      |
| 010 | auth-service-oauth2-google-demo               | 1     | TODO   | 009             | -      |
| 011 | member-controllers-restdocs                   | 1     | TODO   | 008,010,006     | -      |
| 012 | learning-track-script-step-entities           | 2     | TODO   | 007             | -      |
| 013 | learning-track-service-controller-restdocs    | 2     | TODO   | 012,011         | -      |
| 014 | learning-script-service-recommender-restdocs  | 2     | TODO   | 013             | -      |
| 015 | session-entities-user-scoped-repository       | 3     | TODO   | 012             | -      |
| 016 | session-service-update-script-orphan          | 3     | TODO   | 015             | -      |
| 017 | session-controller-restdocs                   | 3     | TODO   | 016,014         | -      |
| 018 | recording-entity-3mode-factories-checks       | 4     | TODO   | 015             | -      |
| 019 | feedback-phoneme-entities-atomic-update       | 4     | TODO   | 018             | -      |
| 020 | recording-storage-interface-local-impl        | 4     | TODO   | 000             | -      |
| 021 | model-server-client-analyze-g2p               | 4     | TODO   | 005             | -      |
| 022 | llm-client-rule-based-default                 | 4     | TODO   | 019             | -      |
| 023 | recording-service-upload-txn-sync             | 4     | TODO   | 018,020,021,022 | -      |
| 024 | recording-controller-restdocs                 | 4     | TODO   | 023,017         | -      |
| 025 | feedback-service-generate-retry-complete      | 4     | TODO   | 019,022,008     | -      |
| 026 | feedback-controllers-restdocs                 | 4     | TODO   | 025,024         | -      |
| 027 | tts-client-service-controller-restdocs        | 4     | TODO   | 021,006         | -      |
| 028 | stats-demo-ranking-entity-seed                | 5     | TODO   | 007             | -      |
| 029 | stats-service-controller-restdocs             | 5     | TODO   | 025,028         | -      |
| 030 | ranking-service-controller-restdocs           | 5     | TODO   | 028,029         | -      |
| 031 | hardening-jacoco-asciidoctor-gate             | 6     | TODO   | 030,027         | -      |
| 032 | hardening-prod-profile-actuator-security      | 6     | TODO   | 031             | -      |
| 033 | hardening-final-integration                   | 6     | TODO   | 032             | -      |
