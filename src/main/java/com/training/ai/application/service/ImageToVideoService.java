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
             // 降低 CRF 值，平衡画质和速度
             // ultrafast 模式下，CRF 可以稍微调大一点点以减小文件体积，或者保持 23 -> 改为 25 以提升速度
             recorder.setVideoOption("crf", "25");
             
             // 使用更快的预设以大幅缩短编码时间
             // 从 fast 改为 ultrafast：牺牲少量压缩率换取最快的编码速度
             recorder.setVideoOption("preset", "ultrafast");
             
             // 启用多线程编码
             // 设置为 0 表示自动使用所有可用的 CPU 核心
             recorder.setVideoOption("threads", "0");

            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.setSampleRate(44100);
            
            // 启动录制器
            recorder.start();
            
            // 记录当前录制的总时长（微秒），用于保证时间戳连续
            long currentRecorderTimestamp = 0;
            
            // 重用转换器，避免每次循环都创建
            org.bytedeco.javacv.Java2DFrameConverter converter = new org.bytedeco.javacv.Java2DFrameConverter();

            for (ImageSlide slide : slides) {
                log.info("处理图片幻灯片: {}", slide.getImagePath());
                
                // 计算该幻灯片的总帧数
                // 30 fps
                int totalFrames = (int) (slide.getDuration() * 30);
                long frameDurationUs = 1000000L / 30;
                
                // 处理图片部分
                try (FFmpegFrameGrabber slideImageGrabber = new FFmpegFrameGrabber(slide.getImagePath())) {
                    slideImageGrabber.start();
                    
                    // 获取图片帧
                    Frame imageFrame = slideImageGrabber.grabImage();
                    if (imageFrame == null) {
                        log.error("无法读取图片: {}", slide.getImagePath());
                        continue;
                    }
                    
                    // 图片处理逻辑：缩放、填充背景等
                    // 移除循环内重复创建 converter
                    // org.bytedeco.javacv.Java2DFrameConverter converter = new org.bytedeco.javacv.Java2DFrameConverter();
                    java.awt.image.BufferedImage bufferedImage = converter.getBufferedImage(imageFrame);
                    
                    if (bufferedImage != null) {
                        // 创建一个新的 BufferedImage，尺寸与视频一致，类型为 BGR (兼容性好)
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
                        
                        // 如果尺寸完全一致，居中绘制
                        if (imgW <= width && imgH <= height && 
                            Math.abs(width - imgW) <= 2 && Math.abs(height - imgH) <= 2) {
                            int x = (width - imgW) / 2;
                            int y = (height - imgH) / 2;
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
                        imageFrame = converter.convert(normalizedImage);
                    }
                    
                    // 预处理音频：将音频完全读取到内存或按需读取
                    FFmpegFrameGrabber audioGrabber = null;
                    if (slide.getAudioPath() != null && new File(slide.getAudioPath()).exists()) {
                        log.info("处理音频: {}", slide.getAudioPath());
                        audioGrabber = new FFmpegFrameGrabber(slide.getAudioPath());
                        audioGrabber.start();
                    }
                    
                    // 优化：不逐帧写入视频图像，而是使用 record(Frame, PixelFormat) 的变体或者直接调整时间戳
                    // 对于静态图片，FFmpeg 支持 "VFR" (Variable Frame Rate)，但为了兼容性，通常还是固定帧率
                    // 关键优化：不要在每一帧都调用 recorder.record(imageFrame) 进行完整的编码操作
                    // 如果图像内容没有变化，可以使用 recorder.record(imageFrame) 但其实非常浪费
                    // 更好的方法是：对于静态图片，我们只在关键帧或者每秒写入一次，其他时候让播放器保持上一帧？
                    // 不，MP4 还是需要连续的帧。
                    // 但是！我们可以显著减少 Java <-> Native 的调用开销。
                    
                    // 真正的优化：批量写入。但是 FFmpegFrameRecorder 没有批量 API。
                    
                    // 另一个优化点：音频和视频分开写入？不，必须交错。
                    
                    // 循环写入当前幻灯片的每一帧
                    for (int i = 0; i < totalFrames; i++) {
                        // 1. 写入视频帧
                        // 计算这一帧在整个视频中的绝对时间戳
                        long frameTimestamp = currentRecorderTimestamp + (i * frameDurationUs);
                        recorder.setTimestamp(frameTimestamp);
                        
                        // 优化：对于静态图片，其实不需要每一帧都重新编码
                        // 但 MP4 标准需要连续的帧。
                        // 如果我们使用 VFR (Variable Frame Rate)，可以只写入关键帧和变化帧。
                        // 但为了兼容性，我们保持 CFR (Constant Frame Rate)。
                        // 
                        // 在 ultrafast 模式下，对于完全相同的帧，x264 编码器会非常高效地处理（P-skip）。
                        // 所以这里的性能瓶颈主要在于 record() 调用的开销和内存拷贝。
                        // 
                        // 如果 slide 持续时间很长（例如 10秒 = 300帧），我们可以尝试减少 recorder.record(imageFrame) 的频率？
                        // 不行，那样会导致视频变短或者帧率不稳定。
                        
                        recorder.record(imageFrame);
                        
                        // 2. 写入音频帧
                        // 只有当有音频时才处理
                        if (audioGrabber != null) {
                            // 尝试读取一帧或多帧音频，直到填满当前视频帧的时间间隙
                            Frame audioFrame;
                            while ((audioFrame = audioGrabber.grabSamples()) != null) {
                                // 重新设置音频帧的时间戳
                                long audioAbsTimestamp = currentRecorderTimestamp + audioFrame.timestamp;
                                recorder.setTimestamp(audioAbsTimestamp);
                                recorder.record(audioFrame);
                                
                                if (audioFrame.timestamp >= (i + 1) * frameDurationUs) {
                                    break;
                                }
                            }
                        }
                    }
                    
                    // 更新全局时间戳，准备处理下一张幻灯片
                    currentRecorderTimestamp += (totalFrames * frameDurationUs);
                    
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
