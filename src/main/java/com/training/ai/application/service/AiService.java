package com.training.ai.application.service;

import com.training.ai.application.dto.AiRequest;
import com.training.ai.application.dto.AiResponse;
import com.training.ai.application.dto.AiRecordDTO;
import com.training.ai.common.exception.BusinessException;
import com.training.ai.domain.ai.AiRecord;
import com.training.ai.domain.ai.AiRecordRepository;
import com.training.ai.domain.user.User;
import com.training.ai.domain.user.UserRepository;
import com.training.ai.infrastructure.ai.AiClient;
import com.training.ai.infrastructure.ai.AiClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {
    
    private final AiClientFactory aiClientFactory;
    private final AiRecordRepository aiRecordRepository;
    private final UserRepository userRepository;
    
    @Transactional
    public AiResponse chat(AiRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        AiClient aiClient = aiClientFactory.getClient(request.getProvider());
        
        try {
            AiResponse response = aiClient.chat(request);
            
            AiRecord record = AiRecord.builder()
                    .userId(request.getUserId())
                    .provider(String.valueOf(request.getProvider()))
                    .model(request.getModel())
                    .prompt(request.getPrompt())
                    .response(response.getContent())
                    .requestType("chat")
                    .inputTokens(response.getInputTokens())
                    .outputTokens(response.getOutputTokens())
                    .totalTokens(response.getTotalTokens())
                    .build();
            
            aiRecordRepository.save(record);
            
            log.info("AI请求成功: userId={}, provider={}, model={}", 
                    request.getUserId(), request.getProvider(), request.getModel());
            
            return response;
            
        } catch (Exception e) {
            log.error("AI请求失败: userId={}, provider={}, model={}", 
                    request.getUserId(), request.getProvider(), request.getModel(), e);
            
            AiRecord record = AiRecord.builder()
                    .userId(request.getUserId())
                    .provider(String.valueOf(request.getProvider()))
                    .model(request.getModel())
                    .prompt(request.getPrompt())
                    .response("请求失败: " + e.getMessage())
                    .requestType("chat")
                    .build();
            
            aiRecordRepository.save(record);
            
            throw new BusinessException("AI服务调用失败: " + e.getMessage());
        }
    }
    
    public Page<AiRecordDTO> getRecords(Long userId, Pageable pageable) {
        return aiRecordRepository.findByUserId(userId, pageable)
                .map(this::convertToDTO);
    }
    
    private AiRecordDTO convertToDTO(AiRecord record) {
        return AiRecordDTO.builder()
                .id(record.getId())
                .provider(record.getProvider())
                .model(record.getModel())
                .prompt(record.getPrompt())
                .response(record.getResponse())
                .requestType(record.getRequestType())
                .inputTokens(record.getInputTokens())
                .outputTokens(record.getOutputTokens())
                .totalTokens(record.getTotalTokens())
                .createTime(record.getCreateTime())
                .build();
    }
}
