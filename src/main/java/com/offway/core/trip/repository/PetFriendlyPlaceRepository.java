package com.offway.core.trip.repository;

import com.offway.core.trip.domain.PetFriendlyPlace;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** 반려동반 장소 저장소 port(#566). 도메인·서비스는 이 인터페이스에만 의존한다. */
public interface PetFriendlyPlaceRepository {

    /**
     * 그 지역의 반려동반 장소 전부 — <b>코스 생성이 이것으로 표식을 붙인다</b>.
     *
     * <p>지역당 평균 일곱 곳쯤이라(442건 / 67지역) 상한을 두지 않는다. 코스 슬롯마다 조회하는 것이
     * 아니라 <b>지역당 한 번</b> 받아 콘텐츠 ID 집합으로 쓴다 — 슬롯마다 물으면 슬롯 수만큼 질의가 난다.
     */
    List<PetFriendlyPlace> findByRegionId(long regionId);

    long count();

    /**
     * 받은 것을 저장한다 — <b>같은 장소가 이미 있으면 최신 값으로 덮는다</b>.
     *
     * <p>자연키는 TourAPI {@code contentId} 다. 동반 조건은 시설이 고쳐 올리므로 갱신 대상이다.
     *
     * @return 새로 넣거나 고친 행 수
     */
    int upsertAll(Collection<PetFriendlyPlace> places);

    /**
     * 이번 회차에 안 온 장소를 지운다 — <b>온전히 받은 회차에서만</b> 부른다.
     *
     * <p>반려동반을 그만둔 장소는 다음 회차 목록에서 빠진다. upsert 만으로는 옛 행이 남아, <b>이제
     * 데려갈 수 없는 곳에 칩을 띄우게 된다</b> — 사용자가 반려동물을 데리고 갔다가 못 들어간다.
     *
     * @param fetchedAt 이번 회차의 조회 시각. 이보다 오래된 행을 지운다
     * @return 지운 행 수
     */
    int deleteFetchedBefore(LocalDateTime fetchedAt);
}
