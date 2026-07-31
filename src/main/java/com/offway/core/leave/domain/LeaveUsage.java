package com.offway.core.leave.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 연차 사용 내역 한 건. 이 내역의 합이 "쓴 연차" 이고, 총 연차에서 빼면 남은 연차가 된다.
 *
 * <p><b>증감</b>이다 — 코스를 취소하면 음수 내역을 하나 더 쌓아 되돌린다. 기존 행을 지우면 "언제 무엇이 취소됐는지" 가
 * 사라진다.
 *
 * <p>{@code courseId} 는 raw ID 다(도메인 경계를 넘으므로 연관관계를 두지 않는다 — persistence-convention).
 * 코스 확정 차감(#91)이 이 값으로 <b>중복 차감을 막는다</b> — 같은 코스로 이미 쌓인 내역이 있으면 건너뛴다.
 */
@Entity
@Table(name = "leave_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveUsage {

    /** 사유 최대 길이 — 화면에 한 줄로 보이는 짧은 메모다. */
    public static final int MAX_REASON_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_id", nullable = false, length = LeaveBalance.MAX_OWNER_ID_LENGTH)
    private String guestId;

    /** 연차를 쓴(또는 되돌린) 날. */
    @Column(name = "used_on", nullable = false)
    private LocalDate usedOn;

    /** 증감(0.5 단위). 사용은 양수, 취소는 음수. */
    @Column(name = "days", nullable = false)
    private double days;

    @Column(length = MAX_REASON_LENGTH)
    private String reason;

    /** 이 내역을 만든 코스 (수동 입력이면 null). 중복 차감 방지의 키다. */
    @Column(name = "course_id")
    private Long courseId;

    private LeaveUsage(String guestId, LocalDate usedOn, double days, String reason, Long courseId) {
        this.guestId = Objects.requireNonNull(guestId, "guestId 는 null 일 수 없습니다.");
        this.usedOn = Objects.requireNonNull(usedOn, "usedOn 은 null 일 수 없습니다.");
        this.days = requireDays(days);
        this.reason = trimReason(reason);
        this.courseId = courseId;
    }

    /** 사용자가 직접 남기는 내역. */
    public static LeaveUsage manual(String guestId, LocalDate usedOn, double days, String reason) {
        return new LeaveUsage(guestId, usedOn, days, reason, null);
    }

    /** 코스 확정으로 생기는 내역 — {@code courseId} 가 중복 차감을 막는다(#91). */
    public static LeaveUsage forCourse(String guestId, LocalDate usedOn, double days, String reason, long courseId) {
        return new LeaveUsage(guestId, usedOn, days, reason, courseId);
    }

    /**
     * 계약 예외(400)를 던진다 — {@code IllegalArgumentException} 이 아니다.
     *
     * <p>지금은 요청 DTO 가 먼저 걸러 여기 닿지 않지만, 코스 확정 차감(#91)이 {@link #forCourse} 를 서비스에서
     * 직접 부르면 DTO 를 거치지 않고 들어온다. 그때 불변식 예외를 던지면 클라이언트 계약 위반이 500 으로 나간다.
     */
    private static double requireDays(double days) {
        if (!LeaveDays.isValidUsage(days)) {
            throw LeaveException.invalidLeaveUsageDays();
        }
        return days;
    }

    /** 길이를 넘으면 자른다 — 사유는 부가 정보라 요청을 400 으로 되돌릴 만큼은 아니다. */
    private static String trimReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.strip();
        return trimmed.length() <= MAX_REASON_LENGTH ? trimmed : trimmed.substring(0, MAX_REASON_LENGTH);
    }
}
