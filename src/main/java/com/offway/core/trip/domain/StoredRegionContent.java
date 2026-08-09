package com.offway.core.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지역 콘텐츠의 <b>영속 형태</b>(#193) — {@link RegionContent} 를 DB 에 담는다.
 *
 * <p><b>왜 도메인 record 와 따로 두나.</b> {@link RegionContent} 는 병합·충분도 판단을 하는 값객체다. 거기에
 * JPA 를 얹으면 record 를 포기해야 하고, 저장 표현(카테고리를 쉼표로 잇는 것)이 도메인 규칙에 섞인다.
 * 이 클래스는 저장 표현만 알고, 도메인 값으로 오가는 변환을 스스로 책임진다.
 *
 * <p><b>인접 병합까지 끝난 결과</b>가 들어온다. 예전에는 요청 경로에서 89곳 팬아웃 + 인접 50km 병합을 했는데,
 * 인접 관계는 고정 좌표에서 나오는 값이라 미리 계산해도 틀어지지 않는다.
 */
@Entity
@Table(name = "region_content")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredRegionContent {

    /** 카테고리 목록의 저장 구분자. */
    private static final String CATEGORY_DELIMITER = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Column(name = "content_count", nullable = false)
    private int contentCount;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** {@link Category} 이름을 쉼표로 이은 값. 빈 목록이면 빈 문자열이다. */
    @Column(nullable = false, length = 200)
    private String categories;

    @Column(name = "neighbor_included", nullable = false)
    private boolean neighborIncluded;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private StoredRegionContent(Long regionId, RegionContent content, LocalDateTime updatedAt) {
        this.regionId = Objects.requireNonNull(regionId, "지역 ID는 필수입니다");
        Objects.requireNonNull(content, "지역 콘텐츠는 필수입니다");
        if (content.contentCount() < 0) {
            throw new IllegalArgumentException("볼거리 수는 음수일 수 없습니다: " + content.contentCount());
        }
        this.contentCount = content.contentCount();
        this.imageUrl = content.imageUrl();
        this.categories = content.categories().stream().map(Enum::name).collect(Collectors.joining(CATEGORY_DELIMITER));
        this.neighborIncluded = content.neighborIncluded();
        this.updatedAt = Objects.requireNonNull(updatedAt, "갱신 시각은 필수입니다");
    }

    /** 도메인 값을 저장 형태로. */
    public static StoredRegionContent of(Long regionId, RegionContent content, LocalDateTime updatedAt) {
        return new StoredRegionContent(regionId, content, updatedAt);
    }

    /**
     * 저장 형태를 도메인 값으로.
     *
     * <p><b>모르는 카테고리 이름은 버린다.</b> enum 상수가 이름이 바뀌거나 사라져도 그 지역 콘텐츠가 통째로
     * 못 읽히는 것보다, 칩 하나가 빠지는 편이 낫다.
     */
    public RegionContent toRegionContent() {
        List<Category> parsed = categories.isBlank()
                ? List.of()
                : Arrays.stream(categories.split(CATEGORY_DELIMITER))
                        .map(String::strip)
                        .filter(name -> !name.isBlank())
                        .map(StoredRegionContent::categoryOrNull)
                        .filter(Objects::nonNull)
                        .toList();
        return new RegionContent(contentCount, imageUrl, parsed, neighborIncluded);
    }

    private static Category categoryOrNull(String name) {
        try {
            return Category.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
