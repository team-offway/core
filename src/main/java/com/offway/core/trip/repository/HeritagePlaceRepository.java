package com.offway.core.trip.repository;

import com.offway.core.trip.domain.HeritagePlace;
import java.util.List;
import java.util.Optional;

/**
 * 국가유산 저장소 port(#160). 도메인·서비스는 이 인터페이스에만 의존한다.
 */
public interface HeritagePlaceRepository {

    /**
     * 코스에 쓸 수 있는 후보만 돌려준다 — 대분류가 방문 가능한 것(유적건조물·자연유산·등록문화유산).
     *
     * <p>필터를 저장소 경계에서 끝낸다. 호출부가 매번 걸러야 하면 한 군데만 빠뜨려도 소장 유물이 코스에 들어간다.
     */
    List<HeritagePlace> findVisitableCandidates(long regionId, int limit);

    Optional<HeritagePlace> findById(long id);

    long count();

    void deleteAll();

    int saveAll(List<HeritagePlace> places);
}
