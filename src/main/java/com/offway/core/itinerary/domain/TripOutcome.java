package com.offway.core.itinerary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 지난 여행에 대한 답 — 다녀왔는지, 언제 답했는지(#116).
 *
 * <p>이 행이 있으면 홈 모달이 그 코스를 다시 묻지 않는다. 코스는 애그리거트 경계 밖이라 raw {@code courseId} 로만
 * 참조한다(persistence-convention).
 */
@Entity
@Table(name = "trip_outcome")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_id", nullable = false, length = 64)
    private String guestId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private VisitOutcome outcome;

    /** 답한 날. 물어본 날이 아니라 답한 날이다 — 언제부터 다시 안 물어보는지의 기준이다. */
    @Column(name = "answered_on", nullable = false)
    private LocalDate answeredOn;

    private TripOutcome(String guestId, Long courseId, VisitOutcome outcome, LocalDate answeredOn) {
        this.guestId = requireGuestId(guestId);
        this.courseId = Objects.requireNonNull(courseId, "코스 ID는 필수입니다");
        this.outcome = Objects.requireNonNull(outcome, "여행 결과는 필수입니다");
        this.answeredOn = Objects.requireNonNull(answeredOn, "답한 날짜는 필수입니다");
    }

    /** 입력에서 곧바로 도출되는 값이라 빌더가 아니라 팩토리다(조립이면 빌더, 계산이면 팩토리). */
    public static TripOutcome of(String guestId, long courseId, VisitOutcome outcome, LocalDate answeredOn) {
        return new TripOutcome(guestId, courseId, outcome, answeredOn);
    }

    private static String requireGuestId(String guestId) {
        Objects.requireNonNull(guestId, "게스트 ID는 필수입니다");
        if (guestId.isBlank()) {
            throw new IllegalArgumentException("게스트 ID는 비어 있을 수 없습니다");
        }
        return guestId;
    }
}
