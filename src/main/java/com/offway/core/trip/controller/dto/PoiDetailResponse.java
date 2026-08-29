package com.offway.core.trip.controller.dto;

import com.offway.core.common.logging.LogSummary;
import com.offway.core.curation.controller.dto.CuratedLinkResponse;
import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.trip.domain.PoiContentType;
import com.offway.core.trip.domain.PoiIntro;
import com.offway.core.trip.service.dto.PoiDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

/**
 * 장소 상세 응답 — API 계약. 코스 타임라인에서 장소를 누르면 보는 상세.
 *
 * <p><b>카테고리별 보조정보를 블록으로 나눠 내린다(#157).</b> 음식점의 대표메뉴와 숙소의 입실 시각을 한 자리에
 * 섞으면 필드 이름이 그 장소에 안 맞는 뜻으로 읽힌다. 카테고리마다 자기 블록을 갖고, <b>해당 없는 블록은
 * null</b> 이다 — 클라이언트는 자기가 그리는 카테고리의 블록만 보면 된다.
 *
 * <p>블록 안의 값이 null 인 것과 블록 자체가 null 인 것은 뜻이 다르다. 앞은 "이 장소는 그 값을 안 알려준다",
 * 뒤는 "이 카테고리가 아니다". 화면은 둘 다 그 줄을 지우면 된다.
 *
 * @param contentId 콘텐츠 ID
 * @param contentTypeId 콘텐츠 타입 코드
 * @param typeLabel 콘텐츠 타입 한글 라벨(관광지·음식점 등)
 * @param title 장소명
 * @param address 주소(없으면 null)
 * @param tel 전화(없으면 null)
 * @param lat 위도(없으면 null)
 * @param lng 경도(없으면 null)
 * @param imageUrl 대표 이미지(없으면 null)
 * @param overview 소개 문구(없으면 null)
 * @param sight 관광지 보조정보(관광지가 아니면 null)
 * @param culture 문화시설 보조정보(문화시설이 아니면 null)
 * @param leports 레포츠 보조정보(레포츠가 아니면 null)
 * @param food 음식점 보조정보(음식점이 아니면 null)
 * @param stay 숙박 보조정보(숙박이 아니면 null)
 * @param mapSearchUrl 지도 검색 링크(인허가·국가유산만. 관광 API 콘텐츠는 null)
 * @param benefit 장소 단위 혜택 문구(단정 가능한 것만. 없으면 null)
 * @param catchphrase 구석구석 캐치프레이즈(감성 한 줄, 없으면 null)
 * @param curatedLinks 외부 페이지로 나가는 창구(#341). 장소 상세에 켜진 것만, 정렬 순으로.
 *     없으면 빈 목록이다 — 위 블록들과 달리 null 이 아니다
 */
