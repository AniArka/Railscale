package com.railscale.train.repository;

import com.railscale.train.domain.Train;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrainRepository extends JpaRepository<Train, UUID> {
    Optional<Train> findByTrainNumber(String trainNumber);
    boolean existsByTrainNumber(String trainNumber);

    @EntityGraph(attributePaths = {"coaches", "coaches.seats"})
    Optional<Train> findWithCoachesAndSeatsById(UUID id);
}
