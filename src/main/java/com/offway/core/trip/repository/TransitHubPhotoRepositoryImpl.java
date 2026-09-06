package com.offway.core.trip.repository;

import com.offway.core.trip.domain.TransitHubPhoto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TransitHubPhotoRepositoryImpl implements TransitHubPhotoRepository {

    private final TransitHubPhotoJpaRepository transitHubPhotoJpaRepository;

    @Override
    public Optional<TransitHubPhoto> findByHubName(String hubName) {
        return transitHubPhotoJpaRepository.findByHubName(hubName);
    }

    @Override
    public List<TransitHubPhoto> findByHubNames(Collection<String> hubNames) {
        return hubNames.isEmpty() ? List.of() : transitHubPhotoJpaRepository.findByHubNameIn(hubNames);
    }

    @Override
    public List<TransitHubPhoto> findAll() {
        return transitHubPhotoJpaRepository.findAll();
    }

    @Override
    public void save(TransitHubPhoto photo) {
        transitHubPhotoJpaRepository.save(photo);
    }
}
