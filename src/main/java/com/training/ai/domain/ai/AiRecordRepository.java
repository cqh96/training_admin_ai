package com.training.ai.domain.ai;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiRecordRepository extends JpaRepository<AiRecord, Long>, JpaSpecificationExecutor<AiRecord> {
    
    Page<AiRecord> findByUserId(Long userId, Pageable pageable);
    
    List<AiRecord> findByUserIdAndCreateTimeBetween(Long userId, LocalDateTime startTime, LocalDateTime endTime);
    
    Long countByUserIdAndCreateTimeBetween(Long userId, LocalDateTime startTime, LocalDateTime endTime);
}
