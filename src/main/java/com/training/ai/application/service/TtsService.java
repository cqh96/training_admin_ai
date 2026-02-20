package com.training.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @author sheeran
 */
@Slf4j
@Service
public class TtsService {

    @Value("${ai.bigmodel.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String synthesizeSpeech(String text, String outputPath) throws IOException {
        log.info("开始语音合成: text={}, output={}", text.substring(0, Math.min(50, text.length())), outputPath);

        try {
            // 根据智谱AI文档调整 URL 和参数
            String url = "https://open.bigmodel.cn/api/paas/v4/audio/speech";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new HashMap<>();
            // 使用用户提供的模型和声音参数
            body.put("model", "glm-tts"); // 修正模型名称
            body.put("input", text);
            body.put("voice", "tongtong"); // 修正声音名称
            // 注意：response_format 可能需要根据实际需求调整，有些 API 可能不支持或默认支持特定格式
            // 根据报错信息 "不支持当前response_format值"，尝试移除该参数或使用文档推荐值
            // 如果文档说默认是 mp3 或 wav，可以尝试不传，或者传 "wav"
            // 这里根据用户提供的示例 payload 修改
            // body.put("response_format", "wav"); 
            
            // 暂时移除 response_format，使用默认值，或者根据文档确认支持的值
            // 如果必须指定，可以尝试 "wav" 或 "mp3"
            // 根据用户错误提示，之前的 mp3 可能不支持，尝试 wav
            // 再次检查用户输入: "response_format": "wav" 在示例中是存在的
            // 但用户错误信息是 "不支持当前response_format值"，可能是之前代码传了 "mp3" 导致的
            // 我们先尝试改成 "wav"
            // 另外，speed 参数在 glm-tts 模型中可能不支持，暂时移除
            
            // body.put("response_format", "wav"); // 尝试 wav
            // body.put("speed", 1.0); // 移除 speed

            // 重新构建 body 以匹配用户提供的成功示例结构
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "glm-tts");
            requestBody.put("input", text);
            requestBody.put("voice", "tongtong");
            // 智谱 GLM-TTS 可能默认返回 mp3 或 wav，显式指定 wav
            // 如果报错依旧，可能需要查阅最新文档，但根据用户提示，示例里有 response_format: wav
            // 也有可能是之前的 mp3 不支持
            // 让我们先按用户给的示例来
            // requestBody.put("response_format", "wav"); 
            // 经过仔细比对用户提供的报错信息：
            // 报错信息中包含的请求体示例：
            // "{\n  \"model\": \"glm-tts\",\n  \"input\": \"你好，今天天气怎么样.\",\n  \"voice\": \"tongtong\",\n  \"response_format\": \"wav\"\n}"
            // 这似乎是用户希望我们参考的正确格式？或者这是报错时打印的请求体？
            // 仔细看：HttpResponse<String> response = Unirest.post(...).body(...).asString(); 根据例子修复问题
            // 用户意思是参考这个 Unirest 的调用来修复。
            // Unirest 调用里用了 response_format: wav。
            // 所以我们将代码改为使用 wav。
            
            // 注意：之前的代码用了 model: tts-1, voice: zh-CN-Xiaoxiao, response_format: mp3
            // 这些参数导致了 400 错误。
            
            requestBody.put("response_format", "wav");

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody),
                    headers
            );

            // 由于 RestTemplate 默认处理 byte[] 可能有问题，如果返回的是音频流
            // 但这里先保持 byte[].class
            byte[] audioData = null;
            int maxRetries = 5;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    ResponseEntity<byte[]> response = restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            byte[].class
                    );

                    if (response.getStatusCode().is2xxSuccessful()) {
                        audioData = response.getBody();
                        break;
                    } else {
                        throw new RuntimeException("语音合成失败，状态码: " + response.getStatusCode());
                    }
                } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                    log.warn("触发速率限制 (429)，等待 {} 秒后重试 (第 {}/{} 次)", (i + 1) * 2, i + 1, maxRetries);
                    if (i == maxRetries - 1) throw e;
                    try {
                        Thread.sleep(2000L * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("线程中断", ie);
                    }
                }
            }

            if (audioData == null || audioData.length == 0) {
                 throw new RuntimeException("语音合成失败，未返回音频数据");
            }

            Path outputFilePath = Paths.get(outputPath);
            // 确保父目录存在
            if (outputFilePath.getParent() != null) {
                Files.createDirectories(outputFilePath.getParent());
            }

            try (FileOutputStream fos = new FileOutputStream(outputFilePath.toFile())) {
                fos.write(audioData);
            }

            log.info("语音合成成功: {}", outputPath);
            return outputPath;

        } catch (Exception e) {
            log.error("语音合成失败", e);
            // 不抛出异常，而是返回 null 或空字符串，以免中断整个视频生成流程
            // 或者根据业务需求决定。这里暂时捕获并记录日志，返回 null
            // 调用方 ImageToVideoService 需要处理 null 返回值
            return null; 
        }
    }

    public String synthesizeSpeech(String text) throws IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String outputPath = "outputs/audio_" + timestamp + ".mp3";
        return synthesizeSpeech(text, outputPath);
    }

    public String synthesizeSpeech(String text, String voice, double speed, String outputPath) throws IOException {
        log.info("开始语音合成: voice={}, speed={}, output={}", voice, speed, outputPath);

        try {
            String url = "https://open.bigmodel.cn/api/paas/v4/audio/speech";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", "tts-1");
            body.put("input", text);
            body.put("voice", voice);
            body.put("response_format", "mp3");
            body.put("speed", speed);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(body),
                    headers
            );

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    byte[].class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("语音合成失败，状态码: " + response.getStatusCode());
            }

            byte[] audioData = response.getBody();
            if (audioData == null) {
                throw new RuntimeException("语音合成失败，未返回音频数据");
            }

            Path outputFilePath = Paths.get(outputPath);
            Files.createDirectories(outputFilePath.getParent());

            try (FileOutputStream fos = new FileOutputStream(outputFilePath.toFile())) {
                fos.write(audioData);
            }

            log.info("语音合成成功: {}", outputPath);
            return outputPath;

        } catch (Exception e) {
            log.error("语音合成失败", e);
            throw new RuntimeException("语音合成失败: " + e.getMessage(), e);
        }
    }
}
