package com.offway.core.curation.repository;

import com.offway.core.curation.domain.CuratedLink;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface CuratedLinkJpaRepository extends JpaRepository<CuratedLink, Long> {

    List<CuratedLink> findByPublishedTrueOrderByDisplayOrderAscIdAsc();

    /** 어드민 목록 — 앱과 같은 정렬로 본다. 화면에서 보는 순서와 실제 노출 순서가 같아야 한다. */
    Page<CuratedLink> findAllByOrderByDisplayOrderAscIdAsc(Pageable pageable);
}
