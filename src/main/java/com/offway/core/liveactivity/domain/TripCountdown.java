package com.offway.core.liveactivity.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 오늘 잠금화면에 올릴 여행 <b>하나</b>를 고른다(#583).
 *
 * <h2>앱과 같은 이름, 같은 규칙</h2>
 *
 * <p>앱에도 같은 규칙이 {@code TripCountdown.pick} 으로 있다. <b>이름을 맞춘 것은 우연이 아니다</b> —
 * 서버가 띄운 카드와 앱이 띄운 카드가 다른 여행을 가리키면, 앱을 연 사람과 안 연 사람이 서로 다른
 * 화면을 본다. 한쪽을 고치면 다른 쪽도 고쳐야 한다는 사실이 이름에서 보이게 둔다.
 *
 * <h2>왜 하나만 고르나</h2>
 *
 * <p>잠금화면은 자리가 하나다. 둘을 띄우면 카드가 서로를 밀어내고, 사용자는 어느 것이 "다음 여행"
 * 인지 알 수 없다.
 *
 * <h2>순서</h2>
 *
 * <ol>
 *   <li><b>진행 중</b>(출발일 ≤ 오늘 ≤ 종료일)이 있으면 그중 출발일이 가장 이른 것. 지금 그 지역에
 *       있는 사람에게 다음 주 여행을 보여줄 이유가 없다
 *   <li>없으면 <b>출발까지 {@value #WITHIN_DAYS}일 이내</b> 중 가장 가까운 것
 *   <li>둘 다 없으면 안 띄운다
 * </ol>
 *
 * <p>이 클래스는 {@code Course} 를 모른다 — 고르는 데 필요한 것이 날짜 셋뿐이라, 엔티티를 끌고 오면
 * 발송 경로(트랜잭션 밖)에서 준영속 연관을 건드릴 위험만 는다.
 */
public final class TripCountdown {

    /**
     * 출발 며칠 전부터 띄우나.
     *
     * <p><b>앱의 {@code within} 과 같은 값이어야 한다.</b> 어긋나면 서버가 띄운 날과 앱이 띄우는 날이
     * 달라져, 같은 사람이 앱을 여는지에 따라 카드가 있었다 없었다 한다.
     */
    public static final int WITHIN_DAYS = 5;

    private TripCountdown() {
    }

    /**
     * 오늘 띄울 여행.
     *
     * <p>기준일을 인자로 받는다 — 배치는 오늘을 넘기고 테스트는 고정된 날짜를 넘긴다. 안에서 현재
     * 시각을 읽으면 이 규칙을 검증할 방법이 없어지고, 테스트가 날짜가 바뀌는 날 깨진다.
     */
    public static Optional<Trip> pick(List<Trip> trips, LocalDate today) {
        List<Trip> usable = trips.stream().filter(Trip::isUsable).toList();
        Optional<Trip> ongoing = usable.stream()
                .filter(trip -> trip.covers(today))
                .min(Comparator.comparing(Trip::travelDate));
        if (ongoing.isPresent()) {
            return ongoing;
        }
        return usable.stream()
                .filter(trip -> trip.startsWithin(today, WITHIN_DAYS))
                .min(Comparator.comparing(Trip::travelDate));
    }

    /**
     * 고르는 데 필요한 것만 담은 값.
     *
     * @param courseId 어느 코스인가 — 카드의 attributes 에 실린다
     * @param travelDate 출발일
     * @param travelDays 여행 기간(일). 1이면 당일치기
     */
    public record Trip(long courseId, LocalDate travelDate, int travelDays) {

        /**
         * 쓸 수 있는 값인가.
         *
         * <p><b>날짜가 없거나 기간이 0 이하면 뺀다.</b> 기간이 0 이하면 종료일이 출발일보다 앞서는
         * 역전 상태가 되고, 그대로 두면 "0일차" 나 음수 D-day 가 화면에 뜬다. 기간을 1 이상으로
         * 거르는 것이 곧 역전을 거르는 것이다 — 종료일은 {@code 출발일 + (기간 - 1)} 이라 기간이
         * 1 이상이면 역전이 성립하지 않는다.
         *
         * <p>엔티티 불변식이 이미 기간을 1 이상으로 강제하지만, 이 값은 DB 에서 오므로 거기까지
         * 믿지 않는다 — 이 카드는 <b>앱을 안 연 사람</b>에게 가고, 그 사람은 이상한 값을 보고도
         * 우리에게 알릴 길이 없다.
         */
        boolean isUsable() {
            return travelDate != null && travelDays >= 1;
        }

        boolean covers(LocalDate today) {
            return !today.isBefore(travelDate) && !today.isAfter(endDate());
        }

        boolean startsWithin(LocalDate today, int days) {
            if (!travelDate.isAfter(today)) {
                return false;
            }
            return ChronoUnit.DAYS.between(today, travelDate) <= days;
        }

        public LocalDate endDate() {
            return TripProgress.endDate(travelDate, travelDays);
        }
    }
}
