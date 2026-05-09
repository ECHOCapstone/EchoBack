# Model Server API Spec

> **Source**: `develop` @ `f76234d36373cf742ca8fd461fa0d6f3c7a0af4a`
> **Reference file**: `backend/src/main/java/com/capstoneecho/echo_back/app/feedback/RestClientModelServerClient.java`
> **작성일**: 2026-05-10
> **상태**: 자체완결(self-contained). 본 문서의 모든 단정문은 위 커밋의 코드(클라이언트 + DTO + 설정)로 역추적 가능하다. 코드만으로 단정할 수 없는 사항은 §6 Open Questions 에 분리했다.

---

## 1. Overview

본 문서는 ECHO 백엔드(Spring Boot) 가 외부 **모델 서버(uvicorn / FastAPI 추정)** 에 보내는 HTTP 호출 계약을 정의한다. 백엔드는 `RestClientModelServerClient` 를 단일 진입점으로 두 개의 엔드포인트만 호출한다.

| # | Endpoint | 용도 |
|---|----------|------|
| 1 | `POST /analyze` | 사용자 발화 오디오 + (선택) 정답 음소열 → 인식된 음소열·정렬·에러·PER 반환 |
| 2 | `POST /g2p` | 영문 텍스트 → ARPAbet 음소열(공백 구분) 변환 |

두 엔드포인트 모두 `multipart/form-data` 로 인코딩된다.

호출자(백엔드 측):
- `RestClientModelServerClient` (단일 구현체, package-private `@Component`)
- 인터페이스: `ModelServerClient`

---

## 2. Base URL & Configuration

### 2.1 Configuration Properties

`application.yaml` 의 `app.model-server.*` 키. 환경변수로 override 가능.

| Property | YAML key | Env override | Default |
|---|---|---|---|
| Base URL | `app.model-server.base-url` | `MODEL_SERVER_BASE_URL` | `http://localhost:8001` |
| Timeout (ms) | `app.model-server.timeout-ms` | `MODEL_SERVER_TIMEOUT_MS` | `30000` |

매핑 클래스: `AppProperties.ModelServer(String baseUrl, long timeoutMs)`.

### 2.2 HTTP Client 특성

`HttpClientConfig.modelRestClient` 빈 구성:

- **HTTP 버전**: HTTP/1.1 강제. (uvicorn 의 HTTP/2 + multipart 조합에서 본문이 깨지는 문제 회피 — 코드 주석으로 명시되어 있음.)
- **구현체**: JDK `java.net.http.HttpClient` + `JdkClientHttpRequestFactory`.
- **Connect timeout**: `timeoutMs`.
- **Read timeout**: `timeoutMs`.
- **재시도**: 없음 (코드에 retry 로직 부재).
- **MessageConverter**: 커스텀 주입 없음 → `RestClient` 기본 컨버터 사용.

### 2.3 직렬화 정책 (Jackson)

`develop` 기준으로 명시적 `ObjectMapper` 빈/`PropertyNamingStrategy`/`spring.jackson.*` 설정이 모두 부재하며, DTO 에 `@JsonProperty` / `@JsonNaming` 어노테이션도 없다. 따라서:

- 응답 JSON 의 필드명은 record 필드명(아래 §3 의 명세) 과 **camelCase 그대로** 일치해야 한다.
- snake_case 등 자동 변환은 적용되지 않는다.

### 2.4 인증

현재 코드에서 `Authorization` 등 인증 헤더는 설정하지 않는다. 모델 서버는 백엔드와 신뢰된 네트워크에서 통신하는 것을 전제로 한다.

### 2.5 업로드 크기 한계

스프링 멀티파트 한도 (백엔드 측):

```yaml
spring.servlet.multipart.max-file-size: 25MB
spring.servlet.multipart.max-request-size: 25MB
```

이 한도는 백엔드가 클라이언트로부터 받을 수 있는 업로드 크기이며, 백엔드 → 모델 서버 호출의 상한과 사실상 동치이다 (백엔드가 받은 오디오를 그대로 전달).

---

## 3. Endpoints

### 3.1 `POST /analyze`

#### Request

- **Content-Type**: `multipart/form-data` (코드에서 명시: `MediaType.MULTIPART_FORM_DATA`)

| Part | 필수 | 타입 | 비고 |
|---|---|---|---|
| `audio` | 필수 | binary | `ByteArrayResource` 로 인코딩. `getFilename()` override 로 파일명 노출 (FastAPI `UploadFile` 인식 전제). 기본 파일명 `"audio.wav"`, 기본 Content-Type `"audio/wav"`. 호출자가 빈/공백 값을 전달하면 위 기본값으로 대체됨. Content-Type 문자열이 `MediaType.parseMediaType` 으로 파싱되지 않으면 `application/octet-stream` 으로 fallback. |
| `canonical` | 선택 | text | 공백 구분 ARPAbet 음소열 (정답 시퀀스). null 또는 blank 이면 part 자체가 omit. |

