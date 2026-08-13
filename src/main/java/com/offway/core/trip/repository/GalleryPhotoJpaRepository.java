package com.offway.core.trip.repository;

import com.offway.core.trip.domain.GalleryPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface GalleryPhotoJpaRepository extends JpaRepository<GalleryPhoto, Long> {

    /**
     * 정렬을 명시한다 — 대표 사진은 <b>동점일 때 목록 순서</b>로 갈린다.
     *
     * <p>정렬이 없으면 DB 가 돌려주는 순서에 결과가 묶여, 같은 데이터로도 배포·플랜에 따라 카드 사진이
     * 바뀔 수 있다. 테스트도 삽입 순서에 기대게 된다.
     */
    List<GalleryPhoto> findByRegionIdOrderByGalContentIdAsc(Long regionId);

    List<GalleryPhoto> findByRegionIdInOrderByRegionIdAscGalContentIdAsc(List<Long> regionIds);

    @Query("select count(p) from GalleryPhoto p where p.regionId is not null")
    long countWithRegion();
}
