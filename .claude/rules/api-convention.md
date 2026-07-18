# API 규약 — 컨트롤러 · OpenAPI 문서화

컨트롤러·`*Api` 인터페이스·OpenAPI 작업 시 읽는다. 응답 래퍼 규격은 `exception-and-response.md` 참고.

> 전제: springdoc-openapi 의존성이 필요하다(`build.gradle.kts`). Spring Boot 4 호환 버전을 Maven Central 에서 확인해 추가한다.

## 컨트롤러 / 인터페이스 분리

- **컨트롤러(`*Controller`)는 `*Api` 인터페이스를 구현한다.** 공개 JSON API 엔드포인트에 적용한다. (헬스체크·어드민 SSR 등 공개 JSON 응답면이 아닌 것은 정당한 예외.)
- **인터페이스(`*Api`)**: OpenAPI 어노테이션만 — `@Tag`, `@Operation`, `@ApiResponse(s)`, `@Schema`. 매핑/검증 어노테이션은 두지 않는다.
- **구현체(`*Controller`)**: `@RestController`, `@RequestMapping`, 메서드별 `@GetMapping`/`@PostMapping`, 파라미터 어노테이션, `@Valid`, `@ResponseStatus`. 라우팅이 컨트롤러만 봐도 한눈에 드러나야 한다.
- 컨트롤러 메서드는 `ApiResponseBody<T>` 를 반환한다(3xx 리다이렉트 예외).

```java
@Tag(name = "연차")
public interface LeaveApi {

    @Operation(summary = "가용 시간(LNT) 산출")
    @ApiResponse(responseCode = "200", description = "산출 성공")
    @ApiResponse(responseCode = "400", description = "연차 일수가 올바르지 않음")
    ApiResponseBody<AvailableTimeResponse> availableTime(AvailableTimeRequest request);
}

@RestController
@RequestMapping("/api/v1/leave")
@RequiredArgsConstructor
public class LeaveController implements LeaveApi {

    private final LeaveService leaveService;

    @Override
    @PostMapping("/available-time")
    public ApiResponseBody<AvailableTimeResponse> availableTime(@Valid @RequestBody AvailableTimeRequest request) {
        return ApiResponseBody.ok(AvailableTimeResponse.from(leaveService.calculate(request.toCommand())));
    }
}
```

## 응답 전수 문서화

**`*Api` 의 각 메서드는 멀쩡한 클라이언트가 정상 요청으로 받을 수 있는 모든 응답을 `@ApiResponse` 로 문서화한다** — 성공과 도달 가능한 실패 전부.

판단 기준은 예외 정책의 그 한 줄과 같다(`exception-and-response.md`):
- **닿는다 → 문서화 대상**: 성공 2xx · 계약 실패 4xx · 외부 의존성 실패 5xx(예: TourAPI 실패 → 502).
- **못 닿는다 → 제외**: 서버 버그·불변식 위반(일반 500). 모든 엔드포인트 공통이라 엔드포인트별 계약이 아니다.

조사 대상 다섯 군데: ① 성공 응답(컨트롤러 `@ResponseStatus` 와 일치) ② Spring Security 권한(비-permitAll → 401, 권한요구 → 403) ③ 도메인 커스텀 예외의 status ④ 외부 의존성 실패 5xx ⑤ Bean Validation → 400.

- description 은 구체적으로. "잘못된 요청 (오류 등)" 금지 → "연차 일수가 음수·형식 오류" 처럼 실제 원인 나열.
- 새 엔드포인트 추가·시그니처 변경·새 예외(특히 새 외부 의존성) 추가 시 `*Api` 의 `@ApiResponse` 를 함께 갱신한다.

## example 객체화 — 후속 (초기 MVP 범위 밖)

core 는 example payload 를 `OperationCustomizer` 빈으로 객체화해 DTO 변경을 컴파일로 추적한다. **OffWay 초기엔 여기까지 하지 않는다** — `@ApiResponse` 선언 전수화까지만 하고, example 객체화(single-source fail detail 등)는 API 가 안정된 뒤 후속으로 승격한다. 그전까지 Swagger 의 자동 생성 example 로 충분하다.
