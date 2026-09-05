package com.offway.core.trip.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 한 지역의 <b>요일별 방문 패턴</b>(#394 · #395) — "화요일에 가장 한산해요" 의 근거.
 *
 * <h2>왜 요일인가</h2>
 *
 * <p>우리 사용자는 <b>연차를 언제 쓸지</b>를 정한다. "지금 붐비나" 가 아니라 <b>"내가 갈 요일에
 * 붐비나"</b> 가 답해야 할 질문이고, 그 답이 곧 연차를 하루 옮길 이유가 된다.
 *
 * <h2>비율로 든다 — 절대값은 지역 크기를 볼 뿐이다</h2>
 *
 * <p>{@link CrowdLevel} 이 절대 임계라서 겪는 문제를 여기서 반복하지 않는다. 요일계수는 <b>그 지역
 * 자신의 평균 대비</b>라, 울릉군과 안동시를 같은 자로 잰다.
 *
 * <h2>표본이 모자라면 값을 내지 않는다</h2>
 *
 * <p>{@value #MIN_SAMPLES_PER_DAY}일에 못 미치는 요일이 하나라도 있으면 빈 값이다. 계절이 상쇄되려면
 * 열두 달이 필요한데(#395), 석 달치로 낸 "토요일 계수" 는 사실 "여름 계수" 다. <b>지어낸 숫자를 보고
 * 갔다가 틀리면 사용자는 우리가 내리는 모든 숫자를 안 믿는다.</b>
 */
public final class WeeklyVisitPattern {

    /**
     * 요일 하나에 필요한 최소 관측 일수.
     *
     * <p>열두 달이면 요일당 52일쯤 된다. 그보다 낮게 잡되(부분 적재·신규 지역), 계절이 어느 정도
     * 상쇄되는 선을 지킨다 — {@value}일이면 약 아홉 달이다.
     */
    private static final int MIN_SAMPLES_PER_DAY = 40;

    /**
     * "한산한 날" 이라 부르기 위한 최소 격차.
     *
     * <p>가장 적은 요일이 나머지보다 {@value}% 넘게 적어야 한다. 요일 차이가 이보다 작으면 그날을
     * 골라도 사용자가 체감하지 못하는데, <b>체감 못 할 차이를 근거로 연차 날짜를 옮기라고 하는 것은
     * 조언이 아니라 소음이다.</b>
     */
    private static final int MIN_GAP_PERCENT = 10;

    private static final int PERCENT = 100;

    private final Map<DayOfWeek, DayStat> byDay;
    private final double totalCount;
    private final int totalDays;

    private WeeklyVisitPattern(Map<DayOfWeek, DayStat> byDay, double totalCount, int totalDays) {
        this.byDay = byDay;
        this.totalCount = totalCount;
        this.totalDays = totalDays;
    }

    /**
     * 일자별 관광객 수로 패턴을 만든다. 표본이 모자라면 <b>빈 값</b>이다.
     *
     * <p>거주자를 뺀 <b>관광객</b>만 넘겨야 한다({@link VisitorType#isTourist()}). 거주자는 요일을 타지
     * 않아 섞으면 요일 차이가 그만큼 희석된다.
     *
     * @param touristsByDate 하루 → 그날 관광객 수. 같은 날의 유형별 값은 이미 합쳐져 있어야 한다
     */
    public static Optional<WeeklyVisitPattern> of(Map<LocalDate, Double> touristsByDate) {
        Map<DayOfWeek, DayStat> byDay = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            byDay.put(day, new DayStat());
        }

        double totalCount = 0;
        int totalDays = 0;
        for (Map.Entry<LocalDate, Double> entry : touristsByDate.entrySet()) {
            double count = entry.getValue();
            byDay.get(entry.getKey().getDayOfWeek()).add(count);
            totalCount += count;
            totalDays++;
        }

        if (totalCount <= 0) {
            // 전 일자가 0이면 비율의 분모가 없다. 관측이 있었지만 방문자가 없었다는 뜻이라
            // 요일을 가릴 근거도 없다.
            return Optional.empty();
        }
        boolean enough = byDay.values().stream().allMatch(stat -> stat.days >= MIN_SAMPLES_PER_DAY);
        if (!enough) {
            return Optional.empty();
        }
        return Optional.of(new WeeklyVisitPattern(byDay, totalCount, totalDays));
    }

    /**
     * 요일계수 — 그 요일 평균 ÷ 그 지역 전체 평균(#395).
     *
     * <p>1.4 면 그 요일이 평소보다 40% 붐빈다는 뜻이다. 여행일 기준 예상 혼잡도가 이 값을 곱해 쓴다.
     */
    public double factorOf(DayOfWeek day) {
        return byDay.get(day).mean() / overallMean();
    }

    /**
     * 가장 한산한 요일 — 격차가 {@value #MIN_GAP_PERCENT}% 를 넘을 때만.
     *
     * <p>넘지 못하면 빈 값이고, 화면은 그 줄을 지운다.
     */
    public Optional<QuietestDay> quietest() {
        DayOfWeek quietest = null;
        double lowestMean = Double.MAX_VALUE;
        for (Map.Entry<DayOfWeek, DayStat> entry : byDay.entrySet()) {
            double mean = entry.getValue().mean();
            if (mean < lowestMean) {
                lowestMean = mean;
                quietest = entry.getKey();
            }
        }

        int gap = gapPercentAgainstOtherDays(quietest);
        if (gap < MIN_GAP_PERCENT) {
            return Optional.empty();
        }
        return Optional.of(new QuietestDay(quietest, gap));
    }

    /**
     * 그 요일이 <b>나머지 요일들보다</b> 몇 % 적은가.
     *
     * <p>전체 평균이 아니라 나머지 평균과 견준다 — 화면 문구가 "다른 요일보다" 이기 때문이다. 전체
     * 평균에는 그 요일 자신이 섞여 있어 격차가 실제보다 작게 나온다.
     */
    private int gapPercentAgainstOtherDays(DayOfWeek day) {
        DayStat stat = byDay.get(day);
        int otherDays = totalDays - stat.days;
        if (otherDays <= 0) {
            return 0;
        }
        double otherMean = (totalCount - stat.count) / otherDays;
        if (otherMean <= 0) {
            return 0;
        }
        double gap = (otherMean - stat.mean()) / otherMean;
        return (int) Math.round(gap * PERCENT);
    }

    private double overallMean() {
        return totalCount / totalDays;
    }

    /** 요일 하나의 누적 — 합과 일수. 평균을 쓰려면 둘 다 있어야 한다. */
    private static final class DayStat {

        private double count;
        private int days;

        private void add(double value) {
            count += value;
            days++;
        }

        private double mean() {
            return days == 0 ? 0 : count / days;
        }
    }
}
