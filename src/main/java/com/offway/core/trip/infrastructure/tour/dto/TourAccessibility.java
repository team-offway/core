package com.offway.core.trip.infrastructure.tour.dto;

import static com.offway.core.trip.domain.AccessibilityCategory.HEARING;
import static com.offway.core.trip.domain.AccessibilityCategory.INFANT;
import static com.offway.core.trip.domain.AccessibilityCategory.MOBILITY;
import static com.offway.core.trip.domain.AccessibilityCategory.VISUAL;

import com.offway.core.trip.domain.AccessibilityCategory;
import com.offway.core.trip.service.dto.AccessibilityFeature;
import com.offway.core.trip.service.dto.PoiAccessibility;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * TourAPI 무장애정보(detailWithTour2) 원시 응답 — 이용약자 편의를 담은 30여 개 필드. 대부분 장소마다 비어 있고, 값이 채워진
 * 필드만 실제 등록된 편의다.
 *
 * <p>{@link #toPoiAccessibility()} 가 값이 있는 필드만 골라 분류·항목명을 붙인 {@link AccessibilityFeature} 목록으로 접는다.
 * 필드→(분류·항목명) 대응은 {@link Facility} enum 이 단일 출처로 들고 있어, 서비스에 {@code if (blank)} 분기를 흩뿌리지 않는다.
 */
public record TourAccessibility(
        String contentId,
        // 이동약자
        String parking,
        String publicTransport,
        String route,
        String ticketOffice,
        String promotion,
        String wheelchair,
        String exit,
        String elevator,
        String restroom,
        String auditorium,
        String room,
        String handicapEtc,
        // 시각장애
        String brailleBlock,
        String helpDog,
        String guideHuman,
        String audioGuide,
        String bigPrint,
        String braillePromotion,
        String guideSystem,
        String blindHandicapEtc,
        // 청각장애
        String signGuide,
        String videoGuide,
        String hearingRoom,
        String hearingHandicapEtc,
        // 영유아·가족
        String stroller,
        String lactationRoom,
        String babySpareChair,
        String infantsFamilyEtc) {

    /** 값이 채워진 편의만 분류·항목명을 붙여 접는다. */
    public PoiAccessibility toPoiAccessibility() {
        List<AccessibilityFeature> features = Arrays.stream(Facility.values())
                .map(facility -> facility.toFeature(this))
                .filter(Objects::nonNull)
                .toList();
        return new PoiAccessibility(contentId, features);
    }

    /** 필드 하나 = 무장애 편의 항목 하나. 분류·표시명과 어떤 필드를 읽을지를 함께 든다(매직 문자열·분기 제거). */
    private enum Facility {
        PARKING(MOBILITY, "장애인 주차장", TourAccessibility::parking),
        PUBLIC_TRANSPORT(MOBILITY, "대중교통 접근", TourAccessibility::publicTransport),
        ROUTE(MOBILITY, "이동 동선", TourAccessibility::route),
        TICKET_OFFICE(MOBILITY, "매표소", TourAccessibility::ticketOffice),
        PROMOTION(MOBILITY, "안내·홍보물", TourAccessibility::promotion),
        WHEELCHAIR(MOBILITY, "휠체어", TourAccessibility::wheelchair),
        EXIT(MOBILITY, "주출입구", TourAccessibility::exit),
        ELEVATOR(MOBILITY, "엘리베이터", TourAccessibility::elevator),
        RESTROOM(MOBILITY, "장애인 화장실", TourAccessibility::restroom),
        AUDITORIUM(MOBILITY, "강당·공연장", TourAccessibility::auditorium),
        ROOM(MOBILITY, "객실", TourAccessibility::room),
        HANDICAP_ETC(MOBILITY, "기타 편의", TourAccessibility::handicapEtc),

        BRAILLE_BLOCK(VISUAL, "점자블록", TourAccessibility::brailleBlock),
        HELP_DOG(VISUAL, "안내견 동반", TourAccessibility::helpDog),
        GUIDE_HUMAN(VISUAL, "안내 요원", TourAccessibility::guideHuman),
        AUDIO_GUIDE(VISUAL, "음성 안내", TourAccessibility::audioGuide),
        BIG_PRINT(VISUAL, "큰 활자 자료", TourAccessibility::bigPrint),
        BRAILLE_PROMOTION(VISUAL, "점자 안내물", TourAccessibility::braillePromotion),
        GUIDE_SYSTEM(VISUAL, "유도·안내 시스템", TourAccessibility::guideSystem),
        BLIND_HANDICAP_ETC(VISUAL, "기타 편의", TourAccessibility::blindHandicapEtc),

        SIGN_GUIDE(HEARING, "수어 안내", TourAccessibility::signGuide),
        VIDEO_GUIDE(HEARING, "영상 안내", TourAccessibility::videoGuide),
        HEARING_ROOM(HEARING, "청각 안내실", TourAccessibility::hearingRoom),
        HEARING_HANDICAP_ETC(HEARING, "기타 편의", TourAccessibility::hearingHandicapEtc),

        STROLLER(INFANT, "유모차 대여", TourAccessibility::stroller),
        LACTATION_ROOM(INFANT, "수유실", TourAccessibility::lactationRoom),
        BABY_SPARE_CHAIR(INFANT, "유아용 의자", TourAccessibility::babySpareChair),
        INFANTS_FAMILY_ETC(INFANT, "기타 편의", TourAccessibility::infantsFamilyEtc);

        private final AccessibilityCategory category;
        private final String name;
        private final Function<TourAccessibility, String> extractor;

        Facility(AccessibilityCategory category, String name, Function<TourAccessibility, String> extractor) {
            this.category = category;
            this.name = name;
            this.extractor = extractor;
        }

        /** 이 필드가 채워져 있으면 편의 항목으로, 비어 있으면 {@code null}. */
        private AccessibilityFeature toFeature(TourAccessibility raw) {
            String detail = extractor.apply(raw);
            if (detail == null || detail.isBlank()) {
                return null;
            }
            return new AccessibilityFeature(category, name, detail.trim());
        }
    }
}
