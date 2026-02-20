package com.training.ai.application.service;

import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;
import ai.z.openapi.ZaiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Slf4j
@Service
public class OcrService {

    @Value("${ai.bigmodel.api-key}")
    private String apiKey;

    private ZaiClient zaiClient;

    public String recognizeText(MultipartFile imageFile) throws IOException {
        if (zaiClient == null) {
            zaiClient = ZaiClient.builder()
                    .ofZAI()
                    .apiKey(apiKey)
                    .build();
            log.info("初始化ZaiClient用于OCR");
        }

        log.info("开始OCR识别图片: {}", imageFile.getOriginalFilename());

        byte[] imageBytes = imageFile.getBytes();
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        String prompt = "请识别这张图片中的文字内容，只返回识别到的文字，不要添加任何其他说明。如果图片中没有文字，请返回'无文字'。";

        ChatCompletionCreateParams chatRequest = ChatCompletionCreateParams.builder()
                .model("glm-4v")
                .messages(java.util.List.of(
                        ChatMessage.builder()
                                .role(ChatMessageRole.USER.value())
                                .content(java.util.List.of(
                                        java.util.Map.of(
                                                "type", "image_url",
                                                "image_url", java.util.Map.of(
                                                        "url", "data:image/jpeg;base64," + base64Image
                                                )
                                        ),
                                        java.util.Map.of(
                                                "type", "text",
                                                "text", prompt
                                        )
                                ))
                                .build()
                ))
                .temperature(0.3F)
                .maxTokens(1000)
                .stream(false)
                .build();

        try {
            ChatCompletionResponse response = zaiClient.chat().createChatCompletion(chatRequest);

            if (!response.isSuccess()) {
                throw new RuntimeException("OCR识别失败: " + response.getMsg());
            }

            String recognizedText = response.getData().getChoices().get(0).getMessage().getContent().toString();
            log.info("OCR识别结果: {}", recognizedText);

            return recognizedText;
        } catch (Exception e) {
            log.error("OCR识别失败", e);
            throw new RuntimeException("OCR识别失败: " + e.getMessage(), e);
        }
    }

    public String recognizeText(String imagePath) throws IOException {
        Path path = Path.of(imagePath);
        byte[] imageBytes = Files.readAllBytes(path);
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        if (zaiClient == null) {
            zaiClient = ZaiClient.builder()
                    .ofZAI()
                    .apiKey(apiKey)
                    .build();
        }

        log.info("开始OCR识别图片: {}", imagePath);

        String prompt = "请识别这张图片中的文字内容，只返回识别到的文字，不要添加任何其他说明。如果图片中没有文字，请返回'无文字'。";

        ChatCompletionCreateParams chatRequest = ChatCompletionCreateParams.builder()
                .model("glm-4v")
                .messages(java.util.List.of(
                        ChatMessage.builder()
                                .role(ChatMessageRole.USER.value())
                                .content(java.util.List.of(
                                        java.util.Map.of(
                                                "type", "image_url",
                                                "image_url", java.util.Map.of(
                                                        "url", "data:image/jpeg;base64," + base64Image
                                                )
                                        ),
                                        java.util.Map.of(
                                                "type", "text",
                                                "text", prompt
                                        )
                                ))
                                .build()
                ))
                .temperature(0.3F)
                .maxTokens(1000)
                .stream(false)
                .build();

        try {
            ChatCompletionResponse response = zaiClient.chat().createChatCompletion(chatRequest);

            if (!response.isSuccess()) {
                throw new RuntimeException("OCR识别失败: " + response.getMsg());
            }

            String recognizedText = response.getData().getChoices().get(0).getMessage().getContent().toString();
            log.info("OCR识别结果: {}", recognizedText);

            return recognizedText;
        } catch (Exception e) {
            log.error("OCR识别失败", e);
            throw new RuntimeException("OCR识别失败: " + e.getMessage(), e);
        }
    }
}
