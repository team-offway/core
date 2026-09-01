package com.offway.core.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.notification.Notifier;
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
     * 시드에 미검증 정책(디지털관광주민증)이 있으므로 방치 요약에는 걸리는 것이 있어야 한다.
     *
     * <p>이 단언이 있어야 "아무것도 안 보내서 통과" 하는 상태와 갈린다 — 위 두 테스트는 0건도 허용한다.
     */
    @Test
    void 미검증_정책은_방치_요약에_실린다() {
        notifier.clear();

        policyAlertService.send(PolicyAlertService.AlertKind.NEGLECT);

        assertEquals(1, notifier.sent().size(), "시드에 미검증 정책이 있어 한 통은 나가야 한다");
        assertTrue(notifier.sent().get(0).contains("미검증"), notifier.sent().get(0));
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
