package com.offway.core.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지역에 붙은 라벨 한 건. region 과는 애그리거트가 다르므로 연관관계 대신 raw ID({@code regionId})로 참조한다.
 */
@Entity
@Table(name = "region_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RegionTagType tag;

    private RegionTag(Long regionId, RegionTagType tag) {
        this.regionId = Objects.requireNonNull(regionId, "regionId 는 null 일 수 없습니다.");
        this.tag = Objects.requireNonNull(tag, "tag 는 null 일 수 없습니다.");
    }

    public static RegionTag of(Long regionId, RegionTagType tag) {
        return new RegionTag(regionId, tag);
    }
}
