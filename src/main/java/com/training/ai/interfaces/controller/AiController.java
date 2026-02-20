package com.training.ai.interfaces.controller;

import com.training.ai.application.dto.AiRecordDTO;
import com.training.ai.application.dto.AiRequest;
import com.training.ai.application.dto.AiResponse;
import com.training.ai.application.service.AiService;
import com.training.ai.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {
    
    private final AiService aiService;
    
    @PostMapping("/chat")
    public Result<AiResponse> chat(@Valid @RequestBody AiRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        request.setUserId(userId);
        AiResponse response = aiService.chat(request);
        return Result.success(response);
    }
    
    @GetMapping("/records")
    public Result<Page<AiRecordDTO>> getRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<AiRecordDTO> records = aiService.getRecords(userId, pageable);
        return Result.success(records);
    }
}