public record PoiDetailResponse(
        String contentId,
        Integer contentTypeId,
        @Schema(example = "관광지") String typeLabel,
        @Schema(example = "완도타워 전망대") String title,
        String address,
        String tel,
        Double lat,
        Double lng,
        String imageUrl,
        String overview,
        @Schema(nullable = true) Sight sight,
        @Schema(nullable = true) Culture culture,
        @Schema(nullable = true) Leports leports,
        @Schema(nullable = true) Food food,
        @Schema(nullable = true) Stay stay,
        @Schema(
                description = "지도 검색 링크. 우리가 영업시간·사진을 못 주는 장소(인허가·국가유산)에만 실린다. "
                        + "인허가 데이터는 \"영업 허가를 받았다\" 는 사실이 목적이라 영업시간이 애초에 없고 "
                        + "다른 공식 API 로도 못 얻는다. 낡은 영업시간을 우리가 보여주는 것보다 지도로 넘기는 편이 낫다.",
                example = "https://map.naver.com/p/search/%EC%9D%98%EC%84%B1%EA%B5%B0+%EC%98%AC%EC%9D%B8%EB%AA%A8%ED%85%94",
                nullable = true) String mapSearchUrl,
        @Schema(description = "이 장소에서 쓸 수 있는 혜택(#172). 단정할 수 있는 것만 — 지금은 숙박세일페스타(숙소)뿐이다. "
                + "관광 API 콘텐츠는 지역을 알 수 없어 비어 있다.",
                example = "숙박 할인", nullable = true) String benefit,
        @Schema(example = "바다 위에 뜬 낭만, 완도의 랜드마크", nullable = true) String catchphrase,
        List<CuratedLinkResponse> curatedLinks)
        implements LogSummary {

    /** 예: {@code 완도타워 전망대(관광지)}. id 는 경로에 이미 있으므로 되풀이하지 않는다. */
    private static final String LOG_FORMAT = "%s(%s)";

    /**
     * 관광지(12) — 우리 89곳 실측 이용시간 100% · 휴무일 100% · 주차 87%.
     *
     * <p>관광 API 가 관광지에는 요금 필드를 주지 않는다. 그래서 블록에도 두지 않는다 — 늘 null 인 필드를
     * 계약에 넣으면 클라이언트가 언젠가 채워질 것으로 읽는다.
     */
    public record Sight(
            @Schema(example = "상시 개방", nullable = true) String useTime,
            @Schema(example = "연중무휴", nullable = true) String restDate,
            @Schema(example = "가능", nullable = true) String parking) {
    }

    /** 문화시설(14) — 이용시간 100% · 휴무일 91% · 요금 87% · 주차 87%. */
    public record Culture(
            @Schema(example = "평일 09:00~17:00", nullable = true) String useTime,
            @Schema(example = "매주 월요일", nullable = true) String restDate,
            @Schema(example = "무료", nullable = true) String fee,
            @Schema(example = "가능", nullable = true) String parking) {
    }

    /**
     * 레포츠(28) — 표본 24건이 전부 비었다(캠핑장·낚시터 위주).
     *
     * <p>그래도 블록을 둔다. 곡성 기차마을 레일바이크처럼 값이 오는 것이 있어 편차가 클 뿐, 필드가 없는 것은
     * 아니다. 안 오면 블록 안이 전부 null 이고 화면은 그 줄들을 지운다.
     */
    public record Leports(
            @Schema(example = "09:00~17:20", nullable = true) String useTime,
            @Schema(example = "연중무휴", nullable = true) String restDate,
            @Schema(example = "성인 10,000원", nullable = true) String fee,
            @Schema(example = "가능", nullable = true) String parking) {
    }

    /** 음식점(39) — 영업시간 100% · 휴무일 100% · 대표메뉴 95% · 취급메뉴 95%. */
    public record Food(
            @Schema(example = "11:30~21:00 (마지막 주문 20:00)", nullable = true) String openTime,
            @Schema(example = "매주 월요일", nullable = true) String restDate,
            @Schema(example = "갈치조림정식", nullable = true) String signatureMenu,
            @Schema(example = "토시살 / 꽃살 / 갈비탕", nullable = true) String menus) {
    }

    /** 숙박(32) — 입실·퇴실 100% · 객실수 100% · 예약안내 33%. */
    public record Stay(
            @Schema(example = "15:00", nullable = true) String checkIn,
            @Schema(example = "11:00", nullable = true) String checkOut,
            @Schema(description = "객실 수. `5` 처럼 숫자만 오기도 하고 `5실` 로 오기도 한다",
                    example = "11", nullable = true) String roomCount,
            @Schema(example = "전화(010-3809-6277)", nullable = true) String reservation) {
    }

    @Override
    public String logSummary() {
        return LOG_FORMAT.formatted(title, typeLabel);
    }

    public static PoiDetailResponse from(PoiDetail poi, List<CuratedLink> curatedLinks) {
        PoiIntro intro = poi.intro();
        PoiContentType type = PoiContentType.from(poi.contentTypeId()).orElse(null);
        return new PoiDetailResponse(
                poi.contentId(),
                poi.contentTypeId(),
                poi.typeLabel(),
                poi.title(),
                poi.address(),
                poi.tel(),
                poi.lat(),
                poi.lng(),
                poi.imageUrl(),
                poi.overview(),
                blockFor(type, PoiContentType.TOURIST_SPOT, intro,
                        it -> new Sight(it.useTime(), it.restDate(), it.parking())),
                blockFor(type, PoiContentType.CULTURE, intro,
                        it -> new Culture(it.useTime(), it.restDate(), it.fee(), it.parking())),
                blockFor(type, PoiContentType.LEPORTS, intro,
                        it -> new Leports(it.useTime(), it.restDate(), it.fee(), it.parking())),
                blockFor(type, PoiContentType.RESTAURANT, intro,
                        it -> new Food(it.useTime(), it.restDate(), it.signatureMenu(), it.menus())),
                blockFor(type, PoiContentType.STAY, intro,
                        it -> new Stay(it.checkIn(), it.checkOut(), it.roomCount(), it.reservation())),
                poi.mapSearchUrl(),
                poi.benefit(),
                poi.catchphrase(),
                CuratedLinkResponse.of(curatedLinks));
    }

    /** 그 카테고리일 때만 블록을 만든다. 보조정보 자체가 없으면(우리 DB 출처) 어떤 블록도 안 만든다. */
    private static <T> T blockFor(
            PoiContentType actual, PoiContentType wanted, PoiIntro intro, Function<PoiIntro, T> build) {
        return actual == wanted && intro != null ? build.apply(intro) : null;
    }
}
