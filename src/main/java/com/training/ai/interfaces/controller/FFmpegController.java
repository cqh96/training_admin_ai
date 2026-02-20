package com.training.ai.interfaces.controller;

import com.training.ai.application.service.FFmpegService;
import com.training.ai.application.service.FFmpegService.VideoInfo;
import com.training.ai.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ffmpeg")
@RequiredArgsConstructor
public class FFmpegController {

    private final FFmpegService ffmpegService;
    private static final String UPLOAD_DIR = "uploads";
    private static final String OUTPUT_DIR = "outputs";

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> convertVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") String format) throws IOException {
        
        String inputPath = saveUploadedFile(file);
        String outputPath = OUTPUT_DIR + "/" + System.currentTimeMillis() + "." + format;
        
        String resultPath = ffmpegService.convertVideo(inputPath, outputPath, format);
        
        Map<String, String> response = new HashMap<>();
        response.put("inputPath", inputPath);
        response.put("outputPath", resultPath);
        
        return Result.success(response);
    }

    @PostMapping(value = "/extract-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> extractAudio(@RequestParam("file") MultipartFile file) throws IOException {
        
        String inputPath = saveUploadedFile(file);
        String outputPath = OUTPUT_DIR + "/" + System.currentTimeMillis() + ".mp3";
        
        String resultPath = ffmpegService.extractAudio(inputPath, outputPath);
        
        Map<String, String> response = new HashMap<>();
        response.put("inputPath", inputPath);
        response.put("outputPath", resultPath);
        
        return Result.success(response);
    }

    @PostMapping(value = "/compress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> compressVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "quality", defaultValue = "0.7") double quality) throws IOException {
        
        String inputPath = saveUploadedFile(file);
        String outputPath = OUTPUT_DIR + "/" + System.currentTimeMillis() + "_compressed.mp4";
        
        String resultPath = ffmpegService.compressVideo(inputPath, outputPath, quality);
        
        Map<String, String> response = new HashMap<>();
        response.put("inputPath", inputPath);
        response.put("outputPath", resultPath);
        response.put("quality", String.valueOf(quality));
        
        return Result.success(response);
    }

    @GetMapping("/info")
    public Result<VideoInfo> getVideoInfo(@RequestParam("filePath") String filePath) {
        VideoInfo info = ffmpegService.getVideoInfo(filePath);
        return Result.success(info);
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> validateVideo(@RequestParam("file") MultipartFile file) throws IOException {
        
        String inputPath = saveUploadedFile(file);
        boolean isValid = ffmpegService.validateVideoFile(inputPath);
        
        Map<String, Object> response = new HashMap<>();
        response.put("filePath", inputPath);
        response.put("valid", isValid);
        
        return Result.success(response);
    }

    @PostMapping(value = "/convert-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> convertAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") String format) throws IOException {
        
        String inputPath = saveUploadedFile(file);
        File audioFile = ffmpegService.convertToAudioFile(inputPath, format);
        
        Map<String, String> response = new HashMap<>();
        response.put("inputPath", inputPath);
        response.put("outputPath", audioFile.getAbsolutePath());
        response.put("format", format);
        
        return Result.success(response);
    }

    private String saveUploadedFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        file.transferTo(filePath.toFile());
        
        return filePath.toString();
    }
}
