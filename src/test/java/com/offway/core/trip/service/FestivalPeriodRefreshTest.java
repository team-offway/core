package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.FestivalPeriod;
import com.offway.core.trip.infrastructure.tour.dto.TourFestival;
import com.offway.core.trip.infrastructure.tour.dto.TourFestivalResult;
import com.offway.core.trip.repository.FestivalPeriodRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 취소된 축제를 언제 걷어내는가(#388).
 *
 * <p><b>여기가 헐거우면 멀쩡한 축제를 우리가 지운다.</b> 조용히, 되돌릴 수 없게. 그래서 "온전히 훑은
 * 회차" 의 정의를 테스트로 고정한다.
 *
 * <p>외부 경계는 stub 으로 격리하되 서비스는 실물이다 — 판정 자체가 확인 대상이라 흉내 내면 뜻이 없다.
 */
class FestivalPeriodRefreshTest {

    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);

    /** 지운 요청을 기록만 하는 저장소 — 판정이 언제 지우기로 하는지를 본다. */
    private static final class RecordingRepository implements FestivalPeriodRepository {

        private final List<Collection<String>> deleteCalls = new ArrayList<>();

        @Override
        public Map<String, FestivalPeriod> findByContentIds(Collection<String> contentIds) {
            return Map.of();
        }

        @Override
        public int upsertAll(Collection<FestivalPeriod> periods) {
            return periods.size();
        }

        @Override
        public int deleteMissingFrom(Collection<String> keptContentIds, LocalDate minEventEnd) {
            deleteCalls.add(keptContentIds);
            return 0;
        }
    }

    private static TourFestivalResult page(int totalCount) {
        return new TourFestivalResult(
                List.of(TourFestival.of("1", "축제", "20260701", "20260703").orElseThrow()), totalCount);
    }

    private static FestivalPeriodRefreshService service(RecordingRepository repository, int totalCount) {
        return new FestivalPeriodRefreshService(
                new com.offway.core.trip.infrastructure.tour.StubTourApiClient() {
                    @Override
                    public TourFestivalResult findFestivals(LocalDate from, int pageNo, int numOfRows) {
                        return page(totalCount);
                    }
                },
                repository,
                new com.offway.core.common.batch.repository.BatchRunRepository() {
                    @Override
                    public boolean hasRunOn(String name, LocalDate date) {
                        return false;
                    }

                    @Override
                    public boolean hasRunSince(String name, java.time.LocalDateTime since) {
                        return false;
                    }

                    @Override
                    public void markStarted(String name, java.time.LocalDateTime at) {
                        // 이 테스트는 정리 판정만 본다
                    }

                    @Override
                    public boolean tryStartOn(String name, LocalDate date, java.time.LocalDateTime at) {
                        return true;
                    }

                    @Override
                    public java.util.List<com.offway.core.common.batch.domain.BatchRun> all() {
                        // 이 테스트는 정리 판정만 본다 — 실행 이력을 읽는 경로가 아니다.
                        return java.util.List.of();
                    }
                });
    }

    @Test
    void 한_페이지로_다_받았으면_취소된_것을_지운다() {
        RecordingRepository repository = new RecordingRepository();

        service(repository, 1).refresh(FROM);

        assertEquals(1, repository.deleteCalls.size());
        assertTrue(repository.deleteCalls.get(0).contains("1"));
    }

    @Test
    void 상한에_걸려_다_못_받았으면_지우지_않는다() {
        // "이번에 안 온 것 = 취소됨" 이 성립하지 않는다. 지우면 멀쩡한 축제를 우리가 없앤다.
        RecordingRepository repository = new RecordingRepository();

        // 페이지 상한(10)을 넘는 건수 — 100건/페이지라 1,001건이면 11페이지다
        service(repository, 1_001).refresh(FROM);

        assertTrue(repository.deleteCalls.isEmpty());
    }
}
