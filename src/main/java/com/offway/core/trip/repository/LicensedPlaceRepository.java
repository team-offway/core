package com.offway.core.trip.repository;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceCategory;
import com.offway.core.trip.domain.PlaceKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 인허가 장소 풀 port(#144) — 도메인이 의존하는 인터페이스. */
public interface LicensedPlaceRepository {

    /** 이 지역의 해당 종류 후보 전부. 코스 생성이 여기서 부족분을 채운다. */
    List<LicensedPlace> findByRegionAndKind(long regionId, PlaceKind kind);

    /**
     * 지역의 장소를 종류별로 페이징 조회한다 — 목록 화면(숙소·맛집·카페·관광명소 탭)이 쓴다.
     *
     * @param category 분류로 좁힐 때. null 이면 그 종류 전체
     */
    Page<LicensedPlace> findPage(long regionId, PlaceKind kind, PlaceCategory category, Pageable pageable);

    /** 단건 조회 — 코스 응답에 나간 식별자로 상세를 되찾을 때 쓴다. */
    Optional<LicensedPlace> findById(long id);

    /** 적재 여부 판정용 — 비어 있을 때만 채운다(멱등). */
    long count();

    /**
     * 대량 적재. 16만 건을 건건이 저장하면 부팅이 분 단위로 늘어나므로 JDBC 배치로 넣는다.
     *
     * @return 실제로 삽입된 건수
     */
    int saveAll(List<LicensedPlace> places);
}