#### Response 200 — `application/json`

매핑 record: `ModelAnalyzeResponse`.

```jsonc
{
  "perceived":   ["...", "..."],          // List<String>  - 인식된 음소 시퀀스
  "canonical":   ["...", "..."],          // List<String>  - 정답 음소 시퀀스 (요청에 canonical 미전송 시 null)
  "peakSoftmax": [0.91, 0.87],            // List<Double>  - 각 perceived 음소의 softmax 최댓값
  "alignment":   [ /* AlignmentItem */ ], // List<AlignmentItem> - Levenshtein 정렬 결과 전체
  "errors":      [ /* AlignmentItem */ ], // List<AlignmentItem> - alignment 중 op != "equal" 만 필터된 항목
  "per":         0.12,                    // Double        - Phoneme Error Rate (canonical 미전송 시 null)
  "durationSec": 3.42                     // Double        - 디코딩된 오디오 길이(초)
}
```

`AlignmentItem` 스키마 (record `ModelAnalyzeResponse.AlignmentItem`):

```jsonc
{
  "op":              "equal",             // String   - 정렬 연산자 (코드는 "equal" 만 명시; 그 외 값 집합은 §6 참조)
  "canonicalIndex":  3,                   // Integer  - canonical 시퀀스 내 인덱스 (의미·null 정책은 §6 참조)
  "canonical":       "AE",                // String   - 해당 위치의 정답 음소
  "recognizedIndex": 3,                   // Integer  - perceived 시퀀스 내 인덱스 (의미·null 정책은 §6 참조)
  "recognized":      "AE"                 // String   - 해당 위치의 인식 음소
}
```

#### Errors

`postMultipart` 가 모든 실패를 두 ErrorCode 로 매핑한다.

| 발생 조건 | Spring 예외 | 매핑된 ErrorCode | HTTP (백엔드 → 클라이언트) | 메시지 본문 |
|---|---|---|---|---|
| 연결 실패 / 네트워크 타임아웃 | `ResourceAccessException` | `MODEL_SERVER_UNAVAILABLE` | 503 | `e.getMessage()` |
| 모델 서버가 비-2xx 응답 | `RestClientResponseException` | `MODEL_SERVER_ERROR` | 502 | `e.getResponseBodyAsString()` (모델 서버 응답 본문 그대로) |

> 모델 서버측 에러 응답의 본문 포맷(JSON / plain text 등) 은 코드만으로 단정할 수 없음 → §6 참조.

#### 사용처 (백엔드 내부)

- `RecordingServiceImpl` — 사용자 발화 업로드 후 분석.
- `FeedbackServiceImpl` — 약점 단어 재시도 분석.

---

### 3.2 `POST /g2p`

#### Request

- **Content-Type**: `multipart/form-data`

| Part | 필수 | 타입 | 비고 |
|---|---|---|---|
| `text` | 필수 | text | 변환 대상 영문 텍스트. **호출자가 null 또는 blank 를 전달하면 클라이언트가 서버를 호출하지 않고 빈 문자열 `""` 을 즉시 반환**한다. |

#### Response 200 — `application/json`

매핑 record: `ModelG2pResponse`.

```jsonc
{
  "phonemes": "HH AH L OW",              // String         - 공백 구분 ARPAbet 시퀀스 (전체 텍스트). /analyze 의 canonical 인자와 동일 포맷.
  "words": [
    {
      "word":     "hello",               // String
      "phonemes": ["HH", "AH", "L", "OW"]// List<String>
    }
  ]
}
```

#### Client-side 후처리

- `response` 가 null 이면 `""` 반환.
- `response.phonemes()` 가 null 이면 `""` 반환.
- 그 외에는 `response.phonemes()` 그대로 반환.

`g2p` 호출 결과는 항상 non-null 문자열이며 빈 문자열일 수 있다 (호출자는 빈 문자열을 안전히 처리해야 함 — 인터페이스 주석 명시).

#### Errors

`/analyze` 와 동일 매핑 (`postMultipart` 공유).

| 발생 조건 | 매핑된 ErrorCode | HTTP |
|---|---|---|
| 연결 실패 / 타임아웃 | `MODEL_SERVER_UNAVAILABLE` | 503 |
| 비-2xx 응답 | `MODEL_SERVER_ERROR` | 502 |

