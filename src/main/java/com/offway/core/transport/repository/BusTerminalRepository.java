package com.offway.core.transport.repository;

import com.offway.core.transport.domain.BusTerminal;
import java.util.List;

/** 고속버스 터미널 마스터 조회 port(#107). 구현은 {@link BusTerminalRepositoryImpl}. */
public interface BusTerminalRepository {

    /** 전국 터미널 전체(452곳) — resolver 가 인메모리로 들고 최근접을 계산한다. */
    List<BusTerminal> findAll();
}
