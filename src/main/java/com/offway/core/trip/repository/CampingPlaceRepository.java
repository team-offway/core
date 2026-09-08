package com.offway.core.trip.repository;

import com.offway.core.trip.domain.CampingPlace;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 야영장 저장소 port(#510). 도메인·서비스는 이 인터페이스에만 의존한다. */
public interface CampingPlaceRepository {

    /**
     * 숙박 후보로 쓸 야영장 — <b>사진 있는 것을 먼저</b> 돌려준다.
     *
     * <p>정렬을 저장소 경계에서 끝낸다. 이 표를 들여온 이유가 사진이라, 모자란 만큼만 잘려 쓰일 때
     * 사진 없는 쪽이 앞에 오면 얻는 것이 없다. 호출부가 매번 정렬하면 한 군데만 빠뜨려도 그렇게 된다.
     *
     * <p>사진이 없는 것도 뒤에 남긴다 — 이름·주소·좌표는 있어 동선에는 올릴 수 있고, 야영장이 한두
     * 곳뿐인 지역에서는 그것도 후보다.
     */
    List<CampingPlace> findCandidates(long regionId, int limit);

    Optional<CampingPlace> findById(long id);

    long count();

    /**
     * 받은 것을 저장한다 — <b>같은 야영장이 이미 있으면 최신 값으로 덮는다</b>.
     *
     * <p>자연키는 고캠핑이 주는 {@code externalId} 다. 이름·주소는 지자체가 고쳐 올리지만 그 값은 유지된다.
     *
     * @return 새로 넣거나 고친 행 수
     */
    int upsertAll(Collection<CampingPlace> places);

    /**
     * 이번 회차에 안 온 야영장을 지운다 — <b>온전히 받은 회차에서만</b> 부른다.
     *
     * <p>폐업하거나 휴장으로 바뀐 야영장은 다음 회차 목록에서 빠진다. upsert 만으로는 옛 행이 남아,
     * 문 닫은 곳을 코스에 넣게 된다.
     *
     * <p>이번 회차가 받은 것은 전부 {@code fetchedAt} 이 갱신되므로 그보다 오래된 행이 곧 사라진 것이다.
     *
     * @param fetchedAt 이번 회차의 조회 시각. 이보다 오래된 행을 지운다
     * @return 지운 행 수
     */
    int deleteFetchedBefore(LocalDateTime fetchedAt);
}
