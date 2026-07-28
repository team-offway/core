package com.offway.core.transport.repository;

import com.offway.core.transport.domain.TrainStation;
import java.util.List;

/** 기차역 마스터 조회 port. 구현은 {@link TrainStationRepositoryImpl}. */
public interface TrainStationRepository {

    /** 전국 역 전체(수백 개) — resolver 가 인메모리로 들고 최근접을 계산한다. */
    List<TrainStation> findAll();
}
