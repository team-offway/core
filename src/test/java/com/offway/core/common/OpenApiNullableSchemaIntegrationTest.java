package com.offway.core.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 생성된 OpenAPI 스펙이 null 가능 응답 필드를 실제로 nullable 로 선언하는지 잠근다.
 *
 * <p>이 계약이 깨지면(예: springdoc 3.0 출력 설정 제거, 커스터마이저 유실) 실제로 null 이 내려가는 응답이 스키마를 어겨 클라이언트
 * 응답 검증이 다시 깨진다. 스칼라(annotation)와 객체 참조(커스터마이저 allOf 래핑) 양쪽을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiNullableSchemaIntegrationTest {

    private static final String API_DOCS = "/v3/api-docs";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 스펙은_OpenAPI_3_0으로_생성된다() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.0")));
    }

    @Test
    void 스칼라_nullable_필드는_nullable로_선언된다() throws Exception {
        // imageUrl — 이미지 없는 장소는 콘텐츠가 있어도 null
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.RegionCard.properties.imageUrl.nullable").value(true))
                .andExpect(jsonPath("$.components.schemas.RegionCard.properties.imageUrl.type").value("string"));
    }

    @Test
    void 객체참조_nullable_필드는_allOf로_감싸_nullable로_선언된다() throws Exception {
        // period(상시운영이면 null) — 커스터마이저가 {nullable:true, allOf:[{$ref}]} 로 보정
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.PolicyResponse.properties.period.nullable").value(true))
                .andExpect(jsonPath("$.components.schemas.PolicyResponse.properties.period.allOf[0]['$ref']")
                        .value("#/components/schemas/Period"));
    }

    @Test
    void 공통래퍼_data_pageResponse_도_nullable_allOf로_보정된다() throws Exception {
        // 실패 시 data=null · 비페이지 pageResponse=null — 전 ApiResponseBody* 공통. 대표로 한 인스턴스 검증.
        String wrapper = "$.components.schemas.ApiResponseBodyPolicyResponse.properties";
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath(wrapper + ".data.nullable").value(true))
                .andExpect(jsonPath(wrapper + ".data.allOf[0]['$ref']").value("#/components/schemas/PolicyResponse"))
                .andExpect(jsonPath(wrapper + ".pageResponse.nullable").value(true))
                .andExpect(jsonPath(wrapper + ".pageResponse.allOf[0]['$ref']")
                        .value("#/components/schemas/PageResponse"));
    }

    @Test
    void 홈_대표혜택_benefit_도_nullable_allOf로_보정된다() throws Exception {
        // 대표 혜택 없으면 null
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.RegionCard.properties.benefit.nullable").value(true))
                .andExpect(jsonPath("$.components.schemas.RegionCard.properties.benefit.allOf[0]['$ref']")
                        .value("#/components/schemas/Benefit"));
    }
}
