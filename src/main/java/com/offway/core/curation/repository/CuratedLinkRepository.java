package com.offway.core.curation.repository;

import com.offway.core.curation.domain.CuratedLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 도메인이 의존하는 port. 구현은 {@link CuratedLinkRepositoryImpl}. */
public interface CuratedLinkRepository {

    /**
     * 게시된 링크 전부를 정렬 순으로.
     *
     * <p><b>면·기간으로 DB 에서 거르지 않는다.</b> {@code surfaces} 는 쉼표로 이은 한 칸이라 SQL 로 거르려면
     * {@code LIKE '%HOME%'} 이 되는데, 인덱스를 못 타는 데다 앞으로 상수명이 서로의 부분문자열이 되면
     * 조용히 틀린다. 게시된 행이 수십 건 규모라 전부 읽어 도메인이 판정하는 편이 싸고 정확하다.
     */
    List<CuratedLink> findAllPublished();

    /**
     * 어드민 목록 — <b>게시 여부·기간과 무관하게 전부</b>(#342).
     *
     * <p>앱 조회({@link #findAllPublished})와 달리 거르지 않는다. 만들다 만 것과 기간이 지난 것을 못 보면
     * 어드민이 고칠 수가 없다.
     */
    Page<CuratedLink> findAll(Pageable pageable);

    Optional<CuratedLink> findById(long id);

    CuratedLink save(CuratedLink link);

    /** 없으면 아무것도 하지 않는다 — 있는지 확인하는 것은 서비스의 몫이다. */
    void deleteById(long id);
}
