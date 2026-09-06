package com.offway.core.trip.repository;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceCategory;
import com.offway.core.trip.domain.PlaceKind;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicensedPlaceJpaRepository extends JpaRepository<LicensedPlace, Long> {

    /** 적합도 순 상위 N — Pageable 로 상한을 건다. */
    List<LicensedPlace> findByRegionIdAndKindOrderByFitnessRankAscNameAsc(
            long regionId, PlaceKind kind, Pageable pageable);

    Page<LicensedPlace> findByRegionIdAndKind(long regionId, PlaceKind kind, Pageable pageable);

    Page<LicensedPlace> findByRegionIdAndKindAndCategory(
            long regionId, PlaceKind kind, PlaceCategory category, Pageable pageable);

    /**
     * 그 지역 전부 — 이름으로 좌표를 이어 붙일 때 쓴다(#186). 월 1회 배치만 탄다.
     *
     * <p><b>id 로 정렬한다.</b> 정규화한 이름이 겹칠 때({@code ○○식당} 이 한 지역에 둘) 먼저 온 것을
     * 남기는데, DB 가 순서를 보장하지 않으면 <b>회차마다 다른 좌표가 붙는다.</b> 그러면 같은 요청이
     * 다른 코스를 내고, 왜 달라졌는지 설명할 수 없다.
     */
    List<LicensedPlace> findByRegionIdOrderByIdAsc(long regionId);
}
