package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionPoi;
import java.time.YearMonth;
import java.util.List;

/** 지역 장소 풀 영속 port(#304). 구현은 {@link RegionPoiRepositoryImpl}. */
public interface RegionPoiRepository {

    /**
     * 이 지역의 사진 있는 장소를 최대 {@code limit} 개.
     *
     * <p>상한을 받는 이유는 화면이 정한 수(매력 포인트 장소 최대 10개)를 넘겨 읽을 이유가 없어서다.
     */
    List<RegionPoi> findShowable(long regionId, int limit);

    /**
     * 여러 지역에서 <b>칩마다 앞의 몇 건</b>씩 — 홈 장소 카드가 쓴다(#305).
     *
     * <p><b>칩별로 고르는 이유.</b> 지역당 상위 N 으로 자르면 등록 수가 많은 칩이 자리를 다 차지해,
     * 사용자가 "숙박" 을 눌렀을 때 아무것도 없는 일이 생긴다. 칩이 뜻을 가지려면 칩마다 후보가 있어야 한다.
     *
     * <p>사진 없는 장소는 빠진다 — {@link #findShowable} 과 같은 규칙이다. 섞이면 가로 목록에 회색 판이 낀다.
     *
     * <p>순서는 {@code id} 다. 적재가 통째 교체라 그 순서가 외부가 준 순서이고,
     * {@code PoiIntroRepository.findMissingForCards} 가 같은 순서로 상세를 받아 둔다 —
     * <b>둘이 갈리면 받아 둔 것과 보여주는 것이 어긋난다.</b>
     */
    List<RegionPoi> findForCards(List<Long> regionIds, int perCategory);

    /** 그 달치가 이미 적재됐는가 — 갱신 배치가 외부를 부를지 가른다. */
    boolean hasFresh(long regionId, YearMonth baseYm);

    /** 이 지역의 장소를 통째로 갈아 끼운다. 지우고 넣는 것이 한 트랜잭션이어야 중간 상태가 안 보인다. */
    void replaceRegion(long regionId, List<RegionPoi> pois);
}
