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

    static {
        // 允许处理包含大量内部文件的 PPTX 文件
        // 避免 "The file appears to be potentially malicious" 错误
        try {
            org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(0);
            // 设置为 Integer.MAX_VALUE 而不是 -1，因为 -1 可能导致比较逻辑 (count > max) 总是为真
            org.apache.poi.openxml4j.util.ZipSecureFile.setMaxFileCount(Integer.MAX_VALUE);
        } catch (Throwable e) {
            log.warn("配置 POI ZipSecureFile 失败", e);
        }
    }

    /**
     * 将 PPT 文件转换为视频 (同步方法，保留向后兼容)
     */
    public String generateVideoFromPpt(MultipartFile file) throws IOException {
        return generateVideoFromPpt(file, (percent, msg) -> log.info("进度 {}: {}", percent, msg));
    }

    /**
     * 将 PPT 文件转换为视频 (支持进度回调)
     * 1. PPT 转图片 & 提取文本
     * 2. 文本转语音 (TTS)
     * 3. 合成视频 (图片 + 语音)
     */
    public String generateVideoFromPpt(MultipartFile file, java.util.function.BiConsumer<Integer, String> progressCallback) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        
        String taskId = UUID.randomUUID().toString();
        File taskDir = new File(TEMP_DIR, taskId);
        if (!taskDir.exists()) {
            taskDir.mkdirs();
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        File pptFile = new File(taskDir, "source" + extension);
        file.transferTo(pptFile);

        return processPptFile(pptFile, originalFilename, progressCallback, taskId);
    }

    /**
     * 核心处理逻辑，接受已存在的本地文件
     */
    public String generateVideoFromPptFile(File pptFile, String originalFilename, java.util.function.BiConsumer<Integer, String> progressCallback) throws IOException {
        String taskId = UUID.randomUUID().toString();
        // 如果文件不在我们的临时目录结构中，可能需要复制，或者直接使用
        // 这里假设调用者已经准备好了文件，或者我们只需要读取它
        // 为了复用逻辑，我们还是创建一个任务目录
        File taskDir = new File(TEMP_DIR, taskId);
        if (!taskDir.exists()) {
            taskDir.mkdirs();
        }
        
        // 复制源文件到任务目录 (避免修改源文件)
        String extension = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase() : ".pptx";
        File targetPptFile = new File(taskDir, "source" + extension);
        Files.copy(pptFile.toPath(), targetPptFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        return processPptFile(targetPptFile, originalFilename, progressCallback, taskId);
    }

    private String processPptFile(File pptFile, String originalFilename, java.util.function.BiConsumer<Integer, String> progressCallback, String taskId) throws IOException {
        long startTime = System.currentTimeMillis();
        progressCallback.accept(0, "开始处理 PPT 转视频: " + originalFilename);
        
        File taskDir = pptFile.getParentFile(); // 假设 pptFile 已经在任务目录中

        try {
            // 1. PPT 转 PDF
            progressCallback.accept(10, "正在将 PPT 转换为 PDF...");
            File pdfFile = new File(taskDir, "converted.pdf");
            pptService.convertPptToPdf(pptFile, pdfFile);
            progressCallback.accept(30, "PPT 转 PDF 完成");

            // 2. 从 PPT 提取文本内容
            progressCallback.accept(35, "正在提取 PPT 文本内容...");
            List<String> textContents = extractTextFromPpt(pptFile);
            progressCallback.accept(40, "提取文本内容完成，共 " + textContents.size() + " 页");

            // 3. PDF 转图片
            progressCallback.accept(45, "正在将 PDF 转换为高清图片...");
            List<PptPage> pages = pptService.convertPdfToPages(pdfFile, taskDir, textContents);
            if (pages.isEmpty()) {
                throw new IllegalArgumentException("PDF 转图片失败");
            }
            progressCallback.accept(60, "PDF 转图片完成，共 " + pages.size() + " 页");

            // 4. 准备临时目录存放音频
            File audioDir = new File(TEMP_DIR, UUID.randomUUID().toString());
            if (!audioDir.exists()) {
                audioDir.mkdirs();
            }

            // 5. 为每页生成语音并构建 ImageSlide (并行处理)
            progressCallback.accept(65, "正在生成语音合成 (TTS)...");
            List<ImageSlide> slides;
            java.util.concurrent.ForkJoinPool customThreadPool = new java.util.concurrent.ForkJoinPool(5);
            try {
                // 使用 AtomicInteger 追踪进度
                java.util.concurrent.atomic.AtomicInteger processedCount = new java.util.concurrent.atomic.AtomicInteger(0);
                int totalPages = pages.size();
                
                slides = customThreadPool.submit(() ->
                        pages.parallelStream()
                                .map(page -> {
                                    List<ImageSlide> pageSlides = processPage(page, audioDir);
                                    int current = processedCount.incrementAndGet();
                                    int percent = 65 + (int)((current / (double)totalPages) * 20); // 65% -> 85%
                                    progressCallback.accept(percent, "已处理第 " + page.getPageIndex() + " 页语音");
                                    return pageSlides;
                                })
                                .flatMap(List::stream)
                                .collect(java.util.stream.Collectors.toList())
                ).get();
            } catch (Exception e) {
                log.error("并发处理 TTS 失败", e);
                throw new RuntimeException("TTS 生成失败", e);
            } finally {
                customThreadPool.shutdown();
            }
            progressCallback.accept(85, "语音合成完成，准备合成视频...");

            // 6. 生成视频
            String videoFilename = "video_" + System.currentTimeMillis() + ".mp4";
            String videoOutputPath = new File(audioDir, videoFilename).getAbsolutePath();

            progressCallback.accept(90, "正在合成最终视频 (FFmpeg)...");
            String result = imageToVideoService.createVideoWithAudio(slides, videoOutputPath);

            // 7. 清理临时 PDF 文件
            if (pdfFile.exists()) {
                pdfFile.delete();
            }
            
            long endTime = System.currentTimeMillis();
            long durationMs = endTime - startTime;
            long seconds = durationMs / 1000;
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            long s = seconds % 60;
            String doneMsg = String.format("PPT 转视频处理完成，总耗时: %d小时%d分%d秒", h, m, s);
            progressCallback.accept(100, doneMsg);

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
