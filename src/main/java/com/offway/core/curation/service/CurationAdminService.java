package com.offway.core.curation.service;

import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.curation.domain.CurationException;
import com.offway.core.curation.repository.CuratedLinkRepository;
import com.offway.core.curation.service.dto.CuratedLinkCommand;
import com.offway.core.user.service.AdminAccountService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 백오피스의 큐레이션 링크 CRUD(#342).
 *
 * <p>앱 조회({@link CurationService})와 나눠 둔다. 두 경로는 <b>보는 대상이 다르다</b> — 앱은 게시되고 기간
 * 안인 것만, 어드민은 만들다 만 것과 지난 것까지 전부다. 한 서비스에 두면 "거르는가" 를 인자로 받게 되고,
 * 그 인자를 잘못 넘긴 하루만큼 미공개 항목이 앱에 나간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurationAdminService {

    /**
     * 지운 행이 없다 — <b>없던 것을 지우라는 요청</b>이다.
     *
     * <p>숫자 {@code 0} 자체는 자명하지만, 이 비교가 답하는 질문("있었나")은 자명하지 않다. 이름을 붙여
     * 삭제 계약이 코드에 남게 한다.
     */
    private static final int NO_ROWS_DELETED = 0;

    private final CuratedLinkRepository curatedLinkRepository;
    private final AdminAccountService adminAccountService;

    @Transactional(readOnly = true)
    public Page<CuratedLink> list(Pageable pageable) {
        return curatedLinkRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public CuratedLink get(long id) {
        return curatedLinkRepository.findById(id).orElseThrow(CurationException::linkNotFound);
    }

    /**
     * 새 링크. 값 검증은 도메인 생성자가 한다 — 여기서 미리 걸러 두면 규칙이 두 곳이 된다.
     *
     * <p>감사 흔적을 <b>생성에도</b> 남긴다. "누가 만들었나" 를 따로 두지 않는 이유는, 만든 뒤 아무도 안
     * 고쳤다면 마지막으로 손댄 사람이 곧 만든 사람이기 때문이다.
     */
    @Transactional
    public CuratedLink create(CuratedLinkCommand command, UUID adminUserId) {
        String label = labelOf(adminUserId);
        CuratedLink saved = curatedLinkRepository.save(command.toCuratedLink(label));
        log.info("큐레이션 링크 생성 id={} 게시={} by={}", saved.getId(), saved.isPublished(), label);
        return saved;
    }

    /**
     * 전체 교체. 부분 갱신을 하지 않는 이유는 도메인 {@code update} 주석에 있다.
     *
     * <p>불러온 엔티티를 고치고 끝낸다 — 트랜잭션 안이라 변경 감지가 반영한다. {@code save} 를 다시 부르면
     * 같은 일이 두 번 도는 것처럼 읽혀 오해를 만든다.
     */
    @Transactional
    public CuratedLink update(long id, CuratedLinkCommand command, UUID adminUserId) {
        CuratedLink link = curatedLinkRepository.findById(id).orElseThrow(CurationException::linkNotFound);
        String label = labelOf(adminUserId);
        command.applyTo(link, label);
        log.info("큐레이션 링크 수정 id={} 게시={} by={}", id, link.isPublished(), label);
        return link;
    }

    /**
     * 삭제 — 없으면 404 다.
     *
     * <p>없는 것을 지우라는 요청을 200 으로 넘기지 않는다. 어드민 화면은 목록을 들고 있어서, 다른 탭에서
     * 이미 지운 항목을 누르면 여기 닿는다. 조용히 성공시키면 화면이 낡은 목록을 그대로 믿는다.
     *
     * <p><b>확인과 삭제를 한 문장으로 한다.</b> 미리 조회해 확인하면 그 사이에 다른 어드민이 같은 항목을
     * 지웠을 때 404 여야 할 요청이 200 으로 나간다 — 어드민이 둘이면 실제로 겹치는 순간이 있다. 지운 행
     * 수가 곧 답이다.
     */
    @Transactional
    public void delete(long id, UUID adminUserId) {
        if (curatedLinkRepository.deleteById(id) == NO_ROWS_DELETED) {
            throw CurationException.linkNotFound();
        }
        log.info("큐레이션 링크 삭제 id={} by={}", id, labelOf(adminUserId));
    }

    /**
     * 감사 흔적에 적을 이름.
     *
     * <p>화이트리스트에 이름이 없을 수 있다 — 토큰은 유효한데 그 사이 화이트리스트에서 빠진 경우다. 그때는
     * <b>쓰기를 막지 않고</b> 이름 없이 적는다. 권한 판정은 이미 끝났고, 여기서 다시 막으면 access TTL 동안
     * 요청이 500 처럼 보이는 403 을 받는다.
     */
    private String labelOf(UUID adminUserId) {
        return adminAccountService.labelOf(adminUserId).orElse(null);
    }
}
