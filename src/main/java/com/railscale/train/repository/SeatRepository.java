package com.railscale.train.repository;

import com.railscale.train.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findByCoachId(UUID coachId);
    List<Seat> findByCoachTrainId(UUID trainId);
}
