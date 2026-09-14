package com.offway.core.trip.domain;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

/**
 * 그 장소가 <b>사용자가 고른 날짜</b>에 붐비는가(#565) — 장소 모달의 혼잡 칩.
 *
 * <h2>두 근거가 한 칩으로 나온다</h2>
 *
 * <p>집중률 예측이 있으면 그 장소·그 날짜의 값으로 판정하고({@link #ofForecast}), 없으면 그 지역의
 * 요일계수로 판정한다({@link #ofRegionWeekday}). 폴백은 <b>지역 단위</b>라 그 코스의 장소 전부에 같은
 * 칩이 붙는다 — 사용자가 장소 속성으로 오해하지 않게 문구와 {@link Basis} 를 갈라 둔다.
 *
 * <h2>둘 다 없으면 칩이 없다</h2>
 *
 * <p>"확인 안 됨" 을 띄우지 않는다. 안 붐비는 곳과 못 받아온 곳이 화면에서 같아 보이면 안 된다 —
 * 반려동반 칩(#566)과 같은 판정이다.
 *
 * @param level 붐빔인가 한산인가
 * @param basis 무엇으로 판정했나 — 화면이 문구를 고르고, 우리가 폴백 비율을 볼 수 있다
 * @param label 화면에 그대로 쓰는 문구. 서버가 한글 라벨을 든다({@link QuietestDay#label()} 선례)
 */
public record CrowdChip(Level level, Basis basis, String label) {

    /**
     * "붐빔" 문턱 — 집중률 {@value} 이상.
     *
     * <p>실측(2026-09-14 · 12곳 531개 관광지 15,930행): 이 값이면 슬롯의 <b>12.4%</b> 에 붙는다. 60 으로
     * 내리면 24.1% 라 넷 중 하나꼴이 되어 "붐빈다" 가 특별한 말이 아니게 된다.
     */
    private static final double BUSY_MIN_RATE = 80.0;

    /**
     * "한산" 문턱 — 집중률 {@value} 이하.
     *
     * <p>같은 실측에서 <b>14.3%</b>. 30 으로 올리면 38.0% 라 셋 중 하나가 한산해져 같은 이유로 무뎌진다.
     * 값 분포의 중앙이 35.7 이라 30 은 "보통" 에 가깝다.
     */
    private static final double QUIET_MAX_RATE = 20.0;

    /**
     * 지역 폴백의 "붐빔" 문턱 — 요일계수 {@value} 이상(#565 이슈의 실측).
     *
     * <p>실측(2026-09-13 · 89곳): 토 35곳 · 일 25곳 · <b>평일 0곳</b>으로 갈린다. 1.2 로 내리면 토 82곳
     * ·일 75곳이라 사실상 주말 코스 전부가 되어 정보량을 잃는다.
     *
     * <p><b>폴백에는 "한산" 이 없다.</b> 인구감소지역 89곳이 전부 주말에 더 붐벼서(주말이 평일보다 한산한
     * 지역 0곳), 대칭으로 쓰면 평일 코스 전체에 "한산" 이 붙는다.
     */
    private static final double REGION_BUSY_MIN_FACTOR = 1.4;

    /** 집중률 근거일 때의 문구 — 그 날짜를 가리킨다. */
    private static final String FORECAST_BUSY_LABEL = "이날 붐빔";
    private static final String FORECAST_QUIET_LABEL = "이날 한산";

    /** 지역 폴백일 때의 문구 — 요일을 가리킨다. 장소가 아니라 지역 이야기라는 것이 문구에 드러난다. */
    private static final String REGION_BUSY_LABEL_FORMAT = "%s엔 붐비는 지역";

    /** 혼잡의 방향. */
    public enum Level {
        BUSY,
        QUIET
    }

    /** 무엇으로 판정했나. */
    public enum Basis {

        /** 그 장소·그 날짜의 집중률 예측. 장소마다 갈린다. */
        ATTRACTION_FORECAST,

        /** 그 지역의 요일계수. <b>지역 단위라 같은 코스의 장소에 같은 값이 붙는다.</b> */
        REGION_WEEKDAY
    }

    /**
     * 집중률 예측으로 판정한다 — 문턱 사이(전체의 73%)면 칩이 없다.
     *
     * @param rate 그 장소·그 날짜의 집중률 0~100
     */
    public static Optional<CrowdChip> ofForecast(double rate) {
        if (rate >= BUSY_MIN_RATE) {
            return Optional.of(new CrowdChip(Level.BUSY, Basis.ATTRACTION_FORECAST, FORECAST_BUSY_LABEL));
        }
        if (rate <= QUIET_MAX_RATE) {
            return Optional.of(new CrowdChip(Level.QUIET, Basis.ATTRACTION_FORECAST, FORECAST_QUIET_LABEL));
        }
        return Optional.empty();
    }

    /**
     * 지역 요일계수로 판정한다 — 붐빔만 낸다.
     *
     * @param day 여행 요일
     * @param factor 그 요일의 계수(그 요일 평균 ÷ 그 지역 전체 평균)
     */
    public static Optional<CrowdChip> ofRegionWeekday(DayOfWeek day, double factor) {
        if (factor < REGION_BUSY_MIN_FACTOR) {
            return Optional.empty();
        }
        String label = REGION_BUSY_LABEL_FORMAT.formatted(
                day.getDisplayName(TextStyle.FULL, Locale.KOREAN));
        return Optional.of(new CrowdChip(Level.BUSY, Basis.REGION_WEEKDAY, label));
    }
}
