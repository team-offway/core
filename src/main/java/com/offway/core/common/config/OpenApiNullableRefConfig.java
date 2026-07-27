package com.offway.core.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * null 가능한 <b>객체 참조($ref)</b> 응답 필드를 OpenAPI 3.0 의 {@code {nullable:true, allOf:[{$ref}]}} 로 감싼다.
 *
 * <p>swagger-core 는 스칼라 필드의 {@code @Schema(nullable=true)} 는 {@code nullable:true} 로 잘 내지만, {@code $ref}
 * 필드에는 nullable 을 통째로 드롭한다(3.0 에서 $ref 형제 키워드가 무시되는 것을 회피하는 allOf 래핑을 이 버전이 안 함). 그 결과
 * 실제로 null 이 내려가는 응답(비페이지 {@code pageResponse}, 실패 시 {@code data}, 상시운영 정책 {@code period} 등)이
 * 스키마 계약을 어겨 클라이언트(apidog 등) 응답 검증이 깨진다. 스펙 생성 후 해당 참조 필드만 표준 형태로 보정한다.
 */
@Configuration
public class OpenApiNullableRefConfig {

    /** 공통 래퍼에서 null 이 흔한 참조 필드. */
    private static final String WRAPPER_SCHEMA_PREFIX = "ApiResponseBody";

    private static final String FIELD_DATA = "data";
    private static final String FIELD_PAGE_RESPONSE = "pageResponse";

    @Bean
    OpenApiCustomizer nullableRefCustomizer() {
        return openApi -> {
            Map<String, Schema> schemas = schemas(openApi);
            if (schemas == null) {
                return;
            }
            // 공통 래퍼: data(실패 시 null)·pageResponse(비페이지 null) — 모든 ApiResponseBody* 에 적용
            schemas.forEach((name, schema) -> {
                if (name.startsWith(WRAPPER_SCHEMA_PREFIX)) {
                    wrapNullable(schema, FIELD_DATA);
                    wrapNullable(schema, FIELD_PAGE_RESPONSE);
                }
            });
            // 도메인 개별 nullable 참조
            wrapNullable(schemas.get("PolicyResponse"), "period"); // 상시운영이면 null
            wrapNullable(schemas.get("RegionCard"), "benefit"); // 대표 혜택 없으면 null
        };
    }

    private static Map<String, Schema> schemas(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            return null;
        }
        return openApi.getComponents().getSchemas();
    }

    /** {@code schema.properties[field]} 가 순수 $ref 면 {@code {nullable:true, allOf:[{$ref}]}} 로 교체한다. */
    private static void wrapNullable(Schema<?> schema, String field) {
        if (schema == null || schema.getProperties() == null) {
            return;
        }
        Schema<?> property = schema.getProperties().get(field);
        if (property == null || property.get$ref() == null) {
            return; // 필드 없음 · 이미 래핑됨 · 스칼라(annotation 이 처리) — 건드리지 않는다
        }
        Schema<?> wrapper = new Schema<>().nullable(true).addAllOfItem(new Schema<>().$ref(property.get$ref()));
        schema.getProperties().put(field, wrapper);
    }
}
