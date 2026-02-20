package com.training.ai.domain.ai;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_ai_record")
public class AiRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false, length = 50)
    private String provider;
    
    @Column(nullable = false, length = 50)
    private String model;
    
    @Column(columnDefinition = "TEXT")
    private String prompt;
    
    @Column(columnDefinition = "TEXT")
    private String response;
    
    @Column(nullable = false, length = 20)
    private String requestType;
    
    @Column
    private Integer inputTokens;
    
    @Column
    private Integer outputTokens;
    
    @Column
    private Integer totalTokens;
    
    @Column(nullable = false)
    private LocalDateTime createTime;
    
    @Column
    private LocalDateTime updateTime;
    
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
