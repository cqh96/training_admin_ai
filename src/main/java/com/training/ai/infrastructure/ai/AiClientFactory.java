package com.training.ai.infrastructure.ai;

import com.training.ai.domain.ai.AiProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AiClientFactory {
    
    private final List<AiClient> aiClients;
    private Map<AiProvider, AiClient> clientMap;
    
    public AiClient getClient(AiProvider provider) {
        if (clientMap == null) {
            clientMap = aiClients.stream()
                    .collect(Collectors.toMap(
                            client -> {
                                if (client instanceof OpenAiClient) return AiProvider.OPENAI;
                                if (client instanceof QwenClient) return AiProvider.QWEN;
                                if (client instanceof BigModelClient) return AiProvider.BIGMODEL;
                                return AiProvider.CUSTOM;
                            },
                            Function.identity()
                    ));
        }
        
        AiClient client = clientMap.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("不支持的AI提供商: " + provider);
        }
        return client;
    }
}
