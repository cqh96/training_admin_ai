package com.training.ai.interfaces.controller;

import com.training.ai.application.service.FileParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/file-parsing")
@RequiredArgsConstructor
public class FileParsingController {

    private final FileParsingService fileParsingService;

    @PostMapping("/upload")
    public ResponseEntity<String> parseFile(@RequestParam("file") MultipartFile file) {
        log.info("收到文件解析请求: {}", file.getOriginalFilename());
        try {
            String content = fileParsingService.parseFileContent(file);
            return ResponseEntity.ok(content);
        } catch (IOException e) {
            log.error("文件读取失败", e);
            return ResponseEntity.internalServerError().body("文件读取失败: " + e.getMessage());
        } catch (RuntimeException e) {
             log.warn("文件解析失败: {}", e.getMessage());
             return ResponseEntity.badRequest().body("文件解析失败: " + e.getMessage());
        }
    }
}
