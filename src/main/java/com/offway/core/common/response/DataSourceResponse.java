package com.offway.core.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import java.util.stream.Stream;
import io.swagger.v3.oas.annotations.media.ArraySchema;

/**
 * 화면에 그리는 출처 한 줄(#399) — <b>기관명을 서버가 준다.</b>
 *
 * <p>이름만 주고 앱이 표로 옮기게 하면 출처가 하나 늘 때 앱이 배포돼야 뜬다. 표기 누락은 규정 위반이라
 * 그 공백이 그대로 위반이 된다.
 *
 * <p>{@code key} 도 함께 준다. 앱이 특정 기관만 다르게 그리고 싶을 때(로고 대신 텍스트만 허용된다는
 * 규정 때문에 강조 방식이 갈릴 수 있다) 문구를 문자열 비교하지 않게 하려는 것이다 —
 * {@code CategoryTagResponse} 가 같은 이유로 {@code key}·{@code label} 을 함께 준다.
 *
 * @param key 기관 코드
 * @param label 화면에 쓰는 기관명. 앱은 여기에 "출처: ⓒ" 만 붙인다
 */
@Builder(access = AccessLevel.PRIVATE)
public record DataSourceResponse(
        @Schema(example = "KTO") String key,
        @Schema(example = "한국관광공사") String label) {

    /**
     * 선언 순서로 정렬해 내보낸다.
     *
     * <p>{@link Set} 을 그대로 실으면 순서가 보장되지 않아 <b>같은 화면에서 출처 차례가 요청마다
     * 바뀐다.</b> enum 선언 순서를 쓰면 가장 무겁게 표기해야 하는 한국관광공사가 늘 앞에 온다.
     */
    @ArraySchema(schema = @Schema(implementation = DataSourceResponse.class))
    public static List<DataSourceResponse> of(Set<DataSource> sources) {
        return Stream.of(DataSource.values())
                .filter(sources::contains)
                .map(source -> DataSourceResponse.builder().key(source.name()).label(source.label()).build())
                .toList();
    }
}
