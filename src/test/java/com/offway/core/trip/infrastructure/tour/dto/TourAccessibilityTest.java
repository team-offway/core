package com.offway.core.trip.infrastructure.tour.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.AccessibilityCategory;
import com.offway.core.trip.service.dto.AccessibilityFeature;
import com.offway.core.trip.service.dto.PoiAccessibility;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TourAPI 무장애 원시응답 → 편의 목록 접기 단위 테스트. 값이 채워진 필드만 분류·항목명을 붙여 나오고, 빈 필드는 제외되는지 검증한다.
 */
class TourAccessibilityTest {

    @Test
    void 값이_채워진_필드만_분류와_항목명을_붙여_편의로_접는다() {
        TourAccessibility raw = builder()
                .wheelchair("대여가능")
                .restroom("장애인 화장실 있음")
                .audioGuide("음성안내 있음")
                .signGuide("수어 안내 있음")
                .lactationRoom("수유실 있음")
                .build();

        PoiAccessibility result = raw.toPoiAccessibility();
        List<AccessibilityFeature> features = result.features();

        assertEquals(5, features.size());
        assertTrue(features.stream()
                .anyMatch(f -> f.name().equals("휠체어")
                        && f.category() == AccessibilityCategory.MOBILITY
                        && f.detail().equals("대여가능")));
        assertTrue(features.stream()
                .anyMatch(f -> f.name().equals("음성 안내") && f.category() == AccessibilityCategory.VISUAL));
        assertTrue(features.stream()
                .anyMatch(f -> f.name().equals("수어 안내") && f.category() == AccessibilityCategory.HEARING));
        assertTrue(features.stream()
                .anyMatch(f -> f.name().equals("수유실") && f.category() == AccessibilityCategory.INFANT));
    }

    @Test
    void 공백_문자열_필드는_편의로_내지_않는다() {
        TourAccessibility raw = builder()
                .wheelchair("대여가능")
                .exit("") // 빈 문자열 → 제외
                .parking("   ") // 공백만 → 제외
                .build();

        List<AccessibilityFeature> features = raw.toPoiAccessibility().features();

        assertEquals(1, features.size());
        assertEquals("휠체어", features.getFirst().name());
    }

    @Test
    void 등록된_편의가_하나도_없으면_빈_목록이다() {
        List<AccessibilityFeature> features = builder().build().toPoiAccessibility().features();
        assertTrue(features.isEmpty());
    }

    @Test
    void 상세문구의_앞뒤_공백은_다듬어진다() {
        TourAccessibility raw = builder().wheelchair("  대여가능  ").build();
        assertEquals("대여가능", raw.toPoiAccessibility().features().getFirst().detail());
    }

    /** 29개 필드를 일일이 넘기지 않도록 테스트용 빌더 — 채우지 않은 필드는 null. */
    private static Builder builder() {
        return new Builder();
    }

    private static final class Builder {
        private String wheelchair;
        private String restroom;
        private String audioGuide;
        private String signGuide;
        private String lactationRoom;
        private String exit;
        private String parking;

        Builder wheelchair(String v) {
            this.wheelchair = v;
            return this;
        }

        Builder restroom(String v) {
            this.restroom = v;
            return this;
        }

        Builder audioGuide(String v) {
            this.audioGuide = v;
            return this;
        }

        Builder signGuide(String v) {
            this.signGuide = v;
            return this;
        }

        Builder lactationRoom(String v) {
            this.lactationRoom = v;
            return this;
        }

        Builder exit(String v) {
            this.exit = v;
            return this;
        }

        Builder parking(String v) {
            this.parking = v;
            return this;
        }

        TourAccessibility build() {
            return new TourAccessibility(
                    "126508",
                    parking, null, null, null, null, wheelchair, exit, null, restroom, null, null, null,
                    null, null, null, audioGuide, null, null, null, null,
                    signGuide, null, null, null,
                    null, lactationRoom, null, null);
        }
    }
}
