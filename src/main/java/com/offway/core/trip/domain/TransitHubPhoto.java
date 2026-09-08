package com.offway.core.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 역·터미널·항구 한 곳의 사진(#450) — 관광사진갤러리에서 지점 이름으로 찾아 둔 것.
 *
 * <p><b>못 찾아도 행을 남긴다.</b> {@code imageUrl} 이 {@code null} 인 것은 "물어봤는데 없다" 이고,
 * 행 자체가 없는 것은 "아직 안 물어봄" 이다. 둘을 같은 값으로 뭉치면 배치가 매번 같은 지점을 다시 묻거나,
 * 반대로 새로 올라온 사진을 영영 못 본다.
 */
@Entity
@Table(name = "transit_hub_photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransitHubPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 지점명 — 검색어이자 키다. 같은 지점이 고속·시외에 다른 코드로 올라 있어 코드로 잡지 않는다. */
    @Column(name = "hub_name", nullable = false, length = 64)
    private String hubName;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 100)
    private String photographer;

    @Column(length = 300)
    private String title;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    private TransitHubPhoto(
            String hubName, String imageUrl, String photographer, String title, LocalDateTime fetchedAt) {
        this.hubName = requireHubName(hubName);
        this.imageUrl = imageUrl;
        this.photographer = photographer;
        this.title = title;
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "조회 시각은 필수입니다");
    }

    /** 사진을 찾은 경우. */
    public static TransitHubPhoto found(
            String hubName, String imageUrl, String photographer, String title, LocalDateTime fetchedAt) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("사진을 찾았다면 주소가 있어야 합니다: " + hubName);
        }
        return new TransitHubPhoto(hubName, imageUrl, photographer, title, fetchedAt);
    }

    /** 물어봤는데 없는 경우 — 그것도 결과라 남긴다. */
    public static TransitHubPhoto missing(String hubName, LocalDateTime fetchedAt) {
        return new TransitHubPhoto(hubName, null, null, null, fetchedAt);
    }

    /** 화면에 쓸 사진 — 못 찾았으면 빈 값이다. */
    public Optional<String> usableImageUrl() {
        return Optional.ofNullable(imageUrl).filter(url -> !url.isBlank());
    }

    /** 같은 지점을 다시 물었을 때 값을 갈아 끼운다 — 행을 지우고 다시 넣으면 id 가 흔들린다. */
    public void refresh(String imageUrl, String photographer, String title, LocalDateTime fetchedAt) {
        this.imageUrl = imageUrl;
        this.photographer = photographer;
        this.title = title;
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "조회 시각은 필수입니다");
    }

    private static String requireHubName(String hubName) {
        if (hubName == null || hubName.isBlank()) {
            throw new IllegalArgumentException("지점명은 필수입니다");
        }
        return hubName;
    }
}
