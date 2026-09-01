package com.offway.core.policy.service;

import com.offway.core.common.notification.Notifier;
import com.offway.core.policy.domain.PolicyAlert;
import com.offway.core.policy.repository.PolicyRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 낡아 가는 정책을 팀에게 알린다(#220).
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>정책은 수동 seed 라 <b>낡는 것이 유일한 실패 모드</b>인데, 낡으면 기간 판정이 막아 뱃지가 조용히
 * 사라진다. 사용자에게 거짓말은 안 하지만 <b>우리도 모른다</b> — 후속 캠페인이 열려도 시드를 안 고치면
 * 서비스는 계속 비어 있다.
 *
 * <h2>왜 매일 다 보내지 않는가</h2>
 *
 * <p>둘로 나눈 이유가 알림 피로다.
 *
 * <ul>
 *   <li><b>종료 예고</b>는 매일 돌지만 D-14 · D-7 · 당일에만 걸린다. 안 고쳐도 더 울리지 않는다
 *   <li><b>방치 요약</b>은 주 1회다. 종료됐거나 미검증인 정책은 고칠 때까지 계속 걸리므로, 매일 보내면
 *       며칠 만에 아무도 안 본다 — 그러면 알림이 없는 것과 같다
 * </ul>
 *
 * <p><b>보낼 것이 없는 날은 보내지 않는다.</b> "오늘은 없음" 도 소음이고, 조용한 날이 정상이라는 것을
 * 채널이 스스로 말해야 한다.
 *
 * <h2>왜 {@code @Profile("prod")} 가 아닌가</h2>
 *
 * <p>이슈는 로컬에서 디스코드가 울리는 것을 막자고 그 조건을 적었는데, <b>그건 이미 막혀 있다</b> —
 * {@code DiscordWebhookNotifier} 자체가 {@code @Profile("prod")} 이고 그 밖에서는 {@code LoggingNotifier}
 * 가 로그만 남긴다. 여기에 조건을 한 번 더 걸면 얻는 것은 로컬에서 조회 한 번을 아끼는 것뿐인데, 대신
 * <b>이 빈이 테스트 컨텍스트에 아예 없어져</b> 저장소부터 발송까지의 배선을 확인할 자리가 사라진다.
 *
 * <p>외부 API 는 부르지 않는다. 만료 판정은 날짜 비교고, 나가는 것은 웹훅 하나뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyAlertService {

    /** 정책 기간도 사람의 아침도 한국 기준이다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final PolicyRepository policyRepository;
    private final Notifier notifier;

    /** 매일 아침 9시 5분 — 자정에 보내면 어차피 아침에 본다. 정각을 피해 다른 배치와 겹치지 않게 했다. */
    @Scheduled(cron = "0 5 9 * * *", zone = "Asia/Seoul")
    public void notifyExpiring() {
        send("정책 종료 예고", true);
    }

    /** 월요일 아침 — 한 주에 한 번 밀린 것을 본다. */
    @Scheduled(cron = "0 15 9 * * MON", zone = "Asia/Seoul")
    public void notifyNeglected() {
        send("손봐야 할 정책", false);
    }

    /**
     * 오늘 걸리는 것을 모아 한 통으로 보낸다.
     *
     * <p><b>{@code @Transactional} 을 붙이지 않는다.</b> 위 두 메서드가 같은 빈에서 이걸 부르므로
     * self-invocation 이라 프록시를 안 탄다 — 붙여 봐야 동작하지 않고, 동작하는 것처럼 보이는 표시만
     * 남는다. 조회 한 번이라 Spring Data 가 여는 트랜잭션으로 충분하다.
     *
     * @param expiryNotice 종료 예고만 담을지, 그 밖의 방치만 담을지
     */
    void send(String title, boolean expiryNotice) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        List<PolicyAlert.Entry> entries =
                PolicyAlert.entriesOf(policyRepository.findAll(), today, expiryNotice);

        PolicyAlert.of(title, entries, today)
                .ifPresentOrElse(
                        alert -> {
                            notifier.send(alert.message());
                            log.info("정책 점검 알림 — {} {}건", title, entries.size());
                        },
                        // 0건도 남긴다 — 배치가 돌았는지, 왜 조용한지를 로그만으로 답할 수 있어야 한다.
                        () -> log.debug("정책 점검 — {} 없음", title));
    }
}
