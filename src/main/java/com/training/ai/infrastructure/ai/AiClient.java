package com.training.ai.infrastructure.ai;

import com.training.ai.application.dto.AiRequest;
import com.training.ai.application.dto.AiResponse;

public interface AiClient {
    AiResponse chat(AiRequest request);
}
