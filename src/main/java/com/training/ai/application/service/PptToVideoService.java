package com.training.ai.application.service;

import com.training.ai.application.service.ImageToVideoService.ImageSlide;
import com.training.ai.application.service.PptService.PptPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PptToVideoService {

    private final PptService pptService;
    private final TtsService ttsService;
    private final ImageToVideoService imageToVideoService;

    private static final String TEMP_DIR = System.getProperty("user.dir") + File.separator + "temp" + File.separator + "ppt_video_temp";

    /**
     * 将 PPT 文件转换为视频
     * 1. PPT 转图片 & 提取文本
     * 2. 文本转语音 (TTS)
     * 3. 合成视频 (图片 + 语音)
     */
    public String generateVideoFromPpt(MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("开始处理 PPT 转视频: {}", file.getOriginalFilename());

        String taskId = UUID.randomUUID().toString();
        File taskDir = new File(TEMP_DIR, taskId);
        if (!taskDir.exists()) {
            taskDir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        File pptFile = new File(taskDir, "source" + extension);
        file.transferTo(pptFile);

        try {
            // 1. PPT 转 PDF
            File pdfFile = new File(taskDir, "converted.pdf");
            pptService.convertPptToPdf(pptFile, pdfFile);
            log.info("PPT 转 PDF 完成: {}", pdfFile.getAbsolutePath());

            // 2. 从 PPT 提取文本内容
            List<String> textContents = extractTextFromPpt(pptFile);
            log.info("提取文本内容完成，共 {} 页", textContents.size());

            // 3. PDF 转图片
            List<PptPage> pages = pptService.convertPdfToPages(pdfFile, taskDir, textContents);
            if (pages.isEmpty()) {
                throw new IllegalArgumentException("PDF 转图片失败");
            }
            log.info("PDF 转图片完成，共 {} 页", pages.size());

            // 4. 准备临时目录存放音频
            File audioDir = new File(TEMP_DIR, UUID.randomUUID().toString());
            if (!audioDir.exists()) {
                audioDir.mkdirs();
            }

            // 5. 为每页生成语音并构建 ImageSlide (并行处理)
            log.info("开始并行处理 TTS 生成 (并发数: 5)...");
            List<ImageSlide> slides;
            java.util.concurrent.ForkJoinPool customThreadPool = new java.util.concurrent.ForkJoinPool(5);
            try {
                slides = customThreadPool.submit(() ->
                        pages.parallelStream()
                                .map(page -> processPage(page, audioDir))
                                .flatMap(List::stream)
                                .collect(java.util.stream.Collectors.toList())
                ).get();
            } catch (Exception e) {
                log.error("并发处理 TTS 失败", e);
                throw new RuntimeException("TTS 生成失败", e);
            } finally {
                customThreadPool.shutdown();
            }

            // 6. 生成视频
            String videoFilename = "video_" + System.currentTimeMillis() + ".mp4";
            String videoOutputPath = new File(audioDir, videoFilename).getAbsolutePath();

            log.info("开始合成视频: {}", videoOutputPath);
            String result = imageToVideoService.createVideoWithAudio(slides, videoOutputPath);

            // 7. 清理临时 PDF 文件
            if (pdfFile.exists()) {
                pdfFile.delete();
            }
            
            long endTime = System.currentTimeMillis();
            long durationMs = endTime - startTime;
            log.info("PPT 转视频处理完成: {}, 总耗时: {} ms (约 {} 秒)",
                    file.getOriginalFilename(), durationMs, String.format("%.2f", durationMs / 1000.0));

            return result;
        } finally {
            if (pptFile.exists()) {
                pptFile.delete();
            }
        }
    }

    /**
     * 处理单页 PPT：生成 TTS 语音并构建 ImageSlide 列表（支持长文本分段）
     */
    private List<ImageSlide> processPage(PptPage page, File audioDir) {
        List<ImageSlide> slides = new ArrayList<>();
        String text = page.getTextContent();
        
        // 如果文本长度超过限制，进行分段处理
        if (StringUtils.hasText(text) && text.length() > 900) {
            log.info("第 {} 页文本过长 ({} 字符)，进行分段处理", page.getPageIndex(), text.length());
            List<String> textSegments = splitText(text, 900);
            
            for (int i = 0; i < textSegments.size(); i++) {
                String segment = textSegments.get(i);
                String audioPath = null;
                double duration = 3.0;
                
                String audioFilename = "audio_" + page.getPageIndex() + "_part_" + (i + 1) + ".wav";
                String targetAudioPath = new File(audioDir, audioFilename).getAbsolutePath();
                
                try {
                    audioPath = ttsService.synthesizeSpeech(segment, targetAudioPath);
                    if (audioPath != null) {
                        double audioDuration = getAudioDuration(audioPath);
                        if (audioDuration > 0) {
                            duration = audioDuration;
                            // 仅在最后一段添加缓冲时间
                            if (i == textSegments.size() - 1) {
                                duration += 0.5;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("第 {} 页第 {} 段 TTS 生成失败", page.getPageIndex(), i + 1, e);
                }
                
                slides.add(ImageSlide.builder()
                        .imagePath(page.getImagePath())
                        .text(segment)
                        .audioPath(audioPath)
                        .duration(duration)
                        .build());
            }
        } else {
            // 文本未超长，正常处理
            String audioPath = null;
            double duration = 3.0; // 默认时长 3 秒

            if (StringUtils.hasText(text)) {
                // 生成音频文件路径
                String audioFilename = "audio_" + page.getPageIndex() + ".wav";
                String targetAudioPath = new File(audioDir, audioFilename).getAbsolutePath();
                
                try {
                    // 调用 TTS 生成语音
                    audioPath = ttsService.synthesizeSpeech(text, targetAudioPath);
                    
                    if (audioPath != null) {
                        // 获取音频时长
                        double audioDuration = getAudioDuration(audioPath);
                        if (audioDuration > 0) {
                            duration = audioDuration; // 使用音频时长
                            // 稍微增加一点缓冲时间 (0.5s) 让视频更自然
                            duration += 0.5;
                        }
                    }
                } catch (Exception e) {
                    log.error("第 {} 页 TTS 生成失败，使用默认时长", page.getPageIndex(), e);
                }
            } else {
                log.info("第 {} 页无文本内容，使用默认时长 {}s", page.getPageIndex(), duration);
            }

            slides.add(ImageSlide.builder()
                    .imagePath(page.getImagePath())
                    .text(text)
                    .audioPath(audioPath)
                    .duration(duration)
                    .build());
        }
        return slides;
    }

    /**
     * 将长文本按标点符号分割成小段
     */
    private List<String> splitText(String text, int maxLength) {
        List<String> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        while (text.length() > maxLength) {
            // 寻找最佳分割点：优先句号，其次逗号，最后空格
            int splitIndex = -1;
            
            // 在 [maxLength/2, maxLength] 范围内查找标点符号
            // 这样避免每句话都太短，也避免切断句子
            int searchEnd = maxLength;
            int searchStart = maxLength / 2;
            
            // 截取待搜索的子串
            String sub = text.substring(0, searchEnd);
            
            // 优先级 1: 句号/问号/感叹号
            int p1 = Math.max(sub.lastIndexOf("。"), Math.max(sub.lastIndexOf("？"), sub.lastIndexOf("！")));
            if (p1 == -1) {
                p1 = Math.max(sub.lastIndexOf("."), Math.max(sub.lastIndexOf("?"), sub.lastIndexOf("!")));
            }
            if (p1 > searchStart) splitIndex = p1;
            
            // 优先级 2: 逗号/分号
            if (splitIndex == -1) {
                int p2 = Math.max(sub.lastIndexOf("，"), sub.lastIndexOf("；"));
                if (p2 == -1) {
                    p2 = Math.max(sub.lastIndexOf(","), sub.lastIndexOf(";"));
                }
                if (p2 > searchStart) splitIndex = p2;
            }
            
            // 优先级 3: 空格/换行
            if (splitIndex == -1) {
                int p3 = Math.max(sub.lastIndexOf(" "), sub.lastIndexOf("\n"));
                if (p3 > searchStart) splitIndex = p3;
            }
            
            // 如果实在找不到合适的分割点，就强制分割
            if (splitIndex == -1) {
                splitIndex = maxLength;
            } else {
                // 包含标点符号
                splitIndex += 1; 
            }
            
            segments.add(text.substring(0, splitIndex));
            text = text.substring(splitIndex);
        }
        
        if (StringUtils.hasText(text)) {
            segments.add(text);
        }
        
        return segments;
    }

    private List<String> extractTextFromPpt(File file) throws IOException {
        List<String> textContents = new ArrayList<>();

        String originalFilename = file.getName();
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();

        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            if (".pptx".equals(extension)) {
                try (org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(fis)) {
                    for (org.apache.poi.xslf.usermodel.XSLFSlide slide : ppt.getSlides()) {
                        StringBuilder text = new StringBuilder();
                        for (org.apache.poi.xslf.usermodel.XSLFShape shape : slide.getShapes()) {
                            if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape) {
                                org.apache.poi.xslf.usermodel.XSLFTextShape textShape = (org.apache.poi.xslf.usermodel.XSLFTextShape) shape;
                                text.append(textShape.getText()).append(" ");
                            }
                        }
                        textContents.add(text.toString().trim());
                    }
                }
            } else if (".ppt".equals(extension)) {
                try (org.apache.poi.hslf.usermodel.HSLFSlideShow ppt = new org.apache.poi.hslf.usermodel.HSLFSlideShow(fis)) {
                    for (org.apache.poi.hslf.usermodel.HSLFSlide slide : ppt.getSlides()) {
                        StringBuilder text = new StringBuilder();
                        for (org.apache.poi.sl.usermodel.Shape shape : slide.getShapes()) {
                            if (shape instanceof org.apache.poi.hslf.usermodel.HSLFTextShape) {
                                org.apache.poi.hslf.usermodel.HSLFTextShape textShape = (org.apache.poi.hslf.usermodel.HSLFTextShape) shape;
                                text.append(textShape.getText()).append(" ");
                            }
                        }
                        textContents.add(text.toString().trim());
                    }
                }
            } else {
                throw new IllegalArgumentException("不支持的文件格式: " + extension);
            }
        } catch (Exception e) {
            log.error("提取 PPT 文本失败", e);
            throw new IOException("提取 PPT 文本失败", e);
        }

        return textContents;
    }

    private double getAudioDuration(String audioPath) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(audioPath)) {
            grabber.start();
            long durationMicroseconds = grabber.getLengthInTime();
            grabber.stop();
            return durationMicroseconds / 1_000_000.0;
        } catch (Exception e) {
            log.error("获取音频时长失败: {}", audioPath, e);
            return 0;
        }
    }
}
