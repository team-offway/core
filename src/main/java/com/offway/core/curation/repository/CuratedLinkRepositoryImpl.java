package com.offway.core.curation.repository;

import com.offway.core.curation.domain.CuratedLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
public class CuratedLinkRepositoryImpl implements CuratedLinkRepository {

    private final CuratedLinkJpaRepository curatedLinkJpaRepository;

    public CuratedLinkRepositoryImpl(CuratedLinkJpaRepository curatedLinkJpaRepository) {
        this.curatedLinkJpaRepository = curatedLinkJpaRepository;
    }

    @Override
    public List<CuratedLink> findAllPublished() {
        // 정렬 순서가 같은 display_order 에서 갈리지 않게 id 를 tie-breaker 로 둔다 —
        // 없으면 같은 목록이 요청마다 다른 순서로 내려가 앱이 스크롤 위치를 잃는다.
        return curatedLinkJpaRepository.findByPublishedTrueOrderByDisplayOrderAscIdAsc();
    }

    @Override
    public Page<CuratedLink> findAll(Pageable pageable) {
        return curatedLinkJpaRepository.findAllByOrderByDisplayOrderAscIdAsc(pageable);
    }

    @Override
    public Optional<CuratedLink> findById(long id) {
        return curatedLinkJpaRepository.findById(id);
    }

    @Override
    public CuratedLink save(CuratedLink link) {
        return curatedLinkJpaRepository.save(link);
    }

    @Override
    public void deleteById(long id) {
        curatedLinkJpaRepository.deleteById(id);
    }
}
