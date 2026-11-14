package com.railscale.train.repository;

import com.railscale.train.domain.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StationRepository extends JpaRepository<Station, UUID> {
    Optional<Station> findByCode(String code);
    boolean existsByCode(String code);
}
