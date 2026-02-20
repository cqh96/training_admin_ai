package com.training.ai.interfaces.controller;

import com.training.ai.application.service.PptService;
import com.training.ai.application.service.PptToVideoService;
import com.training.ai.application.util.PptUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ppt")
@RequiredArgsConstructor
public class PptController {

    private final PptService pptService;
    private final PptToVideoService pptToVideoService;

    @PostMapping("/upload")
    public ResponseEntity<List<String>> uploadPpt(@RequestParam("file") MultipartFile file) {
        log.info("收到PPT上传请求: {}", file.getOriginalFilename());
        try {
            List<String> imagePaths = pptService.convertPptToImages(file);
            return ResponseEntity.ok(imagePaths);
        } catch (IOException e) {
            log.error("PPT转换失败", e);
            return ResponseEntity.internalServerError().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/to-video")
    public ResponseEntity<org.springframework.core.io.Resource> pptToVideo(@RequestParam("file") MultipartFile file) {
        log.info("收到PPT转视频请求: {}", file.getOriginalFilename());
        try {
            String videoPath = pptToVideoService.generateVideoFromPpt(file);
            java.io.File videoFile = new java.io.File(videoPath);
            
            if (!videoFile.exists()) {
                return ResponseEntity.internalServerError().body(new ByteArrayResource(("视频生成失败: 文件不存在 " + videoPath).getBytes()));
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(videoFile);
            
            String originalFilename = file.getOriginalFilename();
            String videoFilename = (originalFilename != null ? originalFilename.substring(0, originalFilename.lastIndexOf(".")) : "video") + ".mp4";
            String encodedFilename = URLEncoder.encode(videoFilename, StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"")
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .contentLength(videoFile.length())
                    .body(resource);
                    
        } catch (IOException e) {
            log.error("PPT转视频失败", e);
            return ResponseEntity.internalServerError().body(new ByteArrayResource(("视频生成失败: " + e.getMessage()).getBytes()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ByteArrayResource(("请求参数错误: " + e.getMessage()).getBytes()));
        } catch (Exception e) {
            log.error("PPT转视频发生未知错误", e);
            return ResponseEntity.internalServerError().body(new ByteArrayResource(("视频生成失败: " + e.getMessage()).getBytes()));
        }
    }

    @PostMapping("/to-pdf")
    public ResponseEntity<String> pptToPdf(@RequestParam("file") MultipartFile file) {
        log.info("收到PPT转PDF请求: {}", file.getOriginalFilename());
        try {
            String pdfPath = pptService.convertToPdf(file);
            return ResponseEntity.ok(pdfPath);
        } catch (IOException e) {
            log.error("PPT转PDF失败", e);
            return ResponseEntity.internalServerError().body("PDF生成失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("请求参数错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("PPT转PDF发生未知错误", e);
            return ResponseEntity.internalServerError().body("PDF生成失败: " + e.getMessage());
        }
    }

    /**
     * 接口1：前端上传PPT/PPTX，直接返回PDF文件下载
     * 示例：Post请求 /api/ppt2pdf/upload
     */
    @PostMapping("/upload2")
    public ResponseEntity<byte[]> uploadAndConvert(@RequestParam("file") MultipartFile file,
                                                   HttpServletRequest request) throws Exception {
        // 调用工具类转换
        byte[] pdfBytes = PptUtil.convert(file);
        // 构造下载响应头
        String originalFilename = file.getOriginalFilename();
        assert originalFilename != null;
        String pdfFileName = originalFilename.substring(0, originalFilename.lastIndexOf(".")) + ".pdf";
        // 解决中文文件名乱码
        String encodeFileName = URLEncoder.encode(pdfFileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", encodeFileName);
        headers.setContentLength(pdfBytes.length);
        // 返回PDF字节流
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/ppt-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ByteArrayResource> convertPptToPdf(@RequestParam("file") MultipartFile file) {
        try {
            // 校验文件类型（可选）
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || (!originalFilename.endsWith(".ppt") && !originalFilename.endsWith(".pptx"))) {
                throw new IllegalArgumentException("仅支持 PowerPoint 文件（.ppt 或 .pptx）");
            }

            byte[] pdfBytes = pptService.convertPptToPdf(file);

            ByteArrayResource resource = new ByteArrayResource(pdfBytes);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalFilename.replaceAll("\\.[^.]*$", "") + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfBytes.length)
                    .body(resource);
        } catch (Exception e) {
            // 生产环境请使用更细致的异常处理
            throw new RuntimeException("转换失败：" + e.getMessage(), e);
        }
    }
}
