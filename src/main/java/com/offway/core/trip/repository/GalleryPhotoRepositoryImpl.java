package com.offway.core.trip.repository;

import com.offway.core.trip.domain.GalleryPhoto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class GalleryPhotoRepositoryImpl implements GalleryPhotoRepository {

    private final GalleryPhotoJpaRepository jpaRepository;

    /**
     * 한 트랜잭션 안에서 비우고 새로 넣는다 — 중간 상태(사진이 통째로 빈 구간)가 조회에 보이지 않게.
     *
     * <p>외부 호출은 이 밖에서 이미 끝난 뒤다(영속성 규약).
     */
    @Override
    @Transactional
    public void replaceAll(List<GalleryPhoto> photos) {
        jpaRepository.deleteAllInBatch();
        jpaRepository.saveAll(photos);
    }

    @Override
    public List<GalleryPhoto> findByRegionId(Long regionId) {
        return jpaRepository.findByRegionId(regionId);
    }

    @Override
    public List<GalleryPhoto> findByRegionIds(List<Long> regionIds) {
        if (regionIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByRegionIdIn(regionIds);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public long countWithRegion() {
        return jpaRepository.countWithRegion();
    }
}
