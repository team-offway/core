package com.offway.core.trip.repository;

import com.offway.core.trip.domain.FestivalPlace;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 축제 저장소 port(#433). 도메인·서비스는 이 인터페이스에만 의존한다. */
public interface FestivalPlaceRepository {

    /**
     * 그날 <b>실제로 열리는</b> 축제만 돌려준다.
     *
     * <p>필터를 저장소 경계에서 끝낸다. 호출부가 매번 걸러야 하면 한 군데만 빠뜨려도 끝난 축제가 코스에
     * 들어가고, 그건 사용자를 헛걸음시키는 종류의 오류다.
     *
     * <p><b>여행일을 모르면 부르지 않는다.</b> 날짜 없이 "그냥 축제" 를 후보로 넣을 자리가 없다 —
     * 언제 여는지 모르는 축제는 코스에 못 올린다.
     */
    List<FestivalPlace> findOpenOn(long regionId, LocalDate date, int limit);

    Optional<FestivalPlace> findById(long id);

    long count();

    /**
     * 받은 것을 저장한다 — <b>같은 회차가 이미 있으면 최신 값으로 덮는다</b>.
     *
     * <p>지자체가 날짜·장소를 고쳐 다시 올리는 일이 있어 덮어써야 한다. 자연키(지역·축제명·시작일)가
     * 같은 회차를 가리키므로 그 키로 맞춘다.
     *
     * @return 새로 넣거나 고친 행 수
     */
    int upsertAll(Collection<FestivalPlace> places);

    /**
     * 이번 회차에 안 온 축제를 지운다 — <b>온전히 받은 회차에서만</b> 부른다.
     *
     * <p>지자체가 취소된 축제를 목록에서 빼면 upsert 만으로는 옛 행이 남는다. 저장된 미래 기간에는
     * {@code isOpenOn} 이 계속 참이라, 열리지도 않는 축제를 코스에 넣게 된다.
     *
     * <p><b>받은 것의 목록이 아니라 시각으로 가른다.</b> 자연키가 세 칸(지역·이름·시작일)이라 목록으로
     * 넘기면 그것을 문자열로 합쳐야 하는데, 축제명에 어떤 구분자가 들어올지 우리가 정할 수 없다.
     * 이번 회차가 받은 것은 전부 {@code fetchedAt} 이 갱신되므로 그보다 오래된 행이 곧 사라진 축제다.
     *
     * @param fetchedAt 이번 회차의 조회 시각. 이보다 오래된 행을 지운다
     * @return 지운 행 수
     */
    int deleteFetchedBefore(LocalDateTime fetchedAt);
}
