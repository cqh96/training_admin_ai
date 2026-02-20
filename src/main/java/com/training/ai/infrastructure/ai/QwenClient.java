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
public class QwenClient implements AiClient {
    
    @Value("${ai.qwen.api-key}")
    private String apiKey;
    
    @Value("${ai.qwen.api-url}")
    private String apiUrl;
    
    @Value("${ai.qwen.timeout}")
    private int timeout;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public AiResponse chat(AiRequest request) {
        try {
            String url = apiUrl + "/services/aigc/text-generation/generation";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            Map<String, Object> body = new HashMap<>();
            body.put("model", request.getModel());
            body.put("input", Map.of("messages", List.of(
                    Map.of("role", "user", "content", request.getPrompt())
            )));
            body.put("parameters", Map.of(
                    "temperature", request.getTemperature(),
                    "max_tokens", request.getMaxTokens()
            ));
            
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
            String content = jsonNode.get("output").get("text").asText();
            
            JsonNode usage = jsonNode.get("usage");
            int inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
            int outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;
            int totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : 0;
            
            return AiResponse.builder()
                    .content(content)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .totalTokens(totalTokens)
                    .model(request.getModel())
                    .build();
                    
        } catch (Exception e) {
            log.error("Qwen API调用失败", e);
            throw new RuntimeException("Qwen API调用失败: " + e.getMessage());
        }
    }
}
