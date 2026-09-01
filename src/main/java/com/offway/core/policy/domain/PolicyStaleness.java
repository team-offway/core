package com.offway.core.policy.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 정책 하나가 <b>사람 손을 필요로 하는 상태인가</b>(#220).
 *
 * <p>정책은 수동 seed 라 낡는 것이 유일한 실패 모드인데, 낡으면 {@link Policy#isActiveOn} 이 막아 뱃지가
 * <b>조용히 사라진다.</b> 사용자에게 거짓말은 안 하지만 우리도 모른다 — 후속 캠페인이 열려도 시드를 안
 * 고치면 서비스는 계속 비어 있다.
 *
 * <p>판정을 여기 두는 이유는 <b>날짜를 고정해 검증하기 위해서</b>다. 스케줄러 안에 두면 "D-14 에만
 * 보낸다" 를 확인하려고 시계를 돌려야 한다.
 */
public enum PolicyStaleness {

    /**
     * 곧 끝난다 — 예고.
     *
     * <p><b>이쪽이 본체다.</b> 만료 후에 알리면 이미 뱃지가 사라진 뒤라, 그 사이 사용자는 받을 수 있는
     * 혜택을 못 본다.
     */
    EXPIRING_SOON("종료 임박"),

    /** 오늘 끝난다 — 내일부터 뱃지가 사라진다. */
    EXPIRES_TODAY("오늘 종료"),

    /** 이미 끝났는데 아직 그대로다 — 후속 캠페인이 열렸는지 확인해야 한다. */
    EXPIRED("종료됨"),

    /** 존재는 확인됐으나 상세·기간이 미확정이라 화면에 안 나간다. */
    UNVERIFIED("미검증"),

    /** 기간은 멀쩡한데 확인한 지 오래됐다 — 기관 페이지는 개편이 잦다. */
    STALE_CHECK("확인 오래됨");

    /** 종료 예고를 보내는 날. 이 날이 아니면 안 보낸다 — 매일 보내면 며칠 만에 아무도 안 본다. */
    private static final int[] NOTICE_DAYS_BEFORE = {14, 7};

    /** 확인일자가 이만큼 지나면 다시 보라고 한다. 기관 페이지 개편 주기를 넉넉히 잡은 값이다. */
    public static final int STALE_CHECK_DAYS = 90;

    private final String label;

    PolicyStaleness(String label) {
        this.label = label;
    }

    /** 화면·알림에 그대로 쓸 한글 사유. */
    public String label() {
        return label;
    }

    /**
     * 이 정책이 <b>오늘</b> 알릴 상태인가 — 아니면 빈 값이다.
     *
     * <p>사유가 겹칠 수 있어 순서가 곧 우선순위다. 끝나가는 것이 확인일자보다 급하고, 화면에 아예 안
     * 나가는 미검증이 그다음이다. 하나만 고르는 이유는 한 정책이 여러 줄로 뜨면 목록이 사람 눈에서
     * 흐려지기 때문이다.
     */
    public static Optional<PolicyStaleness> of(Policy policy, LocalDate today) {
        LocalDate end = policy.getPeriodEnd();
        if (end != null) {
            if (end.isEqual(today)) {
                return Optional.of(EXPIRES_TODAY);
            }
            if (end.isBefore(today)) {
                return Optional.of(EXPIRED);
            }
            if (isNoticeDay(end, today)) {
                return Optional.of(EXPIRING_SOON);
            }
        }
        if (!policy.isVerified()) {
            return Optional.of(UNVERIFIED);
        }
        if (isCheckStale(policy.getCheckedOn(), today)) {
            return Optional.of(STALE_CHECK);
        }
        return Optional.empty();
    }

    /**
     * 남은 날이 정해진 예고일과 <b>정확히</b> 같은가.
     *
     * <p>"14일 이하" 로 두면 만료까지 매일 울린다. 안 고치면 계속 오고, 며칠이면 아무도 안 본다 —
     * 그러면 알림이 없는 것과 같다. 정책 기간은 몇 달 단위로 움직이므로 매일 볼 이유도 없다.
     */
    private static boolean isNoticeDay(LocalDate end, LocalDate today) {
        long remaining = ChronoUnit.DAYS.between(today, end);
        for (int notice : NOTICE_DAYS_BEFORE) {
            if (remaining == notice) {
                return true;
            }
        }
        return false;
    }

    /**
     * 확인한 지 오래됐는가.
     *
     * <p><b>확인일자를 모르면 낡았다고 하지 않는다.</b> 이 컬럼이 생기기 전 행은 null 인데, 그건 "확인한
     * 적 없음" 이 아니라 "모른다" 다 — 모르는 것을 낡음으로 단정하면 첫 실행에서 전부 울린다.
     */
    private static boolean isCheckStale(LocalDate checkedOn, LocalDate today) {
        return checkedOn != null && ChronoUnit.DAYS.between(checkedOn, today) > STALE_CHECK_DAYS;
    }

    /** 날마다 보내는 예고인가 — 나머지는 주 1회 요약으로 묶는다. */
    public boolean isExpiryNotice() {
        return this == EXPIRING_SOON || this == EXPIRES_TODAY;
    }
}
