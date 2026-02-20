package com.training.ai.application.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageToVideoService {

    private final OcrService ocrService;
    private final TtsService ttsService;
    private final FFmpegService ffmpegService;

    public VideoCreationResult createVideoFromImages(
            List<MultipartFile> imageFiles,
            double durationPerImage,
            String outputPath) throws IOException {

        log.info("开始从图片创建视频: 图片数量={}, 每张图片时长={}, 输出路径={}", 
                imageFiles.size(), durationPerImage, outputPath);

        List<ImageSlide> slides = new ArrayList<>();
        
        // 确保输出目录存在
        Path outputDir = Paths.get(outputPath).getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        
        // 使用绝对路径的临时目录
        String tempDir = System.getProperty("java.io.tmpdir");
        Path tempDirPath = Paths.get(tempDir, "training-admin-ai-uploads");
        if (!Files.exists(tempDirPath)) {
            Files.createDirectories(tempDirPath);
        }

        for (int i = 0; i < imageFiles.size(); i++) {
            MultipartFile imageFile = imageFiles.get(i);
            String originalFilename = imageFile.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                    : ".jpg";
            
            Path tempImageFile = Files.createTempFile(tempDirPath, "upload_" + System.currentTimeMillis() + "_" + i, extension);
            String tempImagePath = tempImageFile.toAbsolutePath().toString();
            
            log.info("保存临时图片: {}", tempImagePath);
            imageFile.transferTo(tempImageFile.toFile());

            log.info("处理第{}张图片: {}", i + 1, originalFilename);

            // OCR 处理逻辑（暂时模拟）
            String recognizedText = "模拟识别文本"; 
            // String recognizedText = ocrService.recognizeText(tempImagePath);
            log.info("第{}张图片识别的文本: {}", i + 1, recognizedText);

            String audioPath = null;
            // TTS 处理逻辑（暂时模拟）
            Path tempAudioFile = Files.createTempFile(tempDirPath, "tts_" + System.currentTimeMillis() + "_" + i, ".mp3");
            String tempAudioPath = tempAudioFile.toAbsolutePath().toString();
            audioPath = ttsService.synthesizeSpeech(recognizedText, tempAudioPath);
            log.info("第{}张图片的语音已生成: {}", i + 1, audioPath);


            ImageSlide slide = ImageSlide.builder()
                    .imagePath(tempImagePath)
                    .text(recognizedText)
                    .audioPath(audioPath)
                    .duration(durationPerImage)
                    .build();

            slides.add(slide);
        }

        // 创建视频
        // 注意：这里需要根据实际情况调用 ffmpegService 或内部实现
        // 暂时假设 createVideoWithAudio 方法可用且能处理绝对路径
         String videoPath = createVideoWithAudio(slides, outputPath);
        
        // 模拟视频生成成功
        log.info("视频创建模拟完成: {}", outputPath);

        return VideoCreationResult.builder()
                .videoPath(outputPath)
                .slides(slides)
                .totalDuration(slides.size() * durationPerImage)
                .build();
    }

    public String createVideoWithAudio(List<ImageSlide> slides, String outputPath) throws IOException {
        log.info("开始创建带音频的视频，输出路径: {}", outputPath);

        if (slides.isEmpty()) {
            throw new IllegalArgumentException("至少需要一张图片");
        }

        ImageSlide firstSlide = slides.getFirst();
        int width;
        int height;

        // 获取第一张图片的尺寸作为视频尺寸
        try (FFmpegFrameGrabber imageGrabber = new FFmpegFrameGrabber(firstSlide.getImagePath())) {
            imageGrabber.start();
            width = imageGrabber.getImageWidth();
            height = imageGrabber.getImageHeight();
            imageGrabber.stop();
        }

        // 不限制分辨率，直接使用原始图片尺寸，保持原始画质
        // 确保宽高是偶数，满足 H.264 编码要求
        // 策略：向上取偶（Pad），而不是向下裁剪，以免丢失边缘像素
        if (width % 2 != 0) {
            width++;
        }
        if (height % 2 != 0) {
            height++;
        }
        log.info("视频尺寸（原始图片尺寸-偶数对齐）: {}x{}", width, height);

        // 初始化视频录制器
        // 这里的 audioChannels 设置为 1 (单声道)
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height, 1)) {

            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setFrameRate(30);
            
            // 根据分辨率动态计算比特率，确保原始画质
            // 移除固定比特率设置，使用 CRF 模式自适应控制
            // 对于静态 PPT 幻灯片，固定高码率（如 50Mbps）非常浪费且会导致编码缓慢
            // int pixelCount = width * height;
            // int targetBitrate = (int) (pixelCount * 0.2 * 30 / 1000000) * 1000000;
            // if (targetBitrate < 10000000) targetBitrate = 10000000;
            // if (targetBitrate > 80000000) targetBitrate = 80000000;
            // recorder.setVideoBitrate(targetBitrate);
            
            // 关键：设置像素格式，避免 swscaler 警告和兼容性问题
            recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
            
            // 设置 GOP 大小为 30，即每秒一个关键帧，改善 seek 性能
            recorder.setGopSize(30);
            
            // 使用 High Profile 以获得更好的压缩效率和质量
            recorder.setVideoOption("profile", "high");
            
            // 设置 CRF (Constant Rate Factor) 控制质量
             // 范围 0-51，数值越小质量越好，文件越大。18-28 是常用范围
             // 从 18 改为 23 (默认)：2.5K 分辨率下，CRF 23 依然能保持非常高的画质，且文件更小编码更快
             recorder.setVideoOption("crf", "23");
             
             // 使用更快的预设以大幅缩短编码时间
             // 从 medium 改为 fast：在保持良好压缩率的同时显著提升编码速度
             recorder.setVideoOption("preset", "fast");
             
             // 启用多线程编码
             // 设置为 0 表示自动使用所有可用的 CPU 核心
             recorder.setVideoOption("threads", "0");
             
             // 尝试强制使用 High 4:4:4 Predictive Profile (如果兼容性允许)
             // 这能保留完整的色度信息，解决文字边缘模糊问题
             // 如果播放器不支持，可能需要回退到 high profile (4:2:0)
             // 但为了极致画质，先尝试 high444
             // recorder.setVideoOption("profile", "high444"); 
             // 考虑到兼容性，还是使用 high profile，但通过超高码率弥补
             recorder.setVideoOption("profile", "high");

            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.setSampleRate(44100);
            
            // 启动录制器
            recorder.start();
            
            // 创建图像缩放/转换器（如果图片尺寸与视频尺寸不一致）
            // 我们在循环中针对每张图片处理

            for (ImageSlide slide : slides) {
                log.info("处理图片幻灯片: {}", slide.getImagePath());
                
                // 计算该幻灯片的总帧数
                // 30 fps
                int totalFrames = (int) (slide.getDuration() * 30);
                long frameDurationUs = 1000000L / 30;
                
                try (FFmpegFrameGrabber slideImageGrabber = new FFmpegFrameGrabber(slide.getImagePath())) {
                    slideImageGrabber.start();
                    
                    // 获取图片帧
                    Frame imageFrame = slideImageGrabber.grabImage();
                    if (imageFrame == null) {
                        log.error("无法读取图片: {}", slide.getImagePath());
                        continue;
                    }
                    
                    // 如果图片尺寸与视频尺寸不一致，需要缩放
                    // 或者为了避免 Stride/Padding 问题，始终进行规范化处理
                    org.bytedeco.javacv.Java2DFrameConverter converter = new org.bytedeco.javacv.Java2DFrameConverter();
                    java.awt.image.BufferedImage bufferedImage = converter.getBufferedImage(imageFrame);
                    
                    if (bufferedImage != null) {
                        // 创建一个新的 BufferedImage，尺寸与视频一致，类型为 BGR (兼容性好)
                        // 这一步确保了：
                        // 1. 尺寸严格匹配视频宽高
                        // 2. 图像数据布局标准化 (避免 Stride 问题导致的画面错位/截断)
                        // 3. 像素格式为 standard BGR
                        java.awt.image.BufferedImage normalizedImage = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_3BYTE_BGR);
                        java.awt.Graphics2D g2d = normalizedImage.createGraphics();
                        
                        // 设置高质量渲染提示
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                        
                        // 计算保持比例的缩放尺寸 (Fit Center)
                        int imgW = bufferedImage.getWidth();
                        int imgH = bufferedImage.getHeight();
                        
                        // 1. 填充黑色背景
                        g2d.setColor(java.awt.Color.BLACK);
                        g2d.fillRect(0, 0, width, height);
                        
                        // 2. 计算缩放比例和位置
                        double scaleX = (double) width / imgW;
                        double scaleY = (double) height / imgH;
                        
                        // 如果尺寸完全一致（即使之前因为奇偶调整了+1像素，这里也是填充背景后 1:1 绘制）
                        // 彻底消除缩放
                        if (imgW <= width && imgH <= height && 
                            Math.abs(width - imgW) <= 2 && Math.abs(height - imgH) <= 2) {
                            
                            // 居中绘制，不缩放
                            int x = (width - imgW) / 2;
                            int y = (height - imgH) / 2;
                            
                            // 使用最简单的绘制方法，避免任何插值
                            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                            g2d.drawImage(bufferedImage, x, y, null);
                        } else {
                            // 保持长宽比缩放
                            double scale = Math.min(scaleX, scaleY);
                            int newW = (int) (imgW * scale);
                            int newH = (int) (imgH * scale);
                            int x = (width - newW) / 2;
                            int y = (height - newH) / 2;
                            g2d.drawImage(bufferedImage, x, y, newW, newH, null);
                        }
                        
                        g2d.dispose();
                        
                        // 替换原始帧
                        imageFrame = converter.convert(normalizedImage);
                    }

                    // 准备音频抓取器
                    FFmpegFrameGrabber audioGrabber = null;
                    if (slide.getAudioPath() != null && new File(slide.getAudioPath()).exists()) {
                        log.info("处理音频: {}", slide.getAudioPath());
                        audioGrabber = new FFmpegFrameGrabber(slide.getAudioPath());
                        audioGrabber.start();
                    }

                    // 获取当前视频流的起始时间戳 (微秒)
                    long startTimestamp = recorder.getTimestamp();
                    
                    // 优化：设置音频同步间隔，避免逐帧处理音频带来的上下文切换开销
                    // 每 15 帧 (约 0.5 秒) 同步一次音频，实现批量写入
                    int audioSyncInterval = 15; 

                    // 循环写入视频帧
                    for (int i = 0; i < totalFrames; i++) {
                        // 计算当前帧的理论时间戳
                        long currentFrameTimestamp = startTimestamp + (i * frameDurationUs);
                        
                        // 1. 显式设置 recorder 时间戳，确保视频帧时间连续
                        recorder.setTimestamp(currentFrameTimestamp);
                        recorder.record(imageFrame);
                        
                        // 2. 写入音频帧 (批量交错写入)
                        // 仅在特定间隔 或 最后一帧 时处理音频
                        if (audioGrabber != null && (i % audioSyncInterval == 0 || i == totalFrames - 1)) {
                            Frame audioFrame;
                            // 设定本次同步的目标时间戳：当前时间 + 同步间隔
                            // 允许音频稍微超前一点，以保持缓冲区充盈
                            long syncTargetTimestamp = currentFrameTimestamp + (audioSyncInterval * frameDurationUs);
                            
                            // 持续读取音频，直到追上目标时间戳
                            while ((audioFrame = audioGrabber.grabSamples()) != null) {
                                // 重要：音频帧的时间戳是相对于该音频文件开头的
                                // 必须加上当前幻灯片的起始时间戳，才能对齐到整个视频的时间轴
                                long adjustedAudioTimestamp = startTimestamp + audioFrame.timestamp;
                                audioFrame.timestamp = adjustedAudioTimestamp;
                                
                                recorder.record(audioFrame);
                                
                                // 如果当前音频帧的时间已经赶上了同步目标，暂停读取
                                if (adjustedAudioTimestamp >= syncTargetTimestamp) {
                                    break;
                                }
                            }
                        }
                    }

                    if (audioGrabber != null) {
                        try {
                            audioGrabber.stop();
                            audioGrabber.release();
                        } catch (Exception e) {
                            log.warn("关闭音频抓取器失败", e);
                        }
                    }
                } catch (Exception e) {
                    log.error("处理幻灯片失败: {}", slide.getImagePath(), e);
                }
            }

            recorder.stop();
            recorder.release();
            log.info("视频录制完成");

        } catch (Exception e) {
            log.error("创建视频失败", e);
            throw new RuntimeException("创建视频失败: " + e.getMessage(), e);
        }
        
        // 清理临时文件
        for (ImageSlide slide : slides) {
             cleanupTempFile(slide.getImagePath());
             if (slide.getAudioPath() != null) {
                 cleanupTempFile(slide.getAudioPath());
             }
        }

        return outputPath;
    }

    private void cleanupTempFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
                log.debug("删除临时文件: {}", filePath);
            }
        } catch (Exception e) {
            log.warn("删除临时文件失败: {}", filePath, e);
        }
    }

    @Data
    @Builder
    public static class ImageSlide {
        private String imagePath;
        private String text;
        private String audioPath;
        private double duration;
    }

    @Data
    @Builder
    public static class VideoCreationResult {
        private String videoPath;
        private List<ImageSlide> slides;
        private double totalDuration;
    }

    public VideoCreationResult createVideoFromImages(
            List<MultipartFile> imageFiles,
            String outputPath) throws IOException {
        
        return createVideoFromImages(imageFiles, 3.0, outputPath);
    }
}
