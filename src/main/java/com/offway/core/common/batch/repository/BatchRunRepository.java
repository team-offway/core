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
     */
    boolean hasRunSince(String name, LocalDateTime since);

    /** 시작 시각을 남긴다 — 배치당 한 행이라 있으면 갱신, 없으면 만든다. */
    void markStarted(String name, LocalDateTime at);
}
