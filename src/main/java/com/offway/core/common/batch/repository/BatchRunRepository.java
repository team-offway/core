package com.offway.core.common.batch.repository;

import com.offway.core.common.batch.domain.BatchRun;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 배치 실행 기록 port(#226). 구현은 {@link BatchRunRepositoryImpl}. */
public interface BatchRunRepository {

    /** 그 배치가 그 날짜에 시작한 적이 있는가. 기록이 없으면 거짓. */
    boolean hasRunOn(String name, LocalDate date);

    /**
     * 그 배치가 {@code since} 이후에 시작한 적이 있는가. 기록이 없으면 거짓.
     *
     * <p>하루보다 긴 주기를 쓰는 배치용이다. {@link #hasRunOn} 은 "오늘 돌았나" 만 답해서, 주 1회짜리는
     * 어제 돌았어도 오늘 또 돈다.
     *
     * <p><b>이 판정은 자기 DB 안에서만 유효하다.</b> 기록이 DB 에 남으므로 재배포·재기동에는 강하지만,
     * DB 가 다르면 각자 "오늘 처음" 이라고 답한다. 로컬과 운영이 같은 외부 API 키를 쓰면 소비가 두 배가
     * 된다 — 실제로 배치만으로 일일 한도가 다 찼다(#254). 그건 여기서 못 막고, 회차당 처리량을 환경별로
     * 줄여 대응한다({@code BatchBudgetProperties}).
     */
    boolean hasRunSince(String name, LocalDateTime since);

    /** 시작 시각을 남긴다 — 배치당 한 행이라 있으면 갱신, 없으면 만든다. */
    void markStarted(String name, LocalDateTime at);

    /**
     * 그 날짜의 실행을 <b>선점</b>한다 — 이긴 쪽만 참을 받는다(#314).
     *
     * <p><b>{@link #hasRunOn} + {@link #markStarted} 로는 못 막는 경우가 있다.</b> 확인과 기록이 별개
     * 작업이라 그 사이에 다른 실행이 끼어들 수 있고, 트리거가 둘인 배치는 실제로 그 창을 만난다
     * ({@code RegionPoiRefreshService} 가 cron 과 부팅 확인을 함께 쓴다). 둘 다 "아직 안 돌았다" 를 읽으면
     * 같은 날 267콜을 <b>두 번</b> 쏜다 — 관광정보 일일 한도의 절반이 넘는다.
     *
     * <p>조건부 UPDATE 한 문장이 판정과 기록을 함께 한다. 행이 아직 없을 때만 INSERT 로 내려가고, 그 경합은
     * {@code uk_batch_run_name} 이 갈라 준다. {@code ExternalApiCallRepository.claimNotifyStep} 이 같은
     * 이유로 같은 모양을 쓴다.
     *
     * <p>선점에 성공하면 시작 시각이 <b>이미 기록된 상태</b>다 — 이후 작업이 실패해도 그날 다시 쏘지 않는다.
     *
     * @return 이번 호출이 그 날짜를 처음 선점했으면 {@code true}. 이미 누가 잡았으면 {@code false}
     */
    boolean tryStartOn(String name, LocalDate date, LocalDateTime at);

    /**
     * 기록이 있는 배치 전부 — <b>마지막으로 언제 돌았나</b>(#398).
     *
     * <p>한도를 태우는 쪽이 대부분 배치라, 사용량 그래프가 튄 날을 설명하려면 그날 무엇이 돌았는지를
     * 함께 봐야 한다.
     *
     * <p>행 수는 배치 종류만큼이라(현재 열 남짓) 상한을 따로 두지 않는다. 이름 순으로 온다.
     */
    List<BatchRun> all();
}
