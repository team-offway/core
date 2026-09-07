package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.trip.infrastructure.datalab.RelatedAttractionClient;
import com.offway.core.trip.infrastructure.datalab.StubRelatedAttractionClient;
import com.offway.core.trip.infrastructure.datalab.dto.RelatedAttractionItem;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 연관 관광지 적재가 <b>같은 날 몇 번이고 다시 쏘지 않는가</b>.
 *
 * <h2>왜 이것을 잠그나</h2>
 *
 * <p>건너뛰기 판정이 "그 지역이 목표월 자료를 가졌나" 였다. 원본이 목표월을 아직 발행하지 않으면
 * 되짚기가 이전 달을 다시 저장하는데, 저장된 달은 여전히 목표월보다 앞이라 <b>다음 회차에 또
 * 걸린다</b> — 조건이 사실상 참이 되지 않는다.
 *
 * <p>실측(2026-09-07). 89곳이 6·7월 자료를 갖고 있는데 목표가 8월이라 전부 낡음으로 걸렸고, 회차마다
 * <b>194콜</b>을 태우며 같은 7월 행을 다시 저장했다. {@code fixedDelay} 는 재배포마다 처음부터 다시
 * 세므로 배포가 잦은 날 네 번 돌아 하루 <b>780콜 · 한도의 78%</b> 가 됐다. 늘어난 자료는 0건이다.
 *
 * <p>{@code HubAttractionRefreshService} 가 같은 자리에서 같은 실수를 했다(#337). 그쪽이 이미 가진
 * 해법(하루 한 번)을 여기에도 건다.
 */
@SpringBootTest
class RelatedAttractionRefreshIntegrationTest {

    @Autowired
    private RelatedAttractionRefreshService refreshService;

    @Autowired
    private StubRelatedAttractionClient relatedAttractionClient;

    @Autowired
    private BatchRunRepository batchRunRepository;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        RelatedAttractionClient stubRelatedAttractionClient() {
            return new StubRelatedAttractionClient();
        }
    }

    /**
     * 이 배치가 "오늘은 아직 안 돌았다" 인 상태로 만든다.
     *
     * <p>클래스에 {@code @Transactional} 이 없어 실행 기록이 커밋된다. 앞 테스트가 남긴 오늘 기록이
     * 뒤 테스트를 통째로 건너뛰게 만들므로, 각 테스트가 자기 전제를 직접 만든다.
     */
    private void notRunToday() {
        batchRunRepository.markStarted(
                RelatedAttractionRefreshService.BATCH_NAME, LocalDate.now().minusDays(1).atTime(3, 0));
    }

    private static RelatedAttractionItem item(String sigungu) {
        return new RelatedAttractionItem(
                "hub-1", "공산성", "code-1", "산성시장", 1, "관광지", "쇼핑", sigungu);
    }

    /**
     * <b>오늘 이미 돌았으면 외부를 부르지 않는다.</b>
     *
     * <p>이게 없으면 재배포마다 {@code fixedDelay} 가 리셋돼 하루에도 몇 번씩 89곳을 다시 쏜다.
     */
    @Test
    void 오늘_이미_돌았으면_외부를_부르지_않는다() {
        notRunToday();
        relatedAttractionClient.respond((legalCode, month) -> List.of());
        refreshService.refreshIfStale();

        relatedAttractionClient.respond((legalCode, month) -> {
            throw new AssertionError("오늘 이미 돌았는데 외부를 불렀다");
        });
        refreshService.refreshIfStale();
    }

    /**
     * <b>목표월이 미발행이라 아무것도 못 채운 날에도 같은 날 다시 부르지 않는다.</b>
     *
     * <p>폭주를 끊는 핵심이다. 결과로 판정하면 이 상황에서 저장된 달이 목표월보다 계속 앞이라
     * 회차마다 같은 호출이 되풀이된다 — 2026-09-07 에 하루 780콜을 태운 경로가 정확히 이것이다.
     */
    @Test
    void 목표월이_미발행이어도_같은_날_다시_부르지_않는다() {
        notRunToday();
        // 목표월(지난달)은 비어 있고 그 이전 달만 있다 — 운영에서 실제로 있던 상태.
        YearMonth published = YearMonth.from(LocalDate.now()).minusMonths(2);
        relatedAttractionClient.respond((legalCode, month) ->
                month.equals(published) ? List.of(item("공주시")) : List.of());
        refreshService.refreshIfStale();
        assertFalse(relatedAttractionClient.calls().isEmpty(), "첫 회차는 실제로 물어봐야 한다");

        relatedAttractionClient.respond((legalCode, month) -> {
            throw new AssertionError("목표월을 못 채웠다고 같은 날 다시 부르면 한도만 더 태운다");
        });
        refreshService.refreshIfStale();
    }

    /** 스킵이 영구적이면 원본이 새 달을 내도 영영 안 받는다 — 하루가 지나면 다시 돌아야 한다. */
    @Test
    void 어제_돌았으면_오늘_다시_부른다() {
        notRunToday();
        AtomicBoolean called = new AtomicBoolean();
        relatedAttractionClient.respond((legalCode, month) -> {
            called.set(true);
            return List.of();
        });

        refreshService.refreshIfStale();

        assertTrue(called.get(), "하루가 지나면 다시 받아야 한다");
    }
}
