package com.offway.core.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.policy.domain.PolicyType;
import com.offway.core.policy.repository.PolicyJpaRepository;
import com.offway.core.policy.service.dto.PolicyCommand;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 어드민 둘이 <b>동시에</b> 저장할 때 같은 뱃지가 둘 뜨지 않는가(#391).
 *
 * <p>잠그지 않으면 읽기와 쓰기 사이에 창이 남는다. 두 트랜잭션이 모두 "중복 없음" 을 읽으면 같은
 * 분류의 검증 정책이 둘 저장되고, <b>앱에 글자까지 같은 뱃지가 두 개</b> 뜬다 — 뱃지 문구를
 * {@code PolicyType} 이 소유하기 때문에 어느 쪽이 맞는지 사용자가 가릴 방법이 없다.
 *
 * <p><b>클래스에 {@code @Transactional} 을 걸지 않는다.</b> 롤백으로 격리하면 두 스레드가 같은
 * 트랜잭션을 보게 되어 경합 자체가 안 만들어진다({@code AuthIntegrationTest} 의 동시 재발급이 같은
 * 이유로 그렇게 한다). 대신 테스트가 자기 데이터를 직접 치운다.
 */
@SpringBootTest
class PolicyBadgeRaceIntegrationTest {

    /** 시드가 안 쓰는 분류라야 다른 테스트·시드와 안 섞인다. */
    private static final PolicyType TYPE = PolicyType.RURAL;

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    @Autowired
    private PolicyAdminService policyAdminService;

    @Autowired
    private PolicyJpaRepository policyJpaRepository;

    private static PolicyCommand command(String name) {
        return new PolicyCommand(
                TYPE, name, "혜택", "전 국민", FROM, TO, null, "https://example.kr", true, LocalDate.of(2026, 9, 3));
    }

    /**
     * <b>이 테스트가 이 PR 의 전부다.</b> 둘이 동시에 들어와도 하나만 남아야 한다.
     *
     * <p>가장 위험한 경우를 만든다 — 그 분류의 <b>첫 정책</b> 둘이 동시에 들어오는 상황이다. 기존
     * 행이 없어 잠글 행도 없고, 그래서 gap 을 잠그지 않으면 둘 다 통과한다.
     */
    @Test
    void 같은_분류를_동시에_저장하면_하나만_남는다() throws Exception {
        int racers = 2;
        CyclicBarrier gate = new CyclicBarrier(racers);
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        List<Future<String>> results = new ArrayList<>();

        try {
            for (int i = 0; i < racers; i++) {
                String name = "동시저장 " + i;
                results.add(pool.submit(() -> {
                    gate.await();
                    try {
                        policyAdminService.create(command(name), UUID.randomUUID());
                        return "OK";
                    } catch (RuntimeException e) {
                        return e.getClass().getSimpleName();
                    }
                }));
            }

            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(20, TimeUnit.SECONDS));
            }

            long saved = policyJpaRepository.findByTypeAndVerifiedTrue(TYPE).stream()
                    .filter(policy -> policy.getName().startsWith("동시저장"))
                    .count();
            assertEquals(1, saved, "같은 분류의 검증 정책이 둘 저장됐다 — 앱에 같은 뱃지가 두 개 뜬다: " + outcomes);
            assertTrue(outcomes.contains("OK"), "둘 다 실패하면 어드민이 아무것도 못 만든다: " + outcomes);
        } finally {
            pool.shutdownNow();
            cleanUp();
        }
    }

    /** 롤백이 없으므로 직접 치운다 — 남기면 다음 실행이 이미 있는 정책과 겹쳐 실패한다. */
    private void cleanUp() {
        policyJpaRepository.findByTypeAndVerifiedTrue(TYPE).stream()
                .filter(policy -> policy.getName().startsWith("동시저장"))
                .forEach(policy -> policyJpaRepository.deleteById(policy.getId()));
    }
}
