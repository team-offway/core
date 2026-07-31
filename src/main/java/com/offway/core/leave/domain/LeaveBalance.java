package com.offway.core.leave.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소유자의 <b>총 연차</b>. 남은 연차는 저장하지 않는다 — 사용 내역({@link LeaveUsage})을 정본으로 두고
 * {@link LeaveSummary} 가 파생한다.
 *
 * <p>소유 키는 코스 저장과 같은 {@code guest_id} 다. 이 헤더는 발급도 검증도 없어 아무 문자열이나 통과하는데,
 * 배포하지 않는 동안의 전제로 받아들인다(#34 보류). 인증이 붙는 날 {@code user_id} 로 옮기는 것은 코스와 함께
 * 별도 PR 이다.
 */
@Entity
@Table(name = "leave_balance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveBalance {

    /** 소유 키 길이 — 코스({@code Course.MAX_GUEST_ID_LENGTH})와 같은 값을 쓴다. */
    public static final int MAX_OWNER_ID_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_id", nullable = false, unique = true, length = MAX_OWNER_ID_LENGTH)
    private String guestId;

    /** 총 연차(0.5 단위). 사용 내역을 빼면 남은 연차가 된다. */
    @Column(name = "total_days", nullable = false)
    private double totalDays;

    private LeaveBalance(String guestId, double totalDays) {
        this.guestId = requireOwner(guestId);
        this.totalDays = requireTotal(totalDays);
    }

    /** 처음 저장할 때. 이후 수정은 {@link #changeTotal}. */
    public static LeaveBalance of(String guestId, double totalDays) {
        return new LeaveBalance(guestId, totalDays);
    }

    /** 스테퍼로 총 연차를 고쳐 쓴다. */
    public void changeTotal(double totalDays) {
        this.totalDays = requireTotal(totalDays);
    }

    /**
     * 불변식 — 여기 닿는 위반은 버그다. 계약 검증(0.5 단위·상한)은 요청 DTO 경계가 이미 400 으로 걸러야 한다.
     * 그래도 두는 이유는 누가 만들든 스스로 유효함을 보장하는 최후의 보루이기 때문이다.
     */
    private static double requireTotal(double totalDays) {
        if (!LeaveDays.isValidTotal(totalDays)) {
            throw new IllegalArgumentException("총 연차가 유효하지 않습니다: " + totalDays);
        }
        return totalDays;
    }

    /**
     * 계약 예외(400)를 던진다. 빈 헤더({@code X-Guest-Id: " "})는 {@code @RequestHeader} 를 통과하므로
     * <b>멀쩡한 클라이언트가 정상 요청으로 닿을 수 있다</b> — 불변식으로 다루면 500 이 나간다.
     */
    private static String requireOwner(String guestId) {
        if (guestId == null || guestId.isBlank() || guestId.length() > MAX_OWNER_ID_LENGTH) {
            throw LeaveException.invalidOwnerId();
        }
        return guestId;
    }
}
