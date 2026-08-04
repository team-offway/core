package com.offway.core.trip.repository;

import java.util.Optional;

/** 적재된 장소 풀의 출처 기록 port(#144). */
public interface PlacePoolSourceRepository {

    /** 마지막으로 적재한 파일의 체크섬. 적재된 적이 없으면 비어 있음. */
    Optional<String> findChecksum();

    /** 이번 적재의 출처를 기록한다(행은 항상 하나라 덮어쓴다). */
    void record(String checksum, int placeCount);
}
