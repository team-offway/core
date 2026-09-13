package com.offway.core.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 반려동반 가능 장소 한 건(#566) — 슬롯 모달의 "반려동물 동반" 칩과 그 상세.
 *
 * <h2>후보가 아니라 표식이다</h2>
 *
 * <p>{@link CampingPlace} 와 다르다. 그쪽은 <b>새 후보</b>를 데려와 숙박 풀을 채우는데, 이 표는 이미
 * 풀에 있는 장소에 <b>"데려갈 수 있다" 를 붙인다.</b> 그래서 좌표도 사진도 담지 않는다 — 동선에
 * 올리는 값은 원래 후보가 들고 있다.
 *
 * <p>매칭은 {@code contentId} 로 한다. 우리 관광 API 후보도 같은 체계를 쓰기 때문이다. 인허가·
 * 국가유산·고캠핑 출처는 이 값이 없어 판정할 수 없고, 그때는 <b>칩을 띄우지 않는다</b> — 모르는
 * 것을 "불가" 로 내리는 것과 다르다.
 *
 * <h2>칩 하나로는 정직하지 않아 상세를 함께 든다</h2>
 *
 * <p>실측(2026-09-13, 태안 15건 전수)에서 <b>절반이 "일부구역 동반가능"</b> 이었다(전구역 8 · 일부구역 7).
 * 체중 제한도 셋 있었다(5kg·9kg·10kg 이하). "반려동물 동반" 칩만 띄우면 그 조건이 가려지므로,
 * 상세를 미리 받아 모달 응답에 실어 칩을 눌렀을 때 왕복 없이 열리게 한다.
 *
 * <p><b>이용 가능 시설은 대개 빈다.</b> {@code facilities}·{@code providedItems}·{@code rentalItems} 중
 * 하나라도 채워진 곳이 15건 중 1건이었다. 값을 들고는 있지만 화면은 없을 때를 전제로 그려야 한다.
 *
 * <p>지역은 다른 도메인(region)의 레퍼런스라 raw {@code regionId} 로만 참조한다(persistence-convention).
 */
@Entity
@Table(
        name = "pet_friendly_place",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_pet_friendly_place_content", columnNames = "content_id"),
        indexes = {@Index(name = "idx_pet_friendly_place_region", columnList = "region_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetFriendlyPlace {

    private static final int MAX_CONTENT_ID_LENGTH = 50;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_AREA_LENGTH = 100;
    private static final int MAX_PET_LENGTH = 200;
    private static final int MAX_MATTER_LENGTH = 500;

    /** 전 구역에 데려갈 수 있다는 원본 표기. 이 값이 아니면 구역 제한이 있는 것으로 본다. */
    private static final String WHOLE_AREA = "전구역 동반가능";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false, length = MAX_CONTENT_ID_LENGTH)
    private String contentId;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    /** {@code acmpyTypeCd} — "전구역 동반가능" / "일부구역 동반가능". */
    @Column(name = "accompany_area", length = MAX_AREA_LENGTH)
    private String accompanyArea;

    /** {@code acmpyPsblCpam} — "전 견종 동반 가능" / "5kg 이내 소형견" 등. */
    @Column(name = "accompany_pet", length = MAX_PET_LENGTH)
    private String accompanyPet;

    /** {@code acmpyNeedMtr} — "목줄 착용" 등. */
    @Column(name = "required_matter", length = MAX_MATTER_LENGTH)
    private String requiredMatter;

    /**
     * {@code etcAcmpyInfo} — 줄바꿈이 섞여 온다. 길이가 제각각이라 TEXT 로 둔다.
     *
     * <p>{@code columnDefinition} 을 함께 준다 — {@code @Lob} 만 두면 Hibernate 가 {@code tinytext} 를
     * 기대해 스키마 검증이 부팅을 막는다({@link CampingPlace#getIntro()} 와 같은 처리).
     */
    @Lob
    @Column(name = "etc_info", columnDefinition = "TEXT")
    private String etcInfo;

    /** {@code relaAcdntRiskMtr} — 사고 위험 사항. */
    @Lob
    @Column(name = "risk_matter", columnDefinition = "TEXT")
    private String riskMatter;

    @Column(name = "facilities", length = MAX_MATTER_LENGTH)
    private String facilities;

    @Column(name = "provided_items", length = MAX_MATTER_LENGTH)
    private String providedItems;

    @Column(name = "rental_items", length = MAX_MATTER_LENGTH)
    private String rentalItems;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Builder
    private PetFriendlyPlace(
            String contentId,
            Long regionId,
            String name,
            String accompanyArea,
            String accompanyPet,
            String requiredMatter,
            String etcInfo,
            String riskMatter,
            String facilities,
            String providedItems,
            String rentalItems,
            LocalDateTime fetchedAt) {
        this.contentId = requireText(contentId, MAX_CONTENT_ID_LENGTH, "콘텐츠 식별자");
        this.regionId = Objects.requireNonNull(regionId, "지역 id 는 null 일 수 없습니다.");
        this.name = requireText(name, MAX_NAME_LENGTH, "장소명");
        this.accompanyArea = trimToLength(accompanyArea, MAX_AREA_LENGTH);
        this.accompanyPet = trimToLength(accompanyPet, MAX_PET_LENGTH);
        this.requiredMatter = trimToLength(requiredMatter, MAX_MATTER_LENGTH);
        this.etcInfo = trimToNull(etcInfo);
        this.riskMatter = trimToNull(riskMatter);
        this.facilities = trimToLength(facilities, MAX_MATTER_LENGTH);
        this.providedItems = trimToLength(providedItems, MAX_MATTER_LENGTH);
        this.rentalItems = trimToLength(rentalItems, MAX_MATTER_LENGTH);
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "수집 시각은 null 일 수 없습니다.");
    }

    /**
     * 전 구역에 데려갈 수 있는가 — <b>아니면 구역 제한이 있다.</b>
     *
     * <p>실측에서 절반이 "일부구역" 이었다. 화면이 이 값으로 칩 문구를 가를 수 있게 판정을 도메인에
     * 둔다 — 원본 문자열을 클라이언트가 비교하면 표기가 바뀌는 날 조용히 틀린다.
     *
     * <p><b>값이 없으면 참이라고 답하지 않는다.</b> 모르는 것을 "전 구역 가능" 으로 내리면 사용자가
     * 가서야 알게 된다.
     */
    public boolean allowsWholeArea() {
        return WHOLE_AREA.equals(accompanyArea);
    }

    /**
     * 동반 조건을 하나라도 아는가 — 칩을 눌렀을 때 열 내용이 있는지.
     *
     * <p>거짓이면 칩만 띄우고 상세를 열 것이 없다. 그때 빈 모달을 띄우면 사용자는 로딩이 실패한
     * 줄 안다.
     */
    public boolean knowsCondition() {
        return accompanyArea != null || accompanyPet != null || requiredMatter != null || etcInfo != null;
    }

    /**
     * 이용 가능 시설·품목을 아는가 — <b>대개 거짓이다</b>(실측 15건 중 1건).
     *
     * <p>화면이 이 값으로 그 칸을 접는다. 빈 칸을 남기면 "정보가 있는데 못 받아왔다" 처럼 보인다.
     */
    public boolean knowsFacilities() {
        return facilities != null || providedItems != null || rentalItems != null;
    }

    private static String requireText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "는 비어 있을 수 없습니다");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + "가 너무 깁니다: " + trimmed.length());
        }
        return trimmed;
    }

    /**
     * 선택 값은 <b>길다고 버리지 않고 잘라 담는다</b>({@link CampingPlace} 와 같은 이유).
     *
     * <p>유의사항 한 줄이 길다고 장소를 통째로 버리면 "데려갈 수 있다" 는 사실까지 잃는데, 그쪽이
     * 이 표의 존재 이유다.
     */
    private static String trimToLength(String value, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
