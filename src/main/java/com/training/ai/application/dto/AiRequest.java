package com.training.ai.application.dto;

import com.training.ai.domain.ai.AiProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiRequest {
    
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    
    @NotNull(message = "AI提供商不能为空")
    private AiProvider provider;
    
    @NotBlank(message = "模型不能为空")
    private String model;
    
    @NotBlank(message = "提示词不能为空")
    private String prompt;
    
    private Double temperature = 0.7;
    
    private Integer maxTokens = 2000;
}
