package com.offway.core.trip.repository;

import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.OpeningHours;
import com.offway.core.trip.domain.PoiIntro;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 장소 운영시간·휴무일 저장소 port(#157). 구현은 {@link PoiIntroRepositoryImpl}.
 *
 * <p>엔티티를 두지 않는다 — 행이 콘텐츠 하나당 하나이고 도메인 규칙이 없다. 조회는 "이 코스의 콘텐츠들"
 * 처럼 항상 묶음이라 JPA 로 얻을 것도 없다.
 *
 * <p><b>그럼에도 port 를 두는 이유</b> — 읽는 쪽이 다른 도메인이다({@code itinerary} 의
 * {@code OpeningHoursProvider}). 코스 응답이 SQL 이 아니라 계약에 기대게 하려면 경계에 인터페이스가
 * 있어야 한다. 같은 도메인 안에서만 쓰이는 {@code HeritagePoolSourceRepository}·
 * {@code ExternalApiCallRepository} 가 인터페이스 없이 사는 것과 갈리는 지점이다.
 */
public interface PoiIntroRepository {

    /** 콘텐츠 id 로 운영시간을 찾는다. 없는 것은 키가 없다 — 호출자가 "아직 안 받았다" 로 읽는다. */
    Map<String, OpeningHours> findByContentIds(List<String> contentIds);

    /**
     * 아직 안 받은 콘텐츠를 코스 슬롯에서 찾는다 — <b>슬롯 테이블이 곧 일감 목록이다</b>.
     *
     * <p>별도 큐를 두지 않는다. 우리가 운영시간을 알아야 하는 콘텐츠는 정확히 "코스에 실제로 쓰인 것" 이고,
     * 그건 이미 슬롯에 남아 있다. 큐를 만들면 슬롯과 두 곳이 되어 어긋난다.
     *
     * <p>타입이 없는 슬롯(이 기능 이전 코스·우리 DB 출처)은 제외한다 — 타입 없이는 detailIntro2 를 못 부른다.
     *
     * @param emptyRetryBefore 이 시각보다 오래된 <b>빈 행</b>은 다시 일감이 된다. 빈 응답은 실패와 결과가
     *     같으므로 영구 캐시로 굳히지 않는다(#157). 재시도 간격은 호출자(배치)가 정한다
     */
    List<ContentRef> findMissing(int limit, LocalDateTime emptyRetryBefore);

    /**
     * 콘텐츠 id 로 <b>보조정보 전체</b>를 찾는다 — 홈 카드 부제가 쓴다(#305).
     *
     * <p>{@link #findByContentIds} 와 나눠 둔 이유는 읽는 폭이다. 코스 상세는 운영시간 둘만 쓰므로 그
     * 두 칸만 읽고, 부제는 카테고리마다 다른 칸을 보므로 전부 읽는다.
     */
    Map<String, PoiIntro> findIntros(List<String> contentIds);

    /**
     * 홈 카드에 <b>실제로 내릴 장소</b>만 일감으로 고른다(#305) — 지역·칩별 상위 몇 건.
     *
     * <p><b>전량을 받지 않는다.</b> 지역당 등록 건수가 중앙값 57건이라 89곳이면 5,000콜이고, 일일 한도
     * 1,000 으로는 닷새치다. 홈이 보여주는 것만 채우면 {@code 89 × 칩 4 × perCategory} 로 끝난다.
     *
     * <p><b>사진 없는 장소는 제외한다.</b> 카드가 회색 판이 되므로 어차피 안 내린다 — 부제를 받아 둘
     * 이유가 없다(#304 가 같은 규칙을 쓴다).
     *
     * <p><b>상세를 못 받는 타입도 제외한다.</b> 실측(2026-08-24)에서 타입 28(레포츠·캠핑장)은 20건 중
     * 19건이 빈 응답이었다. 부르면 콜만 쓰고 아무것도 안 담긴다.
     *
     * @param perCategory 지역·칩마다 몇 건까지 — 이 값이 회차당 콜 수를 정한다
     * @param emptyRetryBefore 이 시각보다 오래된 <b>빈 행</b>은 다시 일감이 된다. 외부가 나중에 채우면
     *     그때 따라 채워지라고 두는 문이다
     */
    List<ContentRef> findMissingForCards(int limit, int perCategory, LocalDateTime emptyRetryBefore);

    /**
     * 받은 것을 넣는다. 같은 콘텐츠를 다시 받으면 덮어쓴다.
     *
     * <p><b>덮어쓰기라 나중에 채워진 값이 따라온다.</b> 외부가 처음엔 안 주던 대표메뉴를 나중에 채우면,
     * 재시도 회차에 그 값이 그대로 들어온다.
     */
    int upsertAll(Map<ContentRef, PoiIntro> intros, LocalDateTime fetchedAt);

    long count();

    /**
     * 아직 안 받은 콘텐츠 한 건 — 타입이 있어야 detailIntro2 를 부를 수 있다.
     *
     * @param category 이 장소가 걸린 칩. 홈 일감에만 채워진다 — 슬롯 기준 일감은 칩을 모른다
     */
    record ContentRef(String contentId, int contentTypeId, Category category) {

        /** 칩을 모르는 일감(슬롯 기준). */
        public static ContentRef of(String contentId, int contentTypeId) {
            return new ContentRef(contentId, contentTypeId, null);
        }
    }
}
