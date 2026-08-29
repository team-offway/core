package com.offway.core.curation.service;

import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.curation.domain.Surface;
import com.offway.core.curation.repository.CuratedLinkRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 큐레이션 링크 조회(#341) — 다른 도메인은 <b>이 서비스를 통해서만</b> 링크를 얻는다.
 *
 * <p>홈·지역·코스·장소 네 응답이 각자 자기 면의 목록을 받아 간다.
 *
 * <h2>캐시를 붙이지 않는다</h2>
 *
 * <p>게시된 행이 수십 건이고 {@code (published, display_order)} 인덱스 조회라 1ms 안쪽이다. 그리고 어드민
 * 수정이 <b>즉시</b> 반영돼야 하는데 캐시가 그 앞을 막는다. 코스 상세처럼 호출이 잦은 면에서 응답시간이
 * 유의미하게 늘면 그때 붙이되, 어드민 쓰기에서 무효화하는 경로를 함께 둔다.
 */
@Service
@RequiredArgsConstructor
public class CurationService {

    /** 노출 기간은 사용자가 사는 시간대로 판정한다 — UTC 로 재면 자정 전후 하루가 어긋난다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CuratedLinkRepository curatedLinkRepository;

    /**
     * 오늘 그 면에 나갈 링크를 정렬 순으로.
     *
     * <p>게시 여부는 SQL 이, <b>면과 기간은 도메인이</b> 판정한다({@link CuratedLink#visibleOn}). 판정을
     * 한 곳에 모아 두면 어드민 미리보기(#342)가 같은 규칙을 그대로 쓴다.
     */
    @Transactional(readOnly = true)
    public List<CuratedLink> linksOn(Surface surface) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        return curatedLinkRepository.findAllPublished().stream()
                .filter(link -> link.visibleOn(surface, today))
                .toList();
    }
}
