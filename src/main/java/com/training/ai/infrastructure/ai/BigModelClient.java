package com.training.ai.infrastructure.ai;

import ai.z.openapi.ZaiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;
import com.training.ai.application.dto.AiRequest;
import com.training.ai.application.dto.AiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
public class BigModelClient implements AiClient {
    
    @Value("${ai.bigmodel.api-key}")
    private String apiKey;
    
    private ZaiClient zaiClient;
    
    @Override
    public AiResponse chat(AiRequest request) {
        try {
            if (zaiClient == null) {
                zaiClient = ZaiClient.builder()
                        .ofZAI()
                        .apiKey(apiKey)
                        .build();
                log.info("初始化ZaiClient客户端");
            }
            
            log.info("调用BigModel API: model={}, prompt={}", request.getModel(), 
                    request.getPrompt().substring(0, Math.min(50, request.getPrompt().length())));
            
            ChatCompletionCreateParams chatRequest = ChatCompletionCreateParams.builder()
                    .model(request.getModel())
                    .messages(Arrays.asList(
                            ChatMessage.builder()
                                    .role(ChatMessageRole.USER.value())
                                    .content(request.getPrompt())
                                    .build()
                    ))
                    .temperature(request.getTemperature().floatValue())
                    .maxTokens(request.getMaxTokens())
                    .stream(false)
                    .build();
            
            ChatCompletionResponse response = zaiClient.chat().createChatCompletion(chatRequest);
            
            if (!response.isSuccess()) {
                throw new RuntimeException("BigModel API调用失败: " + response.getMsg());
            }
            
            log.info("BigModel API调用成功: model={}, usage={}", 
                    request.getModel(), response.getData().getUsage());
            
            String content = response.getData().getChoices().get(0).getMessage().getContent().toString();
            
            int inputTokens = response.getData().getUsage() != null ? 
                    response.getData().getUsage().getPromptTokens() : 0;
            int outputTokens = response.getData().getUsage() != null ? 
                    response.getData().getUsage().getCompletionTokens() : 0;
            int totalTokens = response.getData().getUsage() != null ? 
                    response.getData().getUsage().getTotalTokens() : 0;
            
            return AiResponse.builder()
                    .content(content)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .totalTokens(totalTokens)
                    .model(request.getModel())
                    .build();
                    
        } catch (Exception e) {
            log.error("BigModel API调用失败", e);
            throw new RuntimeException("BigModel API调用失败: " + e.getMessage(), e);
        }
    }
}
