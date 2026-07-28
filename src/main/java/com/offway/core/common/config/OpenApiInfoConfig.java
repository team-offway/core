package com.offway.core.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * api-docs 메인 페이지 설명 — 화면별로 어떤 API 를 호출해 무엇을 받고, 그 응답을 어떻게 이어 붙여 여행 코스까지 만드는지의
 * 흐름을 담는다. Swagger UI 는 {@code description} 을 Markdown 으로 렌더한다.
 *
 * <p>paths·schema 는 springdoc 이 자동 채우고, 이 빈은 {@code info} 만 제공한다({@link OpenApiNullableRefConfig}
 * 커스터마이저는 그대로 적용된다).
 */
@Configuration
public class OpenApiInfoConfig {

    private static final String TITLE = "OffWay API";
    private static final String VERSION = "v1";

    private static final String DESCRIPTION =
            """
            연차에서 시작해 **인구감소지역 여행 코스**까지 만드는 API입니다. \
            아래는 화면별 호출 흐름 — *무엇을 호출해 무엇을 받고, 그 응답을 들고 다음 무엇을 호출해 코스를 만드나*.

            ## 핵심 여정

            **① 홈 진입**
            - `GET /api/v1/home` → 남은 연차 · 필터칩 · **이번주 추천 지역 카드**(대표 이미지·카테고리·한산도·대표 혜택 뱃지·실시간 대기질)를 한 번에.
            - `GET /api/v1/categories` → 필터칩 전체 목록.
            - `GET /api/v1/leaves/sandwich` → "지금 연차 쓰기 좋은 날"(샌드위치·황금연차).

            **② 연차 → 가용시간(LNT)**
            - `POST /api/v1/leaves/available-time` ← 연차 일수·기간·이동수단
            - → 공휴일(특일정보) 반영한 **실제 여행 가능 시간(LNT)**. 이 값이 이후 추천·코스의 여행 상한이 됩니다.

            **③ 여행지 추천**
            - `POST /api/v1/regions/recommendations` ← 필터(무드·이동수단·가용시간 등)
            - → 인구감소지역 89 중 랭킹된 추천 지역 목록. 각 항목에 **`regionId` · 대표 혜택(`policyId`) · 한산도 뱃지**.
            - `GET /api/v1/policies/{policyId}` ← 카드의 `policyId` → 혜택 상세.

            **④ 코스 생성 — 조합의 핵심**
            - `POST /api/v1/courses/generate` ← **③의 `regionId`** + 여행 날짜(달력) + 이동수단
            - → 여행지(TourAPI) + 동선·도달시간(TMAP·TAGO 열차) + 열차 접근(출발지→지역, 4-way) + 여행 날짜 날씨 를 조합한 **Day별 타임라인 코스**.

            **⑤ 장소 상세**
            - `GET /api/v1/pois/{contentId}` ← 코스 타임라인의 장소 → 운영시간·소개·구석구석 캐치프레이즈.
            - `GET /api/v1/pois/{contentId}/accessibility` → 무장애(배리어프리) 정보.

            **⑥ 내 코스**
            - `POST /api/v1/courses`(저장) · `GET /api/v1/courses`(목록) · `GET /api/v1/courses/{courseId}`(상세).

            ## 한 줄 요약
            홈에서 **추천 지역**을 받고 → 연차로 **가용시간**을 구하고 → `regionId`+날짜+이동수단으로 **코스를 생성**하고 → 장소를 눌러 **상세**를 본 뒤 → **내 코스**로 저장.

            ## 공통 규약
            - 모든 응답은 `ApiResponseBody<T>` 래퍼로 감쌉니다 — `status` · `data` · `detail` · `code`(성공은 `OK`, 실패는 도메인 코드).
            - 외부 의존성(TourAPI·TMAP·TAGO·기상청·에어코리아) 실패는 `502`, 계약 위반은 `4xx`.
            - *(개발 중·후순위)* 게스트 식별 · 연차 영속(내 연차 화면) · 정책 백오피스.
            """;

    @Bean
    OpenAPI offwayOpenApi() {
        return new OpenAPI().info(new Info()
                .title(TITLE)
                .version(VERSION)
                .description(DESCRIPTION));
    }
}
