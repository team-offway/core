package com.offway.core.itinerary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행지 평가 한 건 — <b>익명</b>으로 쌓는다(#592).
 *
 * <h2>왜 사람을 안 담나</h2>
 *
 * 이 값은 <b>지자체에 전달된다.</b> 그러려면 개인을 특정할 수 없어야 한다.
 *
 * <p>처음에는 {@link TripOutcome} 에 컬럼 둘을 더했다. 알맹이가 같고 탈퇴 삭제가 따라온다는 이유였는데,
 * <b>그 표는 {@code userId} 를 들고 있어 평가가 사람에 직접 묶인다.</b> 익명이 되려면 그 연결이 없어야
 * 하고, 그러면 "탈퇴 시 함께 지워진다" 는 장점도 성립하지 않는다 — 지울 것이 애초에 없다.
 *
 * <p><b>코스 id 도 담지 않는다.</b> {@code Course} 가 {@code userId} 를 들고 있어, 코스 참조만 남겨도
 * 한 번의 조인으로 사람이 특정된다. 표를 옮기는 것만으로는 익명이 되지 않는다.
 *
 * <h2>왜 날짜가 아니라 연-월인가</h2>
 *
 * {@code 정선군 + 2026-09-19 + 특징적인 코멘트} 는 소규모 지역에서 좁혀질 수 있다. 우리가 다루는 곳이
 * 인구감소지역이라 지역별 표본이 작다는 점이 특히 그렇다.
 *
 * <p>집계는 월·분기 단위다("2026년 9월 정선 만족도"). 날짜까지 남길 이유가 없으므로 버린다 —
 * <b>재식별 단서를 남기지 않는 것이 공짜일 때는 남기지 않는다.</b>
 *
 * <h2>대가</h2>
 *
 * <b>남긴 평가를 되찾을 수 없다.</b> 누가 썼는지 모르므로 수정·삭제 요구에 응할 방법이 없다. 개인정보가
 * 아니라 삭제 의무가 없다는 것이 근거이지만, 앱 문구가 그 사실을 먼저 알려야 한다.
 *
 * <p>중복도 여기서는 막을 수 없다(막을 키가 없다). {@link TripOutcome} 이 코스당 한 번만 답을 받으므로
 * 평가도 한 번만 들어온다 — <b>두 표를 한 트랜잭션에 쓰는 것</b>이 그 보장을 잇는 조건이다.
 */
@Entity
@Table(name = "region_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionFeedback {

    /** 받은 시기 표기 — 사전순 비교가 곧 시간순이라 범위 조회가 그대로 된다. */
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어느 지역에 대한 평가인가. 지역 마스터는 애그리거트 밖이라 raw ID 다(persistence-convention). */
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    /*
     * columnDefinition 을 적는 이유 — 1~5 를 담는 값이라 저장은 TINYINT(1바이트)가 맞는데,
     * Hibernate 는 Integer 필드에 INTEGER 를 기대해 스키마 검증이 부팅을 막는다("wrong column type
     * encountered in column [rating]; found [tinyint], but expecting [integer]"). @Lob 과 MySQL TEXT
     * 에서 겪은 것과 같은 함정이다(#566). 로컬·테스트·운영이 전부 MySQL 이라 방언을 적는 대가가 없다.
     */
    @Column(name = "rating", columnDefinition = "TINYINT")
    private Integer rating;

    /**
     * 남기고 싶은 말 — <b>사용자 입력 원본</b>이다.
     *
     * <p><b>로그에 남기지 않는다.</b> 사용자가 친 그대로이고 지자체로 전달되는 값이다.
     */
    @Column(name = "comment", length = TripFeedback.MAX_COMMENT_LENGTH)
    private String comment;

    @Column(name = "submitted_ym", nullable = false, length = 7)
    private String submittedYearMonth;

    private RegionFeedback(Long regionId, TripFeedback feedback, String submittedYearMonth) {
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        Objects.requireNonNull(feedback, "평가는 필수입니다");
        // **빈 평가로 행을 만들지 않는다.** 건너뛴 사람의 빈 행이 쌓이면 지역별 집계가 실제보다 많은
        // 의견이 있는 것처럼 보인다. 부르는 쪽이 isPresent() 로 걸러야 한다는 뜻이고, 그 규칙을
        // 도메인이 지킨다 — 누가 만들든 같은 결과가 나오게.
        if (!feedback.isPresent()) {
            throw new IllegalStateException("빈 평가로는 행을 만들지 않습니다");
        }
        this.rating = feedback.rating();
        this.comment = feedback.comment();
        this.submittedYearMonth = Objects.requireNonNull(submittedYearMonth, "받은 시기는 필수입니다");
    }

    /**
     * 받은 날에서 시기(연-월)를 도출해 만든다.
     *
     * <p>날짜를 받아 연-월로 <b>깎는 것이 요점이다</b> — 부르는 쪽이 연-월 문자열을 만들면 형식이
     * 호출부마다 갈리고, 날짜를 그대로 넘기고 싶은 유혹이 남는다.
     */
    public static RegionFeedback of(long regionId, TripFeedback feedback, LocalDate submittedOn) {
        Objects.requireNonNull(submittedOn, "받은 날짜는 필수입니다");
        return new RegionFeedback(regionId, feedback, submittedOn.format(YEAR_MONTH));
    }

    /** 담긴 평가 — 값객체로 돌려준다. */
    public TripFeedback feedback() {
        return TripFeedback.of(rating, comment);
    }
}
