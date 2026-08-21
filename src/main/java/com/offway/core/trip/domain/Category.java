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

    /**
     * 관광지 — 자연(NA)·역사문화(HS)·탈것(VE)·행사(EV)·쇼핑(SH).
     *
     * <p><b>쇼핑을 여기 넣는다</b>(#304). 칩은 넷으로 고정이라 쇼핑에 자리를 줄 수 없는데, 그렇다고 빼면
     * 저장조차 안 돼 <b>전체 탭에서도 사라진다.</b> 실측(2026-08-21, 3개 지역)에서 쇼핑은 지역마다 1~4건이
     * 있었고 <b>사진 보유율 100%</b> 였다 — 인구감소지역의 쇼핑은 대개 전통시장·특산품점이라 볼거리에 가깝다.
     */
    SIGHT("관광지", Set.of("NA", "HS", "VE", "EV", "SH")),

    /** 숙박(AC). */
    STAY("숙박", Set.of("AC")),

    /**
     * 체험 — 체험(EX)·레포츠(LS).
     *
     * <p><b>레포츠를 여기로 옮겼다</b>(#304). 예전에는 관광지에 묶여 있었는데, 그러면 <b>체험 칩이 사실상
     * 비었다</b> — 실측에서 순수 EX 는 부산 동구 1건·의성군 1건뿐이었다. 눌러도 한 개짜리 목록이 뜨는 칩은
     * 없는 것만 못하다.
     *
     * <p>레포츠를 옮기면 그 자리가 채워진다(정선군 8 → 17건). 뜻으로도 맞는다 — 둘 다 <b>보는 것이 아니라
     * 하는 것</b>이고, 래프팅·스키는 사용자가 "체험" 으로 찾는다.
     */
    EXPERIENCE("체험", Set.of("EX", "LS")),

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
