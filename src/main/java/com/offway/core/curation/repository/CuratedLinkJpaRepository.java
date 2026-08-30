package com.offway.core.curation.repository;

import com.offway.core.curation.domain.CuratedLink;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface CuratedLinkJpaRepository extends JpaRepository<CuratedLink, Long> {

    List<CuratedLink> findByPublishedTrueOrderByDisplayOrderAscIdAsc();

    /** 어드민 목록 — 앱과 같은 정렬로 본다. 화면에서 보는 순서와 실제 노출 순서가 같아야 한다. */
    Page<CuratedLink> findAllByOrderByDisplayOrderAscIdAsc(Pageable pageable);

    /**
     * 지우고 <b>몇 행을 지웠는지</b> 돌려준다 — 있는지 확인하고 지우는 두 문장을 하나로 합친다.
     *
     * <p>{@code deleteById} 는 반환이 없어 "없어서 못 지웠다" 와 "지웠다" 가 구분되지 않는다. 확인과 삭제를
     * 나눠 두면 그 사이에 다른 어드민이 같은 항목을 지웠을 때 404 가 아니라 200 이 나간다.
     *
     * <p>벌크 삭제라 영속성 컨텍스트를 거치지 않는다. 이 표는 딸린 자식이 없어 cascade 를 잃을 것도 없다.
     *
     * <p><b>{@code clearAutomatically} 를 켜는 이유</b> — 안 켜면 지운 행이 1차 캐시에 남아, 같은
     * 트랜잭션에서 다시 읽었을 때 <b>지워진 것이 그대로 조회된다.</b> 운영 요청은 트랜잭션이 요청마다
     * 갈려 안 드러나지만, 그건 우연히 안 걸리는 것이지 맞는 상태가 아니다. 실제로 테스트가 먼저 잡았다.
     *
     * <p>{@code flushAutomatically} 도 함께 켠다. 앞선 변경이 아직 안 나간 채 DELETE 가 먼저 도는 순서를
     * 막는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CuratedLink link where link.id = :id")
    int deleteByIdReturningCount(@Param("id") long id);
}
