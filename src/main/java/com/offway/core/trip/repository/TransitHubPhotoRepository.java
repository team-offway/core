package com.offway.core.trip.repository;

import com.offway.core.trip.domain.TransitHubPhoto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 교통 거점 사진 조회 port(#450) — 도메인이 의존한다. */
public interface TransitHubPhotoRepository {

    Optional<TransitHubPhoto> findByHubName(String hubName);

    /** 이름 여럿의 사진 — 코스 응답이 한 번에 읽는다. */
    List<TransitHubPhoto> findByHubNames(Collection<String> hubNames);

    List<TransitHubPhoto> findAll();

    void save(TransitHubPhoto photo);
}
