package com.offway.core.trip.domain;

import java.util.Arrays;
import java.util.Objects;
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
    SIGHT("관광지", Set.of("NA", "HS", "VE", "EV", "SH")) {
        @Override
        public Optional<String> subtitle(PoiIntro intro, String catchphrase) {
            return firstPresent(catchphrase, intro == null ? null : intro.fee());
        }
    },

    /** 숙박(AC). */
    STAY("숙박", Set.of("AC")) {

        /**
         * 객실 수와 입실 시각을 한 줄로 잇는다 — {@code 13실 · 15:00 입실}.
         *
         * <p>시안은 {@code 2인실 · ₩50,000대} 였는데 <b>TourAPI 에 요금 필드가 없다</b>(#305 실측). 객실
         * 정보(`detailInfo2`)에 있지만 장소마다 1콜을 더 쓰면서 요금이 있는 숙소는 5곳 중 1곳뿐이라
         * 값어치를 넘는다. 객실 수로 대체한다.
         */
        @Override
        public Optional<String> subtitle(PoiIntro intro, String catchphrase) {
            if (intro == null) {
                return Optional.empty();
            }
            String rooms = roomsOrNull(intro.roomCount());
            String checkIn = blankToNull(intro.checkIn());
            if (rooms != null && checkIn != null) {
                return Optional.of(rooms + JOINER + checkIn + CHECK_IN_SUFFIX);
            }
            return firstPresent(rooms, checkIn == null ? null : checkIn + CHECK_IN_SUFFIX);
        }
    },

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
    EXPERIENCE("체험", Set.of("EX", "LS")) {

        /**
         * 이용요금이 앞이고 없으면 캐치프레이즈다.
         *
         * <p><b>요금으로 짰다가 바꿨다.</b> 실측(2026-08-24)에서 체험 칩 37건 중 요금이 있는 것이
         * <b>0건</b>이었다 — 체험은 콘텐츠 타입 12(관광지)로 와서 요금 칸이 아예 없다. 첫 가지가
         * 죽어 있던 셈이다.
         *
         * <p>체험안내는 60% 채워지고 내용도 구체적이다 —
         * {@code 차조강정 체험 / 사과 향기청 체험 / 목공예 체험 등}. 요금은 뒤로 물리되 남겨 둔다:
         * 이 칩에 문화시설·레포츠가 섞여 오면 그때는 걸린다.
         *
         * <p>이 칩에 함께 묶인 레포츠(LS)는 상세 자체가 빈 응답으로 온다(20건 중 19건).
         * 사슬이 그 빈자리를 캐치프레이즈로 메운다.
         */
        @Override
        public Optional<String> subtitle(PoiIntro intro, String catchphrase) {
            if (intro == null) {
                return firstPresent(catchphrase);
            }
            return firstPresent(intro.experienceGuide(), intro.fee(), catchphrase);
        }
    },

    /** 맛집(FD). */
    FOOD("맛집", Set.of("FD")) {

        /**
         * 대표메뉴가 앞이고 없으면 영업시간이다.
         *
         * <p>시안은 {@code 1인 9,000원} 이었는데 <b>TourAPI 음식점에 가격 필드가 아예 없다</b>(#305 실측).
         * 대신 대표메뉴는 표본 30건 <b>전부</b> 채워져 있었다 — 빈 부제가 거의 안 생긴다.
         */
        @Override
        public Optional<String> subtitle(PoiIntro intro, String catchphrase) {
            if (intro == null) {
                return Optional.empty();
            }
            return firstPresent(intro.signatureMenu(), intro.useTime());
        }
    };

    /** 값을 잇는 가운뎃점. 시안이 {@code 2인실 · ₩50,000대} 로 이 문자를 쓴다. */
    private static final String JOINER = " · ";

    /** 입실 시각 뒤에 붙는 말. 시각만 있으면 무엇의 시각인지 화면에서 알 수 없다. */
    private static final String CHECK_IN_SUFFIX = " 입실";

    /** 객실 수 단위. 외부가 {@code 13} 으로만 줄 때 붙인다. */
    private static final String ROOM_UNIT = "실";

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

    /**
     * 홈 장소 카드의 <b>부제</b> — 장소명 아래 한 줄(#305).
     *
     * <p><b>카테고리마다 다른 필드에서 온다.</b> 맛집은 대표메뉴, 숙박은 객실 수, 체험은 이용요금이다.
     * 그 지식을 각 상수가 들고 있어 호출자는 타입 스위치 없이 칩에게 묻는다.
     *
     * <p><b>사슬의 첫 번째로 값이 있는 것을 쓴다.</b> 없으면 빈 값이고 앱이 그 줄을 접는다 —
     * <b>지어내지 않는다.</b> 실측상 관광지·맛집은 거의 다 차고, 숙박은 캠핑장이, 체험은 레포츠가 빈다.
     *
     * <p>문구 조립을 서버가 하는 이유는 값이 카테고리마다 달라서다. 앱이 조립하면 그 분기가 앱으로 옮겨간다.
     *
     * @param intro 저장된 보조정보. <b>없을 수 있다</b> — 배치가 아직 안 받았거나 외부가 빈 응답을 준 장소다
     * @param catchphrase 구석구석 한 줄 소개(#87). 없으면 {@code null}
     * @return 부제. 재료가 하나도 없으면 빈 값
     */
    public Optional<String> subtitle(PoiIntro intro, String catchphrase) {
        return Optional.empty();
    }

    /**
     * 저장된 이름을 칩으로 되돌린다 — <b>모르는 이름이면 빈 값</b>이다.
     *
     * <p>{@code valueOf} 를 직접 쓰지 않는 이유는 그것이 예외를 던지기 때문이다. 칩 이름은 DB 에 문자열로
     * 남아 있어(예: {@code region_poi.category}) 상수명을 바꾸거나 지우면 기존 행과 어긋나는데, 그때
     * 예외가 나면 그 행 하나 때문에 <b>배치가 통째로 죽는다.</b> 모르는 값은 건너뛰고 나머지를 잇는 편이 낫다.
     */
    public static Optional<Category> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(category -> category.name().equals(name)).findFirst();
    }

    /** 사슬 — 앞의 것부터 값이 있는 첫 번째. 공백만 있는 값은 없는 것으로 본다. */
    private static Optional<String> firstPresent(String... candidates) {
        return Arrays.stream(candidates).map(Category::blankToNull).filter(Objects::nonNull).findFirst();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * 객실 수에 단위를 맞춘다 — 외부가 {@code 13} 으로도 {@code 13실} 로도 준다.
     *
     * <p>둘을 그대로 내리면 카드마다 표기가 달라진다. 숫자만이면 단위를 붙이고, 이미 붙어 있으면 둔다.
     */
    private static String roomsOrNull(String roomCount) {
        String value = blankToNull(roomCount);
        if (value == null) {
            return null;
        }
        return value.chars().allMatch(Character::isDigit) ? value + ROOM_UNIT : value;
    }
}
