package com.offway.core.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.notification.Notifier;
import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.policy.repository.PolicyJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * 저장소부터 발송까지의 배선(#220).
 *
 * <p>무엇을 언제 알릴지는 도메인 단위 테스트가 망라한다({@code PolicyStalenessTest}). 여기서 보는 것은
 * <b>실제 정책 데이터를 읽어 알림 경로까지 이어지는가</b> 하나다.
 *
 * <p>외부 경계인 디스코드만 stub 으로 격리한다. 이 클래스가 도는 것 자체가 절반의 확인이기도 하다 —
 * 이 빈에 {@code @Profile("prod")} 를 걸었다면 테스트 컨텍스트에 아예 없어 여기까지 못 온다.
 */
@SpringBootTest
@Import(PolicyAlertIntegrationTest.StubNotifierConfig.class)
class PolicyAlertIntegrationTest {

    @Autowired
    private PolicyAlertService policyAlertService;

    @Autowired
    private StubNotifier notifier;

    /**
     * Spring Data 쪽을 직접 쓴다 — port 의 {@code deleteById} 는 {@code @Modifying} 쿼리라
     * 트랜잭션을 요구하고, 이 클래스는 {@code @Transactional} 이 아니다(붙이면 커밋이 없어
     * 알림 경로가 헛돈다).
     */
    @Autowired
    private PolicyJpaRepository policyJpaRepository;

    /**
     * 지금 시드된 정책은 셋 다 기간이 2026-11-30 · 2026-08-31 처럼 정해진 날이라, 오늘이 예고일과
     * 겹칠 가능성이 거의 없다. 그래서 <b>대개 아무것도 안 보내야 하고</b>, 그것이 정상이다.
     *
     * <p>여기서 잠그는 것은 "안 보낸다" 가 아니라 <b>보내더라도 형식이 성립한다</b> 는 것이다 — 0건이면
     * 한 통도 안 나가고, 1건 이상이면 제목과 건수가 붙은 한 통만 나간다.
     */
    @Test
    void 종료_예고는_한_통으로_나가거나_아예_안_나간다() {
        notifier.clear();

        policyAlertService.send(PolicyAlertService.AlertKind.EXPIRY);

        assertTrue(notifier.sent().size() <= 1, "여러 통으로 쪼개 보내면 그 자체가 소음이다");
        notifier.sent().forEach(message -> assertTrue(message.startsWith("⚠️ 정책 종료 예고"), message));
    }

    @Test
    void 방치_요약도_한_통으로_묶인다() {
        notifier.clear();

        policyAlertService.send(PolicyAlertService.AlertKind.NEGLECT);

        assertTrue(notifier.sent().size() <= 1);
        notifier.sent().forEach(message -> {
            assertTrue(message.startsWith("⚠️ 손봐야 할 정책"), message);
            // 받는 사람이 무엇을 해야 하는지 알아야 한다 — 정책명만 오면 출처를 다시 찾아야 한다.
            assertTrue(message.contains("확인 "), message);
        });
    }

    /**
     * 방치 정책이 있으면 한 통은 나간다 — <b>이 단언이 있어야 "아무것도 안 보내서 통과" 와 갈린다</b>
     * (위 두 테스트는 0건도 허용한다).
     *
     * <p><b>방치 정책을 이 테스트가 직접 만든다.</b> 예전에는 시드에 걸리는 것이 있다는 데 기댔는데,
     * 그것이 두 번 무너졌다 — 먼저 디지털관광주민증이 검증 완료로 올라갔고(#498), 이어서 기간이 지나
     * 걸려 있던 숙박세일페스타가 가을분으로 옮겨졌다(#612). <b>남는 것이 0 이 되면 단언이 깨진다.</b>
     *
     * <p>시드는 고쳐야 할 대상이고, 테스트는 그 상태에 기대선 안 된다. 미검증 정책 하나를 만들어
     * {@code UNVERIFIED} 를 확실히 하나 만든 뒤 본문에서 지운다 — 이 클래스는 {@code @Transactional}
     * 이 아니라 롤백이 없다.
     */
    @Test
    void 방치_정책이_있으면_요약이_한_통_나간다() {
        Policy neglected = policyJpaRepository.save(Policy.builder()
                .type(PolicyType.WORKER_VACATION)
                .name("테스트용 미검증 정책")
                .benefitDetail("이 테스트가 만들고 지운다")
                .verified(false)
                .build());
        try {
            notifier.clear();

            policyAlertService.send(PolicyAlertService.AlertKind.NEGLECT);

            assertEquals(1, notifier.sent().size(), "손봐야 할 정책이 있으면 한 통은 나가야 한다");
            assertTrue(notifier.sent().get(0).contains("손봐야 할 정책"), notifier.sent().get(0));
        } finally {
            policyJpaRepository.deleteById(neglected.getId());
        }
    }

    /** 외부(디스코드) 경계 stub — 무엇을 보냈는지 기억한다. */
    static class StubNotifier implements Notifier {

        private final List<String> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(String message) {
            sent.add(message);
        }

        List<String> sent() {
            return new ArrayList<>(sent);
        }

        void clear() {
            sent.clear();
        }
    }

    @TestConfiguration
    static class StubNotifierConfig {

        @Bean
        @Primary
        StubNotifier stubNotifier() {
            return new StubNotifier();
        }
    }
}
