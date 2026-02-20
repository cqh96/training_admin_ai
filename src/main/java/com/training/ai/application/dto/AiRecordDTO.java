package com.training.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecordDTO {
    private Long id;
    private String provider;
    private String model;
    private String prompt;
    private String response;
    private String requestType;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private LocalDateTime createTime;
}
