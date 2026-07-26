package com.offway.core.trip.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * 무드/유형 필터 칩. 칩 하나가 lclsSystm(TourAPI 분류체계) 대분류 코드 묶음에 대응한다(F6).
 *
 * <p>api-spec 공통 enum {@code category} 와 값이 일치한다. 각 상수가 자기 코드 묶음을 들고 {@link #includes} 로 스스로
 * 판정하는 다형성 — 서비스는 타입 스위치 없이 칩에게 물어본다. {@code ALL} 은 필터 없음(전부 통과).
 *
 * <p>코드는 TourAPI {@code lclsSystm1}(대분류)다. 이 코드를 실제 호출에 싣는 배선은 첫 소비자(#23)에서 한다.
 */
public enum Category {

    /** 전체 — 필터 없음. */
    ALL("전체", Set.of()) {
        @Override
        public boolean includes(String lclsSystm1) {
            return true;
        }
    },

    /** 관광지 — lclsSystm 대분류 NA·HS·VE·LS·EV 묶음(자연·역사·레포츠·행사 계열, #21 정의). */
    SIGHT("관광지", Set.of("NA", "HS", "VE", "LS", "EV")),

    /** 숙박(AC). */
    STAY("숙박", Set.of("AC")),

    /** 체험(EX). */
    EXPERIENCE("체험", Set.of("EX")),

    /** 맛집(FD). */
    FOOD("맛집", Set.of("FD"));

    private final String label;
    private final Set<String> lclsSystm1Codes;

    Category(String label, Set<String> lclsSystm1Codes) {
        this.label = label;
        this.lclsSystm1Codes = lclsSystm1Codes;
    }

    /** 필터칩에 노출하는 한글 라벨. */
    public String label() {
        return label;
    }

    /** 이 칩이 주어진 lclsSystm 대분류 코드를 포함하는가. {@code ALL} 은 항상 참. */
    public boolean includes(String lclsSystm1) {
        return lclsSystm1Codes.contains(lclsSystm1);
    }

    /**
     * lclsSystm 대분류 코드를 그 코드를 소유한 구체 칩으로 되돌린다(콘텐츠 categories 산출용). {@code ALL} 은 필터가 아니라 전체
     * 표지라 매핑 대상이 아니므로 제외한다. 미지의 코드(null 포함)는 어떤 칩에도 안 들어가 빈 결과.
     */
    public static Optional<Category> fromLclsSystm1(String lclsSystm1) {
        if (lclsSystm1 == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(category -> category != ALL)
                .filter(category -> category.includes(lclsSystm1))
                .findFirst();
    }

    /** TourAPI 필터에 실을 lclsSystm 대분류 코드들(불변). {@code ALL} 은 빈 집합(필터 없음). */
    public Set<String> lclsSystm1Codes() {
        return lclsSystm1Codes;
    }
}
