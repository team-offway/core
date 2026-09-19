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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private VisitOutcome outcome;

    /** 답한 날. 물어본 날이 아니라 답한 날이다 — 언제부터 다시 안 물어보는지의 기준이다. */
    @Column(name = "answered_on", nullable = false)
    private LocalDate answeredOn;

    /**
     * 여행지 별점 1~5 — 지자체 전달용 피드백(#592). <b>없을 수 있다</b>(건너뛰기).
     *
     * <p>값객체({@link TripFeedback})가 검증을 소유하고, 여기에는 풀어서 담는다. 컴포넌트 둘을
     * {@code @Embedded} 로 묶지 않은 이유는 <b>조회가 이 두 칸만 본다</b>는 것이다 — 지역별 집계는
     * {@code AVG(rating)} 과 코멘트 목록이라, 값객체로 감싸면 그 SQL 이 더 멀어진다.
     */
    /*
     * columnDefinition 을 적는 이유 — 1~5 를 담는 값이라 저장은 TINYINT(1바이트)가 맞는데,
     * Hibernate 는 Integer 필드에 INTEGER 를 기대해 스키마 검증이 부팅을 막는다("wrong column type
     * encountered in column [rating]; found [tinyint], but expecting [integer]"). @Lob 과 MySQL TEXT
     * 에서 겪은 것과 같은 함정이다(#566). 로컬·테스트·운영이 전부 MySQL 이라 방언을 적는 대가가 없다.
     */
    @Column(name = "rating", columnDefinition = "TINYINT")
    private Integer rating;

    /**
     * 남기고 싶은 말 — <b>사용자 입력 원본</b>이다(#592).
     *
     * <p><b>로그에 남기지 않는다.</b> 사용자가 친 그대로이고 지자체로 전달되는 값이다.
     *
     * <p>공백뿐인 값은 {@link TripFeedback#of} 가 {@code null} 로 접어 들어온다 — 있는 것처럼 보이는
     * 빈 행이 집계를 흐리지 않게.
     */
    @Column(name = "comment", length = TripFeedback.MAX_COMMENT_LENGTH)
    private String comment;

    private TripOutcome(
            UUID userId, Long courseId, VisitOutcome outcome, LocalDate answeredOn, TripFeedback feedback) {
        this.userId = Objects.requireNonNull(userId, "사용자 ID는 필수입니다");
        this.courseId = Objects.requireNonNull(courseId, "코스 ID는 필수입니다");
        this.outcome = Objects.requireNonNull(outcome, "여행 결과는 필수입니다");
        this.answeredOn = Objects.requireNonNull(answeredOn, "답한 날짜는 필수입니다");
        Objects.requireNonNull(feedback, "평가는 필수입니다(없으면 TripFeedback.none())");
        // **안 간 여행에는 평가가 성립하지 않는다.** 누가 만들든 같은 결과가 나오게 도메인이 막는다 —
        // 서비스에서만 막으면 다른 호출부가 생길 때 그 규칙이 빠진다.
        if (!outcome.deductsLeave() && feedback.isPresent()) {
            throw ItineraryException.feedbackOnUnvisitedTrip();
        }
        this.rating = feedback.rating();
        this.comment = feedback.comment();
    }

    /** 입력에서 곧바로 도출되는 값이라 빌더가 아니라 팩토리다(조립이면 빌더, 계산이면 팩토리). */
    public static TripOutcome of(UUID userId, long courseId, VisitOutcome outcome, LocalDate answeredOn) {
        return new TripOutcome(userId, courseId, outcome, answeredOn, TripFeedback.none());
    }

    /** 여행지 평가까지 함께 답한다(#592). 평가가 비어 있으면 위 팩토리와 같다. */
    public static TripOutcome of(
            UUID userId, long courseId, VisitOutcome outcome, LocalDate answeredOn, TripFeedback feedback) {
        return new TripOutcome(userId, courseId, outcome, answeredOn, feedback);
    }

    /** 남긴 평가 — 없으면 {@link TripFeedback#none()}. */
    public TripFeedback feedback() {
        return TripFeedback.of(rating, comment);
    }
}
