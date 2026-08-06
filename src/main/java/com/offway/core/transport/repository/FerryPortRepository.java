package com.offway.core.transport.repository;

import com.offway.core.transport.domain.FerryPort;
import java.util.List;

/** 여객선 항구 마스터 조회 port(#97). 구현은 {@link FerryPortRepositoryImpl}. */
public interface FerryPortRepository {

    /** 전국 항구 전체(500곳) — resolver 가 인메모리로 들고 최근접을 계산한다. */
    List<FerryPort> findAll();
}
