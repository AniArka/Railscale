package com.railscale.event.repository;

import com.railscale.event.domain.ProcessedEvent;
import com.railscale.event.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}
