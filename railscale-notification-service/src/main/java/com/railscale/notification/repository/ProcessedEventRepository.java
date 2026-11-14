package com.railscale.notification.repository;

import com.railscale.notification.domain.ProcessedEvent;
import com.railscale.notification.domain.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}
