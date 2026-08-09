package com.offway.core.trip.repository;

import com.offway.core.trip.domain.GalleryPhoto;
import java.util.List;

/** 관광사진 갤러리 영속 port(#196). 구현은 {@link GalleryPhotoRepositoryImpl}. */
public interface GalleryPhotoRepository {

    /**
     * 전량을 <b>교체</b>한다.
     *
     * <p>부분 갱신하지 않는다 — 원본에서 내려간 사진이 남으면 죽은 URL 이 대표 사진으로 걸린다. 6,118건이라
     * 통째로 갈아끼워도 부담이 없다.
     */
    void replaceAll(List<GalleryPhoto> photos);

    /** 지역이 붙은 사진들 — 대표 사진 후보. */
    List<GalleryPhoto> findByRegionId(Long regionId);

    /** 여러 지역을 한 번에 — 목록 화면이 지역마다 묻지 않게(N+1 방지). */
    List<GalleryPhoto> findByRegionIds(List<Long> regionIds);

    List<GalleryPhoto> findAll();

    /** 저장된 사진 수 — 적재가 돌았는지 판단하는 데 쓴다. */
    long count();

    /** 지역이 붙은 사진 수 — 정규화가 얼마나 매칭했는지. */
    long countWithRegion();

    /** 정규화 결과를 반영한다. */
    void saveAll(List<GalleryPhoto> photos);
}
