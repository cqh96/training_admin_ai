package com.training.ai.interfaces.controller;

import com.training.ai.application.service.ImageToVideoService;
import com.training.ai.application.service.ImageToVideoService.VideoCreationResult;
import com.training.ai.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/image-to-video")
@RequiredArgsConstructor
public class ImageToVideoController {

    private final ImageToVideoService imageToVideoService;

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> createVideo(
            @RequestParam("images") List<MultipartFile> imageFiles,
            @RequestParam(value = "duration", defaultValue = "3.0") double durationPerImage,
            @RequestParam(value = "outputName", defaultValue = "output_video.mp4") String outputName) {

        try {
            log.info("收到图片生成视频请求: 图片数量={}, 每张图片时长={}, 输出文件名={}", 
                    imageFiles.size(), durationPerImage, outputName);

            if (imageFiles.isEmpty()) {
                return Result.error("请至少上传一张图片");
            }

            if (imageFiles.size() > 20) {
                return Result.error("最多支持20张图片");
            }

            String outputPath = "outputs/" + outputName;
            VideoCreationResult result = imageToVideoService.createVideoFromImages(
                    imageFiles, 
                    durationPerImage, 
                    outputPath
            );

            Map<String, Object> response = new HashMap<>();
            response.put("videoPath", result.getVideoPath());
            response.put("totalDuration", result.getTotalDuration());
            response.put("slidesCount", result.getSlides().size());
            response.put("slides", result.getSlides());

            log.info("图片生成视频成功: {}", result.getVideoPath());
            return Result.success(response);

        } catch (Exception e) {
            log.error("图片生成视频失败", e);
            return Result.error("图片生成视频失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/create-simple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> createSimpleVideo(
            @RequestParam("images") List<MultipartFile> imageFiles,
            @RequestParam(value = "outputName", defaultValue = "simple_video.mp4") String outputName) {

        try {
            log.info("收到简单图片生成视频请求: 图片数量={}, 输出文件名={}", 
                    imageFiles.size(), outputName);

            if (imageFiles == null || imageFiles.isEmpty()) {
                return Result.error("请至少上传一张图片");
            }

            String outputPath = "outputs/" + outputName;
            VideoCreationResult result = imageToVideoService.createVideoFromImages(
                    imageFiles, 
                    outputPath
            );

            Map<String, Object> response = new HashMap<>();
            response.put("videoPath", result.getVideoPath());
            response.put("totalDuration", result.getTotalDuration());
            response.put("slidesCount", result.getSlides().size());

            log.info("简单图片生成视频成功: {}", result.getVideoPath());
            return Result.success(response);

        } catch (Exception e) {
            log.error("简单图片生成视频失败", e);
            return Result.error("简单图片生成视频失败: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public Result<Map<String, String>> getStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("service", "Image to Video Service");
        status.put("status", "running");
        status.put("description", "支持图片文字识别、语音合成和视频生成");
        status.put("features", List.of(
                "图片文字识别（OCR）",
                "语音合成（TTS）",
                "图片序列视频生成",
                "音频同步"
        ).toString());
        
        return Result.success(status);
    }
}
