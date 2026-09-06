package com.offway.core.trip.repository;

import com.offway.core.trip.domain.TransitHubPhoto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitHubPhotoJpaRepository extends JpaRepository<TransitHubPhoto, Long> {

    Optional<TransitHubPhoto> findByHubName(String hubName);

    List<TransitHubPhoto> findByHubNameIn(Collection<String> hubNames);
}
