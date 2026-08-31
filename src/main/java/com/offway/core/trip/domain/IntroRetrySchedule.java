package com.offway.core.trip.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 빈 운영시간을 <b>언제 다시 물을지</b>(#368).
 *
 * <h2>왜 간격을 늘리나</h2>
 *
 * <p>원본에 운영시간이 없는 장소는 받아도 모든 칸이 비어 있다. 예전에는 그것을 <b>7일마다 영원히</b> 다시
 * 물었다 — 그런 장소가 N건이면 매일 {@code N/7} 건이 나가고 줄지 않는다. 새로 채우는 게 아니라 같은 장소를
 * 7일 주기로 돌려가며 다시 묻는 제자리걸음이었고, 그것이 관광정보 한도의 30%를 매일 먹고 있었다.
 *
 * <h2>왜 포기하지는 않나</h2>
 *
 * <p>"몇 번 비면 그만 묻는다" 가 더 싸지만 <b>되살릴 길이 없다.</b> 빈 값을 영구 저장으로 굳히면 지자체가
 * 나중에 운영시간을 채워도 우리는 영영 모르고, {@code poi_intro} 를 지우는 코드는 어디에도 없다.
 *
 * <p>그래서 간격만 늘린다. <b>처음 빈 것과 스무 번 빈 것을 같은 주기로 묻던 것</b>이 문제였지, 다시 묻는
 * 것 자체가 문제가 아니었다.
 *
 * <pre>
 *   1회  7일 → 2회 14 → 3회 28 → 4회 56 → 5회 112 → 6회 이후 180(상한)
 * </pre>
 *
 * <p>정상 상태 비용은 {@code N/180} 으로 수렴한다 — N=2,000 이면 하루 11건, 예전(286건)의 1/26 이다.
 * 그러면서도 원본이 채워지면 늦어도 상한 간격 안에 화면에 반영된다.
 */
public final class IntroRetrySchedule {

    /**
     * 처음 비었을 때 다시 묻기까지 — 예전 고정 간격과 같은 값이다.
     *
     * <p>방금 빈 장소는 원본이 곧 채워질 수 있어 짧게 둔다. 비용이 드는 것은 <b>계속 비는 장소</b>이고,
     * 그쪽은 아래 배수가 밀어낸다.
     */
    private static final Duration FIRST_INTERVAL = Duration.ofDays(7);

    /** 물을 때마다 간격을 두 배로. */
    private static final int GROWTH = 2;

    /**
     * 간격 상한 — 반년.
     *
     * <p>무한히 늘리면 사실상 포기와 같아진다. 이 값이 <b>"원본이 채워지면 늦어도 이 안에는 반영된다"</b>
     * 는 약속이다.
     */
    private static final Duration MAX_INTERVAL = Duration.ofDays(180);

    private IntroRetrySchedule() {
    }

    /**
     * {@code attempts} 번째 빈 응답 뒤 다음에 물을 시각.
     *
     * @param attempts 이번을 포함해 연속으로 빈 응답을 받은 횟수(1 이상)
     * @param fetchedAt 이번에 받은 시각
     */
    public static LocalDateTime nextRetryAt(int attempts, LocalDateTime fetchedAt) {
        return fetchedAt.plus(intervalFor(attempts));
    }

    /**
     * {@code attempts} 번 비었을 때의 간격.
     *
     * <p>{@code long} 으로 곱한 뒤 상한에서 자른다 — {@code int} 로 두면 스무 번쯤에서 넘쳐 <b>간격이
     * 음수가 되고</b>, 그 순간 그 장소가 매 회차 일감으로 되살아난다. 줄이려던 비용이 정반대가 된다.
     */
    static Duration intervalFor(int attempts) {
        if (attempts < 1) {
            throw new IllegalArgumentException("빈 응답 횟수는 1 이상이어야 합니다: " + attempts);
        }
        long days = FIRST_INTERVAL.toDays();
        for (int grown = 1; grown < attempts && days < MAX_INTERVAL.toDays(); grown++) {
            days *= GROWTH;
        }
        return Duration.ofDays(Math.min(days, MAX_INTERVAL.toDays()));
    }
}
