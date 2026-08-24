package com.offway.core.leave.domain;

import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * <p><b>되돌리는 길은 행 삭제다</b>(#265·#113). 예전엔 음수 내역을 하나 더 쌓아 상쇄했는데, 같은 취소가 두 번
 * 들어오면 합이 음수로 내려가 잔여가 총 연차를 넘었다. 수동 내역은 {@code DELETE /me/usages/{id}} 로,
 * 코스 차감은 코스의 차감 취소로 지운다.
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

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    /** 연차를 쓴 날. */
    @Column(name = "used_on", nullable = false)
    private LocalDate usedOn;

    /**
     * 쓴 일수(0.25 단위 양수). 코스 차감만 0 을 허용한다(#212).
     *
     * <p><b>음수 행이 남아 있을 수 있다</b> — 삭제 API 가 없던 시절의 상쇄 등록이다(#265). 새로 들어오는 것은
     * {@link #requireDays} 가 막지만, 이미 적재된 행은 그대로 읽힌다(하이드레이션은 생성자를 거치지 않는다).
     */
    @Column(name = "days", nullable = false)
    private double days;

    @Column(length = MAX_REASON_LENGTH)
    private String reason;

    /** 이 내역을 만든 코스 (수동 입력이면 null). 중복 차감 방지의 키다. */
    @Column(name = "course_id")
    private Long courseId;

    /**
     * 코스 차감 시 첫날에 쓴 연차 (수동 내역이면 null).
     *
     * <p><b>차감량을 다시 계산할 때 필요하다</b>(#170). 여행 날짜가 바뀌면 평일 수·공휴일이 달라져 다시 계산해야
     * 하는데, 그 계산의 입력 중 이것만 어디에도 남지 않았다.
     *
     * <p>{@link #days} 에서 되짚을 수 없다 — 출발일이 주말·공휴일이면 반차를 골라도 차감이 정수로 나오므로
     * 소수점 유무가 단위와 대응하지 않는다. 그 코스를 평일로 옮기면 반차가 조용히 종일로 바뀐다.
     *
     * <p>문자열로 저장한다({@code EnumType.STRING}). 서수로 저장하면 상수 순서를 바꾸는 순간 기존 행의 뜻이
     * 조용히 달라진다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "start_day_leave", length = 20)
    private StartDayLeave startDayLeave;

    /**
     * 예전 컬럼 — <b>읽지 않고 쓰기만 한다</b>(#138).
     *
     * <p>반반차가 생겨 {@link #startDayLeave} 로 옮겼다. 이 컬럼을 같은 배포에서 지우지 않는 이유는
     * add → backfill → drop 3단계 규칙이다(persistence-convention) — 롤백한 이전 버전이 이 값을 읽으므로
     * 그때까지는 함께 채워 둔다. 새 컬럼만 쓰는 것이 확인되면 별도 마이그레이션으로 지운다.
     */
    @Column(name = "half_day_start")
    private Boolean halfDayStart;

    private LeaveUsage(
            UUID userId,
            LocalDate usedOn,
            double days,
            String reason,
            Long courseId,
            StartDayLeave startDayLeave) {
        this.userId = Objects.requireNonNull(userId, "userId 는 null 일 수 없습니다.");
        this.usedOn = Objects.requireNonNull(usedOn, "usedOn 은 null 일 수 없습니다.");
        this.days = requireDays(days, courseId);
        this.reason = trimReason(reason);
        this.courseId = courseId;
        this.startDayLeave = startDayLeave;
        // 전환기 동안 예전 컬럼도 채운다. 반반차는 예전 계약으로 표현할 수 없어 반차로 내려앉는데,
        // 그 값을 읽는 것은 롤백한 이전 버전뿐이고 그 버전에는 반반차 개념이 애초에 없다.
        this.halfDayStart = startDayLeave == null ? null : !startDayLeave.isFullDay();
    }

    /** 사용자가 직접 남기는 내역. */
    public static LeaveUsage manual(UUID userId, LocalDate usedOn, double days, String reason) {
        return new LeaveUsage(userId, usedOn, days, reason, null, null);
    }

    /**
     * 코스 확정으로 생기는 내역 — {@code courseId} 가 중복 차감을 막는다(#91).
     *
     * @param startDayLeave 첫날에 쓴 연차. 날짜를 고칠 때 차감량을 다시 계산하는 입력이라 함께 남긴다(#170)
     */
    public static LeaveUsage forCourse(
            UUID userId,
            LocalDate usedOn,
            double days,
            String reason,
            long courseId,
            StartDayLeave startDayLeave) {
        return new LeaveUsage(
                userId, usedOn, days, reason, courseId, Objects.requireNonNull(startDayLeave, "startDayLeave"));
    }

    /**
     * 첫날에 쓴 연차.
     *
     * <p>수동 내역은 null 이라 종일로 답한다 — 그쪽은 첫날 단위 개념이 없다. 백필 전에 남아 있는 행도 같은
     * 값을 받으므로, 마이그레이션이 늦어도 결과가 예전과 달라지지 않는다.
     */
    public StartDayLeave startDayLeave() {
        return startDayLeave == null ? StartDayLeave.DEFAULT : startDayLeave;
    }

    /** 사용자가 직접 남긴 내역인가 — 코스 확정으로 생긴 행과 규칙이 다르다. */
    public boolean isManual() {
        return courseId == null;
    }

    /**
     * 사용자가 손으로 지울 수 있는 내역인지 확인한다(#265) — <b>코스 확정 내역은 거절한다</b>(409).
     *
     * <p>그 행은 차감량이자 <b>확정 표식</b>이다. 연차 화면에서 지우면 코스는 확정인데 연차는 안 깎인 상태가
     * 남고, 코스 삭제·날짜 변경이 그 행을 전제로 도는 것도 함께 어긋난다. 되돌리는 길은 이미 있다 —
     * 코스의 차감 취소가 코스와 연차를 한 덩어리로 되돌린다(#113).
     *
     * <p>404 로 감추지 않는다. 자기 내역이 화면에 보이는데 "없다" 고 답하면 사용자는 버그로 읽는다.
     * 지울 수 없는 이유를 알려줘야 코스 화면으로 갈 수 있다.
     */
    public void requireManuallyDeletable() {
        if (!isManual()) {
            throw LeaveException.courseLeaveUsageNotDeletable();
        }
    }

    /**
     * 코스의 여행 날짜가 바뀌어 차감을 다시 잡는다(#170) — 쓴 날과 일수를 함께 옮긴다.
     *
     * <p>지우고 다시 넣지 않는다. {@code uk_leave_usage_user_course} 가 코스당 한 행을 강제하는데(#91),
     * 같은 트랜잭션 안에서 delete·insert 를 하면 Hibernate 가 flush 순서를 보장하지 않아 제약에 걸릴 수 있다.
     *
     * <p>반차 여부는 그대로 둔다 — 사용자가 고친 것은 날짜뿐이다.
     */
    public void moveTo(LocalDate usedOn, double days) {
        if (courseId == null) {
            throw new IllegalStateException("수동 내역은 코스 날짜 변경으로 옮길 수 없습니다: id=" + id);
        }
        // 검증을 먼저 끝내고 대입한다 — 중간에 거절되면 날짜만 바뀐 반쪽 상태가 남는다.
        LocalDate movedTo = Objects.requireNonNull(usedOn, "usedOn 은 null 일 수 없습니다.");
        double moved = requireDays(days, courseId);
        this.usedOn = movedTo;
        this.days = moved;
    }

    /**
     * 계약 예외(400)를 던진다 — {@code IllegalArgumentException} 이 아니다.
     *
     * <p>지금은 요청 DTO 가 먼저 걸러 여기 닿지 않지만, 코스 확정 차감(#91)이 {@link #forCourse} 를 서비스에서
     * 직접 부르면 DTO 를 거치지 않고 들어온다. 그때 불변식 예외를 던지면 클라이언트 계약 위반이 500 으로 나간다.
     */
    private static double requireDays(double days, Long courseId) {
        // 음수 수동 등록은 사유를 갈라 답한다(#276) — "단위가 아님" 과 뭉뚱그리면 화면이 "삭제로
        // 취소하세요" 를 안내할 수 없다. 코스 차감은 애초에 음수가 들어올 길이 없어 갈라도 소득이 없다.
        if (courseId == null && LeaveDays.isReversal(days)) {
            throw LeaveException.leaveUsageReversalNotAllowed();
        }
        // 코스 차감은 0 을 허용한다(#212). 주말·공휴일뿐인 구간이면 깎을 연차가 없는데, 그것도 확정이다 —
        // 그 행이 차감량이자 확정 표식이기 때문이다. 수동 내역은 순수 증감 장부라 0 이 그대로 소음이다.
        boolean valid = courseId == null
                ? LeaveDays.isValidUsage(days)
                : LeaveDays.isValidCourseDeduction(days);
        if (!valid) {
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
