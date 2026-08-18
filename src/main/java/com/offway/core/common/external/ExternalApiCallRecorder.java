package com.offway.core.common.external;

import com.offway.core.common.notification.Notifier;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 외부 API 를 부를 때마다 오늘자 사용량을 남긴다(#123).
 *
 * <p><b>왜 필요했나.</b> 인증으로 외부인은 막아도 <b>팀이 개발하며 태우는 것은 막을 수 없다.</b> 막는 대신
 * 보이게 한다. 지금은 한도를 넘겨야만, 그것도 외부 응답이 실패로 바뀌어야만 안다.
 *
 * <p>실제로 그렇게 하루를 태웠다 — 응답 필드 채움률을 재느라 관광정보를 500건 넘게 썼고, 그날 운영 코스 생성이
 * 인허가 폴백으로 내려갔다. 남은 양을 볼 수 있었다면 표본 수를 먼저 줄였을 것이다.
 *
 * <p><b>막지는 않는다.</b> 먼저 숫자를 보고 실제 소비 패턴을 안 뒤에 정한다. 지금 막으면 개발 중 기능이
 * 조용히 죽는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalApiCallRecorder {

    /** 리셋 경계. 공공데이터포털의 일일 한도가 KST 자정에 돌아온다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** 경고를 띄우는 소진율. 평소엔 조용하고 위험할 때만 시끄럽게 한다. */
    private static final int WARN_PERCENT = 70;
    private static final int URGENT_PERCENT = 90;

    private final ExternalApiCallRepository repository;
    private final Notifier notifier;

    /**
     * 한 건 기록한다. <b>실호출 직전</b>에 클라이언트가 명시적으로 부른다.
     *
     * <p>WebClient 필터로 URL 을 파싱해 어느 API 인지 추측하지 않는다 — 경로가 바뀌면 조용히 안 세게 되고,
     * 그때 화면은 "여유 있음" 으로 보인다.
     *
     * <p>기록 실패가 외부 호출을 막지 않는다. 사용량 집계는 관측이지 기능이 아니다.
     */
    public void record(ExternalApi api) {
        try {
            LocalDate today = today();
            long used = repository.recordAndCount(api, today);
            repository.recordCaller(api, today, CallerContext.current());
            logUsage(api, used);
            notifyIfStepCrossed(api, today, used);
        } catch (RuntimeException e) {
            log.warn("외부 API 사용량 기록 실패 api={} cause={}", api, e.getClass().getSimpleName());
        }
    }

    /** 오늘자 API 별 사용량·잔여. 한 번도 안 부른 API 도 0 으로 함께 낸다. */
    public Map<ExternalApi, Long> usageToday() {
        return repository.countsOn(today());
    }

    /** 오늘자 API 별 <b>주체 내역</b>(#285). 한 번도 안 부른 API 는 키가 없다. */
    public Map<ExternalApi, Map<String, Long>> callerUsageToday() {
        return repository.callerCountsOn(today());
    }

    /** 지금 기준 KST 날짜. 자정을 넘기면 새 행이 되어 자연히 리셋된다. */
    public LocalDate today() {
        return LocalDate.now(SERVICE_ZONE);
    }

    /**
     * 10% 단계를 처음 넘겼으면 팀에 알린다(#257).
     *
     * <p><b>단계 판정만으로는 부족하다.</b> 이 메서드는 외부 호출마다 도므로, 70% 를 넘긴 뒤로는 매 호출이
     * 같은 단계에 있다. "어디까지 알렸나" 를 DB 가 들고 있고, 조건부 UPDATE 를 이긴 호출만 실제로 보낸다.
     */
    private void notifyIfStepCrossed(ExternalApi api, LocalDate date, long used) {
        int step = api.usageStep(used);
        if (step <= 0 || !repository.claimNotifyStep(api, date, step)) {
            return;
        }
        notifier.send(usageMessage(api, date, used, step));
    }

    /**
     * 한 줄로 읽히게. 한도 초과는 지금 무엇이 깨지는지까지 말한다.
     *
     * <p><b>둘째 줄에 주체 내역을 싣는다(#285).</b> 전에는 초과 사실만 알려줘 그 알림을 받고도 할 수 있는
     * 일이 없었다 — 배치가 태웠는지 코스 생성이 태웠는지 몰라 다음 행동이 안 정해졌다.
     */
    private String usageMessage(ExternalApi api, LocalDate date, long used, int step) {
        String usage = "%s %d/%d (%d%%)".formatted(api.label(), used, api.dailyLimit(), ExternalApi.percentOf(step));
        String headline = used >= api.dailyLimit()
                ? "🔴 " + usage + " — 한도 소진. 이후 호출은 실패합니다"
                : "⚠️ " + usage;
        return headline + callerLine(api, date);
    }

    /**
     * 주체 내역 줄. 실을 것이 없으면 <b>줄 자체를 안 붙인다</b> — 빈 줄이 붙으면 메시지가 고장 난 것처럼 보인다.
     *
     * <p>내역 조회가 실패해도 알림은 나간다. 한도가 찼다는 사실이 내역보다 급하다.
     */
    private String callerLine(ExternalApi api, LocalDate date) {
        try {
            CallerBreakdown breakdown = CallerBreakdown.of(repository.callerCountsOn(api, date));
            return breakdown.isEmpty() ? "" : "\n" + breakdown.describe();
        } catch (RuntimeException e) {
            log.warn("외부 API 주체 내역 조회 실패 api={} cause={}", api, e.getClass().getSimpleName());
            return "";
        }
    }

    private void logUsage(ExternalApi api, long used) {
        int limit = api.dailyLimit();
        long percent = used * 100 / limit;
        if (used > limit) {
            log.error("외부 API 한도 초과 api={} today={}/{} — 이후 호출은 실패합니다", api.label(), used, limit);
            return;
        }
        if (percent >= URGENT_PERCENT) {
            log.warn("외부 API 한도 임박 api={} today={}/{} ({}%)", api.label(), used, limit, percent);
            return;
        }
        if (percent >= WARN_PERCENT) {
            log.warn("외부 API 사용량 api={} today={}/{} ({}%)", api.label(), used, limit, percent);
            return;
        }
        log.debug("외부 API 호출 api={} today={}/{}", api.label(), used, limit);
    }
}
