package com.offway.core.curation.repository;

import com.offway.core.curation.domain.CuratedLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface CuratedLinkJpaRepository extends JpaRepository<CuratedLink, Long> {

    List<CuratedLink> findByPublishedTrueOrderByDisplayOrderAscIdAsc();
}
