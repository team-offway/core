package com.offway.core.trip.controller.dto;

import com.offway.core.trip.domain.PopularityTrend;
import com.offway.core.trip.domain.QuietestDay;
import com.offway.core.trip.domain.RegionVisitMetrics;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;

/**
 * 지역의 방문 지표(#394) — <b>여러 화면이 같은 모양으로 받는다</b>.
 *
 * <p>이 값은 지역 상세·코스 확정·내 코스 상세에서 <b>같은 컴포넌트</b>로 그려진다. 화면마다 모양이
 * 다르면 클라이언트가 그 수만큼 분기를 만들어야 하므로, 응답 조각을 하나로 두고 각 응답이 이것을 문다.
 *
 * <p>두 값 모두 <b>없을 수 있다</b>. 없으면 그 줄을 지운다 — 지어낸 숫자를 보고 갔다가 틀리면
 * 사용자가 우리가 내리는 모든 숫자를 안 믿는다.
 *
 * @param quietestDay 가장 한산한 요일. 표본이 모자라거나 요일 격차가 미미하면 null
 * @param trend 작년 같은 기간 대비 증감. 작년 치가 없으면 null
 */
public record RegionVisitMetricsResponse(
        @Schema(nullable = true) QuietestDayResponse quietestDay,
        @Schema(nullable = true) TrendResponse trend) {

    /**
     * 도메인 값을 응답 조각으로. <b>{@code null} 도 받는다</b> — 지표를 아직 안 붙인 경로나 빌더로
     * 조립한 값이 넘어올 수 있고, 그때 터지느니 빈 지표로 내리는 편이 맞다. 지표는 덤이다.
     */
    public static RegionVisitMetricsResponse from(RegionVisitMetrics metrics) {
        if (metrics == null) {
            return new RegionVisitMetricsResponse(null, null);
        }
        return new RegionVisitMetricsResponse(
                QuietestDayResponse.from(metrics.quietestDay()),
                TrendResponse.from(metrics.trend()));
    }

    /**
     * 가장 한산한 요일 — "화요일에 가장 한산해요".
     *
     * @param dayOfWeek 요일 코드. 클라이언트가 자체 표기를 쓰고 싶을 때를 위해 함께 내린다
     * @param label 화면에 그대로 쓰는 한글 — "화요일". <b>서버가 든다</b>(이 레포 방식)
     * @param percentLessThanOtherDays 나머지 요일들보다 몇 % 적은가 — 모달 문구의 그 숫자다
     */
    public record QuietestDayResponse(
            @Schema(example = "TUESDAY") DayOfWeek dayOfWeek,
            @Schema(example = "화요일") String label,
            @Schema(example = "30") int percentLessThanOtherDays) {

        static QuietestDayResponse from(QuietestDay quietestDay) {
            if (quietestDay == null) {
                return null;
            }
            return new QuietestDayResponse(
                    quietestDay.dayOfWeek(), quietestDay.label(), quietestDay.percentLessThanOtherDays());
        }
    }

    /**
     * 인기 추세 — "추세 +40% · 요즘 사람이 늘고 있어요".
     *
     * <p><b>{@code rising} 이 거짓인 것과 {@code trend} 자체가 없는 것은 다르다.</b> 전자는 "재 보니 안
     * 늘었다", 후자는 "아직 잴 수 없다" 다. 화면이 둘을 같게 그리더라도 응답은 구분해서 내린다.
     *
     * @param percent 작년 같은 기간 대비 증감률. 줄었으면 음수다
     * @param rising 늘고 있다고 말할 만한가 — 목록의 "최근 인기 상승" 칩이 이 값을 쓴다
     */
    public record TrendResponse(
            @Schema(example = "40") int percent,
            @Schema(example = "true") boolean rising) {

        static TrendResponse from(PopularityTrend trend) {
            if (trend == null) {
                return null;
            }
            return new TrendResponse(trend.percent(), trend.rising());
        }
    }
}
