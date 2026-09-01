package com.offway.core.policy.service;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyException;
import com.offway.core.policy.repository.PolicyRepository;
import com.offway.core.policy.service.dto.PolicyCommand;
import com.offway.core.user.service.AdminAccountService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 백오피스의 정책 CRUD(#344).
 *
 * <p>앱 조회({@link PolicyService})와 나눠 둔다. {@code CurationAdminService} 와 같은 이유다 — 두 경로는
 * <b>보는 대상이 다르다.</b> 앱은 검증되고 기간 안인 것만, 어드민은 미검증과 지난 것까지 전부다. 한
 * 서비스에 두면 "거르는가" 를 인자로 받게 되고, 그 인자를 잘못 넘긴 하루만큼 미검증 정책이 앱에 나간다.
 *
 * <h2>여기가 seed SQL 을 대신한다</h2>
 *
 * <p>{@code R__seed_policies.sql} 이 하던 일이 이 클래스로 옮겨 왔다. 그 파일은 <b>전량 삭제 후
 * 재적재</b>였는데, 어드민이 값을 고칠 수 있게 된 순간 그것이 편집분을 통째로 날리는 폭탄이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyAdminService {

    /**
     * 지운 행이 없다 — <b>없던 것을 지우라는 요청</b>이다.
     *
     * <p>숫자 {@code 0} 자체는 자명하지만, 이 비교가 답하는 질문("있었나")은 자명하지 않다. 이름을 붙여
     * 삭제 계약이 코드에 남게 한다.
     */
    private static final int NO_ROWS_DELETED = 0;

    private final PolicyRepository policyRepository;
    private final AdminAccountService adminAccountService;

    @Transactional(readOnly = true)
    public Page<Policy> list(Pageable pageable) {
        return policyRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Policy get(long id) {
        return policyRepository.findById(id).orElseThrow(PolicyException::notFound);
    }

    @Transactional
    public Policy create(PolicyCommand command, UUID adminUserId) {
        requireNoOverlappingBadge(command, null);
        String label = labelOf(adminUserId);
        Policy saved = policyRepository.save(command.toPolicy(label));
        log.info("정책 생성 id={} 분류={} 검증={} by={}", saved.getId(), saved.getType(), saved.isVerified(), label);
        return saved;
    }

    /**
     * 전체 교체. 불러온 엔티티를 고치고 끝낸다 — 트랜잭션 안이라 변경 감지가 반영한다.
     */
    @Transactional
    public Policy update(long id, PolicyCommand command, UUID adminUserId) {
        Policy policy = policyRepository.findById(id).orElseThrow(PolicyException::notFound);
        requireNoOverlappingBadge(command, id);
        String label = labelOf(adminUserId);
        command.applyTo(policy, label);
        log.info("정책 수정 id={} 분류={} 검증={} by={}", id, policy.getType(), policy.isVerified(), label);
        return policy;
    }

    /**
     * 삭제 — 없으면 404 다. 확인과 삭제를 한 문장으로 해서, 어드민 둘이 같은 항목을 지울 때
     * 404 여야 할 요청이 200 으로 나가지 않게 한다.
     */
    @Transactional
    public void delete(long id, UUID adminUserId) {
        if (policyRepository.deleteById(id) == NO_ROWS_DELETED) {
            throw PolicyException.notFound();
        }
        log.info("정책 삭제 id={} by={}", id, labelOf(adminUserId));
    }

    /**
     * 같은 분류의 뱃지가 같은 기간에 둘 뜨는 것을 막는다(#344).
     *
     * <h2>왜 서버가 막나</h2>
     *
     * <p><b>뱃지 문구를 분류가 소유한다.</b> 같은 분류의 정책 둘이 함께 유효하면 앱에 <b>글자까지 같은
     * 뱃지가 두 개</b> 뜬다 — 어느 쪽이 맞는지 사용자가 가릴 방법이 없다.
     *
     * <p>seed SQL 시절에는 이 실수가 리뷰에서 걸렸다. 배포 없이 만들 수 있게 되면서 <b>새로 열린
     * 실패 경로</b>라, 그 자리를 서버가 대신 막는다.
     *
     * <h2>검증 안 된 것은 보지 않는다</h2>
     *
     * <p>{@code verified=false} 는 앱에 안 나가므로 겹칠 수 없다. 다음 시즌 정책을 <b>미리 만들어 두는
     * 것</b>이 정상 작업이라, 그것까지 막으면 준비를 못 하게 된다 — 켜는 순간에 걸리면 충분하다.
     *
     * @param excludedId 수정 중인 자기 자신. 생성이면 {@code null}
     */
    private void requireNoOverlappingBadge(PolicyCommand command, Long excludedId) {
        if (!command.verified()) {
            return;
        }
        boolean overlaps = policyRepository.findVerifiedByType(command.type()).stream()
                .filter(other -> !other.getId().equals(excludedId))
                .anyMatch(other -> other.periodOverlaps(command.periodStart(), command.periodEnd()));
        if (overlaps) {
            log.info("같은 분류가 같은 기간에 이미 노출된다 분류={}", command.type());
            throw PolicyException.duplicateActiveType();
        }
    }

    /**
     * 감사 흔적에 적을 이름.
     *
     * <p>화이트리스트에 이름이 없을 수 있다 — 토큰은 유효한데 그 사이 명단에서 빠진 경우다. 그때는
     * <b>쓰기를 막지 않고</b> 이름 없이 적는다. 권한 판정은 이미 끝났다.
     */
    private String labelOf(UUID adminUserId) {
        return adminAccountService.labelOf(adminUserId).orElse(null);
    }
}
