package com.offway.core.liveactivity.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 잠금화면의 여행이 <b>오늘 어디쯤 와 있는가</b>(#575).
 *
 * <p>Live Activity 가 매일 자정에 갱신되어야 하는 이유가 이 값이다 — 어제 계산한 {@code D-3} 은 오늘
 * 틀린 값이고, 앱을 안 열면 그 틀린 값이 잠금화면에 그대로 남는다.
 *
 * <h2>문구가 아니라 숫자를 담는다</h2>
 *
 * <p>잠금화면에 뭐라고 쓸지는 <b>앱이 정한다</b>. 서버가 "정선군 여행 D-2" 같은 완성된 문구를 보내면
 * 같은 한국어 조립 규칙이 서버와 앱 두 곳에 생기고, 카피가 바뀔 때마다 <b>양쪽을 고쳐 배포 시점까지
 * 맞춰야</b> 한다. 그 사이에는 앱을 열었을 때와 자정 갱신이 서로 다른 말을 한다.
 *
 * <p>그래서 여기 있는 것은 {@code daysLeft}·{@code dayNth} 두 숫자뿐이다. 문구 규칙("출발 당일은
 * D-DAY 가 아니라 1일차", "D-1 만 '내일 …'")은 이 서비스가 몰라도 되는 것이고, 실제로 모른다.
 *
 * <h2>달력으로 센다</h2>
 *
 * <p><b>경과 시간을 24로 나누지 않는다.</b> 그러면 서머타임·윤초가 낀 구간에서 하루가 어긋나는데,
 * 잠금화면의 D-day 가 하루 틀리는 것은 이 기능이 있으나 마나 한 수준의 오류다. 연·월·일만 비교한다.
 *
 * @param phase 출발 전인가, 여행 중인가, 끝났는가
 * @param daysLeft 남은 날. <b>{@link Phase#BEFORE} 일 때만 값이 있다</b>
 * @param dayNth 여행 며칠째. <b>{@link Phase#DURING} 일 때만 값이 있다</b>
 */
public record TripProgress(Phase phase, Integer daysLeft, Integer dayNth) {

    /** 여행의 세 시점. 잠금화면에 무엇을 보낼지가 여기서 갈린다. */
    public enum Phase {

        /** 아직 안 떠났다 — {@code daysLeft} 를 보낸다. */
        BEFORE,

        /** 여행 중이다 — {@code dayNth} 를 보낸다. */
        DURING,

        /**
         * 끝났다 — 갱신이 아니라 <b>종료</b>를 보낸다.
         *
         * <p>날짜가 없는 코스도 여기다. 언제인지 모르는 여행을 잠금화면에 띄워 둘 수는 없다.
         */
        ENDED
    }

    /**
     * 기준일에서 본 진행 상태.
     *
     * <p>기준일을 인자로 받는다 — 스케줄러는 오늘을 넘기고 테스트는 고정된 날짜를 넘긴다. 안에서
     * {@code LocalDate.now()} 를 부르면 이 계산을 검증할 방법이 없어진다.
     *
     * @param travelDate 출발일. <b>{@code null} 이면 {@link Phase#ENDED}</b> — 날짜가 없는 코스는
     *     잠금화면에 올릴 수 없다
     * @param travelDays 여행 기간(일). 1이면 당일치기다
     * @param today 오늘 (서비스 기준 시간대의 달력 날짜)
     */
    public static TripProgress of(LocalDate travelDate, int travelDays, LocalDate today) {
        if (travelDate == null) {
            return new TripProgress(Phase.ENDED, null, null);
        }
        if (today.isBefore(travelDate)) {
            return new TripProgress(Phase.BEFORE, daysBetween(today, travelDate), null);
        }
        LocalDate endDate = endDate(travelDate, travelDays);
        if (today.isAfter(endDate)) {
            return new TripProgress(Phase.ENDED, null, null);
        }
        // 출발 당일이 1일차다. 0일차는 없다.
        return new TripProgress(Phase.DURING, null, daysBetween(travelDate, today) + 1);
    }

    /**
     * 종료일 — 출발일에서 기간만큼. 1박2일이면 출발 다음 날이다.
     *
     * <p>{@code Course.travelEndDate()} 와 같은 계산이지만 여기로 가져왔다. 그쪽은 엔티티 메서드라
     * <b>준영속 상태에서 부를 수 있는지</b>가 호출 위치마다 달라지는데, 이 계산은 발송 경로(트랜잭션
     * 밖)에서 쓰인다. 값 둘로 답할 수 있는 것을 엔티티에 의존해 묻지 않는다.
     */
    public static LocalDate endDate(LocalDate travelDate, int travelDays) {
        return travelDate.plusDays(travelDays - 1L);
    }

    /** 끝났는가 — 갱신 대신 종료를 보낼 자리다. */
    public boolean ended() {
        return phase == Phase.ENDED;
    }

    private static int daysBetween(LocalDate from, LocalDate to) {
        return Math.toIntExact(ChronoUnit.DAYS.between(from, to));
    }
}
