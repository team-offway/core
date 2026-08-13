package com.offway.core.common.batch.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
}
