package com.training.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResponse {
    private String content;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private String model;
}
