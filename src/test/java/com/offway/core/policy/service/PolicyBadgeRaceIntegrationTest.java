package com.offway.core.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.policy.domain.PolicyException;
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

    /** 동시에 들어오는 어드민 수. 둘이면 한쪽이 다 끝난 뒤 다른 쪽이 시작해도 통과해 버린다. */
    private static final int RACERS = 8;

    /** 반복 횟수 — 스케줄링이 매번 겹치지는 않으므로 여러 번 돌려 확률을 올린다. */
    private static final int ROUNDS = 3;

    private static final String NAME_PREFIX = "동시저장";

    /**
     * 진 쪽이 받아도 되는 사유 둘.
     *
     * <p>{@code POLICY-004} 는 잠금을 잡고 보니 이미 겹쳤다는 뜻이고, {@code POLICY-005} 는 잠금 자체를
     * 못 잡았다는 뜻이다. 둘 다 <b>정상적으로 진 것</b>이다. 그 밖의 예외(교착·타임아웃)는 어드민에게
     * 500 으로 나가므로 실패로 본다.
     */
    private static final List<String> EXPECTED_LOSSES = List.of("POLICY-004", "POLICY-005");

    @Autowired
    private PolicyAdminService policyAdminService;

    @Autowired
    private PolicyJpaRepository policyJpaRepository;

    private static PolicyCommand command(String name) {
        return new PolicyCommand(
                TYPE, name, "혜택", "전 국민", FROM, TO, null, "https://example.kr", true, LocalDate.of(2026, 9, 3));
    }

    /**
     * <b>이 테스트가 이 PR 의 전부다.</b> 동시에 들어와도 하나만 남아야 한다.
     *
     * <p>가장 위험한 경우를 만든다 — 그 분류의 <b>첫 정책</b> 여럿이 동시에 들어오는 상황이다. 기존
     * 행이 없어 잠글 행도 없고, 그래서 gap 을 잠그지 않으면 여럿이 통과한다.
     *
     * <p><b>실패한 쪽의 사유까지 본다.</b> 예전에는 아무 {@link RuntimeException} 이나 "실패" 로
     * 셌는데, 그러면 교착·타임아웃으로 죽어도 통과한다 — 막으려던 것과 정반대 상태인데 초록이 뜬다.
     * 진 쪽은 전부 {@link #EXPECTED_LOSSES} 안의 사유여야 한다.
     *
     * <p>이 단언이 실제로 하나 잡았다 — 경쟁자를 여덟으로 올리니 한 명이 잠금을 못 잡아
     * {@code CannotAcquireLockException} 을 받았다. 어드민에게 <b>500</b> 이 나가던 자리라,
     * {@code POLICY-005} 로 바꿔 무슨 일이 있었는지 그대로 말하게 했다.
     *
     * <p><b>남은 한계.</b> 경합 지점을 코드로 고정하지는 못한다 — 한 트랜잭션을 "잠금 조회 뒤,
     * 저장 전" 에 세우려면 서비스에 테스트 전용 자리를 내야 하는데, 그건 내부 컴포넌트를 테스트가
     * 흔드는 것이라 테스트 규약과 어긋난다. 대신 <b>경쟁자를 {@value #RACERS} 로 늘리고
     * {@value #ROUNDS} 번 반복</b>해, 잠금이 없으면 통과하기 어렵게 만든다. 둘이었을 때는 한쪽이
     * 읽기·저장을 다 끝낸 뒤 다른 쪽이 시작하기만 해도 잠금 없이 통과할 수 있었다.
     */
    @Test
    void 같은_분류를_동시에_저장하면_하나만_남는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            raceOnce(round);
        }
    }

    private void raceOnce(int round) throws Exception {
        CyclicBarrier gate = new CyclicBarrier(RACERS);
        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        List<Future<String>> results = new ArrayList<>();

        try {
            for (int i = 0; i < RACERS; i++) {
                String name = NAME_PREFIX + round + "-" + i;
                results.add(pool.submit(() -> {
                    gate.await();
                    try {
                        policyAdminService.create(command(name), UUID.randomUUID());
                        return "OK";
                    } catch (PolicyException e) {
                        return e.errorCode().code();
                    } catch (RuntimeException e) {
                        // 교착·타임아웃 등 — 아래 단언이 잡아낸다. 사유를 남겨야 원인을 찾는다.
                        return e.getClass().getSimpleName();
                    }
                }));
            }

            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(20, TimeUnit.SECONDS));
            }

            long saved = policyJpaRepository.findByTypeAndVerifiedTrue(TYPE).stream()
                    .filter(policy -> policy.getName().startsWith(NAME_PREFIX))
                    .count();
            assertEquals(1, saved, "같은 분류의 검증 정책이 둘 이상 저장됐다 — 앱에 같은 뱃지가 여럿 뜬다: " + outcomes);

            assertEquals(1, outcomes.stream().filter("OK"::equals).count(),
                    "정확히 하나만 성공해야 한다 — 다 실패하면 어드민이 아무것도 못 만든다: " + outcomes);
            assertEquals(RACERS - 1L, outcomes.stream().filter(EXPECTED_LOSSES::contains).count(),
                    "진 쪽은 전부 " + EXPECTED_LOSSES + " 여야 한다. 그 밖의 예외는 어드민에게 500 으로 나간다: "
                            + outcomes);
        } finally {
            pool.shutdownNow();
            cleanUp();
        }
    }

    /** 롤백이 없으므로 직접 치운다 — 남기면 다음 실행이 이미 있는 정책과 겹쳐 실패한다. */
    private void cleanUp() {
        policyJpaRepository.findByTypeAndVerifiedTrue(TYPE).stream()
                .filter(policy -> policy.getName().startsWith(NAME_PREFIX))
                .forEach(policy -> policyJpaRepository.deleteById(policy.getId()));
    }
}
