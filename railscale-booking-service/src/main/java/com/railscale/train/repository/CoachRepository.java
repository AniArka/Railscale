package com.railscale.train.repository;

import com.railscale.train.domain.Coach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoachRepository extends JpaRepository<Coach, UUID> {
    List<Coach> findByTrainId(UUID trainId);
}
