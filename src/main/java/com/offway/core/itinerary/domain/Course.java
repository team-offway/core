package com.offway.core.itinerary.domain;

import com.offway.core.transport.domain.TransportMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자동 생성된 여행 코스(애그리거트 루트) — 한 지역의 날짜별 타임라인. 코스 생성(#30)이 조립하고, 저장·조회(#33)가 영속화한다.
 *
 * <p>지역은 다른 도메인(trip/region)의 레퍼런스라 raw {@code regionId} 로만 참조하고, 하루 일정({@link DaySchedule})은
 * 생명주기를 공유하는 애그리거트 내부라 {@code @OneToMany} 로 함께 다룬다. 혜택·비용은 정책 매칭 결과라 응답 시점에 계산해 붙인다
 * (도메인 상태 제외 — 저장 코스가 정책 변경에 뒤처지지 않게).
 */
@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    /** 코스 상한 — 최대 2박3일(feature-spec F4 · 와이어프레임 캘린더 정책). */
    public static final int MAX_TRAVEL_DAYS = 3;

    /** 게스트 ID 최대 길이 — {@code guest_id} 컬럼 폭과 일치시켜, 초과 입력이 저장 단계 서버 오류로 새지 않게 경계에서 거른다. */
    public static final int MAX_GUEST_ID_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 추천 지역(raw 참조 — 애그리거트 경계 밖). */
    @Column(name = "region_id", nullable = false)
    private Long regionId;

    /** 여행 일수(1~3) — 하루 일정 수에서 도출된다. */
    @Column(name = "travel_days", nullable = false)
    private int travelDays;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Density density;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TransportMode transport;

    /**
     * 여행 시작일(저장 코스만). 생성만 된 코스와 이 컬럼 이전에 저장된 코스는 null 이다.
     *
     * <p>연차 차감 일수를 <b>서버가 다시 계산</b>하는 근거다 — 차감 시점에 클라이언트가 날짜를 보내면 보낸 만큼
     * 차감량이 바뀐다.
     */
    @Column(name = "travel_date")
    private LocalDate travelDate;

    /** 소유 게스트 ID(저장된 코스만) — 로그인 전이라 클라이언트 게스트 식별자로 "내 코스"를 묶는다. 생성만 된 코스는 null. */
    @Column(name = "guest_id", length = MAX_GUEST_ID_LENGTH)
    private String guestId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(
            name = "course_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @OrderBy("dayNumber")
    private List<DaySchedule> days;

    private Course(
            String guestId,
            Long regionId,
            Density density,
            TransportMode transport,
            List<DaySchedule> days,
            LocalDate travelDate) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("코스에는 하루 이상이 있어야 합니다");
        }
        if (days.size() > MAX_TRAVEL_DAYS) {
            throw new IllegalArgumentException("코스는 최대 " + MAX_TRAVEL_DAYS + "일까지입니다: " + days.size());
        }
        requireSequentialDays(days);
        this.guestId = guestId;
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        this.density = Objects.requireNonNull(density, "일정 밀도는 필수입니다");
        this.transport = Objects.requireNonNull(transport, "이동수단은 필수입니다");
        this.days = List.copyOf(days);
        this.travelDays = days.size();
        this.travelDate = travelDate;
    }

    /** 하루 일정들을 묶어 코스를 만든다(생성용, 소유자 없음). 일수 상한(2박3일)과 일차 연속성을 스스로 검증한다. */
    public static Course of(Long regionId, Density density, TransportMode transport, List<DaySchedule> days) {
        return new Course(null, regionId, density, transport, days, null);
    }

    /** 게스트 소유로 코스를 만든다(저장용). 게스트 ID 는 공백일 수 없고 길이 상한을 넘지 않는다(빈 값이면 모든 요청이 한 묶음을 공유). */
    public static Course ownedBy(
            String guestId,
            Long regionId,
            Density density,
            TransportMode transport,
            List<DaySchedule> days,
            LocalDate travelDate) {
        Objects.requireNonNull(guestId, "게스트 ID는 필수입니다");
        if (guestId.isBlank()) {
            throw new IllegalArgumentException("게스트 ID는 비어 있을 수 없습니다");
        }
        if (guestId.length() > MAX_GUEST_ID_LENGTH) {
            throw new IllegalArgumentException("게스트 ID가 너무 깁니다: " + guestId.length());
        }
        return new Course(guestId, regionId, density, transport, days, travelDate);
    }

    /**
     * 여행 종료일 — 시작일에서 일수만큼. 1박2일이면 시작일 다음 날이다.
     *
     * <p>날짜가 없는 코스에서는 부를 수 없다. 부재가 계약(400)이라 {@link #requireTravelDate()} 로 먼저 거른다.
     */
    public LocalDate travelEndDate() {
        if (travelDate == null) {
            throw new IllegalStateException("여행 날짜가 없는 코스의 종료일을 물었습니다: id=" + id);
        }
        return travelDate.plusDays(travelDays - 1L);
    }

    /**
     * 이 여행이 {@code today} 기준으로 끝났는가 — 종료일이 오늘보다 <b>이전</b>이어야 한다.
     *
     * <p>종료 당일은 끝난 것으로 보지 않는다. 아직 여행 중일 수 있다.
     *
     * <p>여행 날짜가 없으면 끝났는지 알 수 없으므로 거짓이다 — 모르는 것을 "끝났다" 로 답하면 안 된다.
     */
    public boolean hasEndedBy(LocalDate today) {
        return travelDate != null && travelEndDate().isBefore(today);
    }

    /**
     * 연차를 차감하려면 여행 날짜가 있어야 한다 — <b>도메인이 스스로 막는다.</b>
     *
     * <p>이 컬럼이 생기기 전에 저장된 코스가 있으므로 <b>멀쩡한 클라이언트가 정상 요청으로 닿을 수 있다</b>. 불변식이
     * 아니라 계약이라 400 이다.
     */
    public void requireTravelDate() {
        if (travelDate == null) {
            throw ItineraryException.travelDateMissing();
        }
    }

    /** 코스 전체 슬롯(장소) 수. */
    public int totalSlots() {
        return days.stream().mapToInt(DaySchedule::slotCount).sum();
    }

    private static void requireSequentialDays(List<DaySchedule> days) {
        for (int i = 0; i < days.size(); i++) {
            int expected = i + 1;
            if (days.get(i).getDayNumber() != expected) {
                throw new IllegalArgumentException(
                        "일차가 1부터 연속이어야 합니다: " + expected + " 위치에 " + days.get(i).getDayNumber());
            }
        }
    }
}