---

## 4. Error Code Mapping

`com.capstoneecho.echo_back.app.common.ErrorCode` 발췌:

| ErrorCode | HTTP | 기본 메시지 | 발생 트리거 |
|---|---|---|---|
| `MODEL_SERVER_UNAVAILABLE` | 503 Service Unavailable | "모델 서버에 연결할 수 없습니다." | `ResourceAccessException` (connect timeout, refused, DNS 등) |
| `MODEL_SERVER_ERROR` | 502 Bad Gateway | "모델 서버 처리 중 오류가 발생했습니다." | `RestClientResponseException` (모델 서버 비-2xx 응답) |

`BusinessException` 생성 시 두 번째 인자로 모델 서버측 메시지/응답본문이 전달되어 백엔드 로그에 보존된다. 단, **클라이언트 응답에는 ErrorCode 의 기본 메시지만 노출**되는지 여부는 백엔드의 전역 예외 핸들러(`@ControllerAdvice`) 정책에 따르며 본 문서 범위 외이다.

---

## 5. Client Behavior Notes

코드만으로 확정 가능한 동작들:

- `MultipartBodyBuilder` 로 파트 구성 → `RestClient.body(builder.build())` 로 전달. 헤더 `Content-Type: multipart/form-data` 명시.
- `audio` 파트의 `ByteArrayResource` 는 `getFilename()` 익명 서브클래스 override 로 파일명을 노출. **이 override 가 없으면 FastAPI `UploadFile` 이 파트를 파일로 인식하지 않는다** (코드 주석에 근거).
- `canonical` 파트는 비어 있을 때 omit. 빈 값으로 전송하지 않는다.
- `g2p` 는 입력 단계에서 short-circuit (null/blank → 호출 없이 `""`).
- 재시도 / 백오프 / 회로차단기 없음. 한 번 실패하면 즉시 위 ErrorCode 로 매핑.
- 인증 헤더 미설정.

---

## 6. Open Questions (모델 서버측 확인 필요)

코드만으로는 단정할 수 없는 항목. 본 문서를 갱신하려면 모델 서버 구현 확인이 필요하다.

1. **`AlignmentItem.op` 의 값 집합**
   - 코드/주석에 `"equal"` 만 명시. 일반적으로 Levenshtein 정렬은 `equal` / `insert` / `delete` / `replace` 또는 `substitute` 를 쓰지만, 모델 서버가 실제로 내보내는 문자열의 정확한 집합과 표기는 미확인.

2. **`canonicalIndex` / `recognizedIndex` 의 의미**
   - 음소 시퀀스 내 인덱스인지, 문자/그래프임 단위 인덱스인지 코드에서 단정 불가.
   - `op` 값에 따라 한쪽이 null 이 되는지 여부도 미확인 (예: `insert` 인 경우 `canonicalIndex`).

3. **응답 필드 nullable 정책**
   - `perceived`, `canonical`, `peakSoftmax`, `alignment`, `errors` 가 빈 리스트로 오는지 null 로 오는지 단정 불가. (record 매핑상 양쪽 모두 표현 가능.)
   - `canonical` / `per` 는 요청에 canonical 미전송 시 null 임이 DTO 주석에 명시되어 있으나, **그 외 케이스의 nullable 여부는 미확인**.

4. **모델 서버 에러 응답 본문 포맷**
   - 백엔드는 `e.getResponseBodyAsString()` 을 그대로 메시지로 전달하므로 모델 서버측 포맷(JSON 객체인지 plain text 인지, 표준 필드 구조가 있는지) 은 코드로 알 수 없음.

5. **`phonemes` 문자열의 공백 정규화 규칙**
   - "공백 구분" 표기만 있고 단일 공백인지 여러 공백/탭 허용인지 미확인.
   - 빈 입력에 대한 모델 서버 자체 응답(클라이언트 short-circuit 미적용 시) 도 미확인.

6. **지원 오디오 포맷**
   - 클라이언트 기본 Content-Type 은 `audio/wav` 이며 `parseMediaType` 실패 시 `application/octet-stream` 으로 fallback. 모델 서버가 실제로 허용하는 포맷 집합(WAV 외 MP3/OGG/FLAC 등) 은 미확인.

7. **요청 인코딩 라벨링**
   - `audio` 파트의 Content-Type 은 호출자가 넘긴 `contentType` 인자 그대로 사용된다. 모델 서버가 이 라벨을 신뢰하는지, 본문 sniffing 으로 결정하는지 미확인.

---

## 7. Change Log

- 2026-05-10 — 초안 작성 (`develop` @ `f76234d3` 기준).
