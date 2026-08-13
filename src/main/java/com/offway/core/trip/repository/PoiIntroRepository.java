package com.offway.core.trip.repository;

import com.offway.core.trip.domain.OpeningHours;
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
     */
    List<ContentRef> findMissing(int limit);

    /** 받은 것을 넣는다. 같은 콘텐츠를 다시 받으면 덮어쓴다. */
    int upsertAll(Map<ContentRef, OpeningHours> hours, LocalDateTime fetchedAt);

    long count();

    /** 아직 안 받은 콘텐츠 한 건 — 타입이 있어야 detailIntro2 를 부를 수 있다. */
    record ContentRef(String contentId, int contentTypeId) {
    }
}
