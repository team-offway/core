package com.offway.core.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 축제가 언제 열리는지(#388).
 *
 * <h2>왜 따로 서 있나</h2>
 *
 * <p>{@code region_poi} 는 매월 {@code base_ym} 단위로 다시 채워진다. 거기에 두면 갱신 주기가 장소 풀에
 * 묶여, 축제 날짜가 바뀌어도 다음 달까지 못 고친다 — 축제는 날짜가 바뀌고 취소된다.
 *
 * <p>{@code poi_intro}(운영시간·휴무일)가 같은 이유로 {@code content_id} 를 키로 따로 서 있다. 그 선례를
 * 따른다.
 *
 * <h2>모르는 축제는 행이 없다</h2>
 *
 * <p>TourAPI 가 날짜를 안 주는 행이 있다. 그때는 <b>행을 만들지 않는다</b> — 없는 것과 "모른다" 를
 * 구분하려는 것이고, 조회에서 행이 없으면 그 축제는 지금처럼 평범한 볼거리로 남는다.
 */
@Entity
@Table(name = "festival_period")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FestivalPeriod {

    @Id
    @Column(name = "content_id", length = 64)
    private String contentId;

    @Column(name = "event_start", nullable = false)
    private LocalDate eventStart;

    @Column(name = "event_end", nullable = false)
    private LocalDate eventEnd;

    /** 조회 당시 축제명 — 사람이 로그를 읽을 때 쓴다. 화면은 {@code region_poi} 의 제목을 쓴다. */
    @Column(length = 200)
    private String title;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    /**
     * <b>불변식은 여기서 지킨다.</b> 시작이 종료보다 늦으면 {@link #isOpenOn} 이 어떤 날짜에도 참이 아니라,
     * 있는 축제를 우리가 지우는 셈이 된다. 어댑터도 같은 값을 거르지만 엔티티는 누가 만들든 스스로
     * 유효함을 보장하는 최후의 보루다.
     */
    @Builder
    private FestivalPeriod(String contentId, LocalDate eventStart, LocalDate eventEnd, String title,
            LocalDateTime fetchedAt) {
        this.contentId = requireText(contentId);
        this.eventStart = Objects.requireNonNull(eventStart, "행사 시작일은 null 일 수 없습니다.");
        this.eventEnd = Objects.requireNonNull(eventEnd, "행사 종료일은 null 일 수 없습니다.");
        if (eventStart.isAfter(eventEnd)) {
            throw new IllegalArgumentException("행사 시작일이 종료일보다 늦습니다: " + eventStart + " ~ " + eventEnd);
        }
        this.title = title;
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "조회 시각은 null 일 수 없습니다.");
    }

    /** 여행일에 이 축제가 열려 있는가 — 시작일·종료일 당일을 포함한다. */
    public boolean isOpenOn(LocalDate date) {
        return !date.isBefore(eventStart) && !date.isAfter(eventEnd);
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value, "contentId 는 null 일 수 없습니다.");
        if (value.isBlank()) {
            throw new IllegalArgumentException("contentId 는 비어 있을 수 없습니다.");
        }
        return value.strip();
    }
}
