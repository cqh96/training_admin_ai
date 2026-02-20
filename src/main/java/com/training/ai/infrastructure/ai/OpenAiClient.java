package com.training.ai.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.ai.application.dto.AiRequest;
import com.training.ai.application.dto.AiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiClient implements AiClient {
    
    @Value("${ai.openai.api-key}")
    private String apiKey;
    
    @Value("${ai.openai.api-url}")
    private String apiUrl;
    
    @Value("${ai.openai.timeout}")
    private int timeout;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public AiResponse chat(AiRequest request) {
        try {
            String url = apiUrl + "/chat/completions";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            Map<String, Object> body = new HashMap<>();
            body.put("model", request.getModel());
            body.put("messages", List.of(
                    Map.of("role", "user", "content", request.getPrompt())
            ));
            body.put("temperature", request.getTemperature());
            body.put("max_tokens", request.getMaxTokens());
            
            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(body),
                    headers
            );
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String content = jsonNode.get("choices").get(0).get("message").get("content").asText();
            
            JsonNode usage = jsonNode.get("usage");
            int inputTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
            int outputTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
            int totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : 0;
            
            return AiResponse.builder()
                    .content(content)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .totalTokens(totalTokens)
                    .model(request.getModel())
                    .build();
                    
        } catch (Exception e) {
            log.error("OpenAI API调用失败", e);
            throw new RuntimeException("OpenAI API调用失败: " + e.getMessage());
        }
    }
}
