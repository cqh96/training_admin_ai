package com.training.ai.application.service;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
public class FFmpegService {

    public FFmpegService() {
        FFmpegLogCallback.set();
    }

    public String convertVideo(String inputPath, String outputPath, String format) {
        log.info("开始转换视频: input={}, output={}, format={}", inputPath, outputPath, format);
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath);
             FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, 
                     grabber.getImageWidth(), grabber.getImageHeight(), 
                     grabber.getAudioChannels())) {
            
            grabber.start();
            
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat(format);
            recorder.setFrameRate(grabber.getFrameRate());
            recorder.setSampleRate(grabber.getSampleRate());
            
            recorder.start();
            
            while (grabber.grab() != null) {
                recorder.record(grabber.grab());
            }
            
            log.info("视频转换成功: {}", outputPath);
            return outputPath;
            
        } catch (Exception e) {
            log.error("视频转换失败", e);
            throw new RuntimeException("视频转换失败: " + e.getMessage(), e);
        }
    }

    public String extractAudio(String inputPath, String outputPath) {
        log.info("开始提取音频: input={}, output={}", inputPath, outputPath);
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath);
             FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, 0)) {
            
            grabber.start();
            
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.setSampleRate(grabber.getSampleRate());
            recorder.setAudioChannels(grabber.getAudioChannels());
            recorder.setFormat("mp3");
            
            recorder.start();
            
            while (grabber.grab() != null) {
                recorder.record(grabber.grab());
            }
            
            log.info("音频提取成功: {}", outputPath);
            return outputPath;
            
        } catch (Exception e) {
            log.error("音频提取失败", e);
            throw new RuntimeException("音频提取失败: " + e.getMessage(), e);
        }
    }

    public String compressVideo(String inputPath, String outputPath, double quality) {
        log.info("开始压缩视频: input={}, output={}, quality={}", inputPath, outputPath, quality);
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath);
             FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, 
                     grabber.getImageWidth(), grabber.getImageHeight(), 
                     grabber.getAudioChannels())) {
            
            grabber.start();
            
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setVideoBitrate((int)(grabber.getVideoBitrate() * quality));
            recorder.setFrameRate(grabber.getFrameRate());
            recorder.setSampleRate(grabber.getSampleRate());
            
            recorder.start();
            
            while (grabber.grab() != null) {
                recorder.record(grabber.grab());
            }
            
            log.info("视频压缩成功: {}", outputPath);
            return outputPath;
            
        } catch (Exception e) {
            log.error("视频压缩失败", e);
            throw new RuntimeException("视频压缩失败: " + e.getMessage(), e);
        }
    }

    public VideoInfo getVideoInfo(String inputPath) {
        log.info("获取视频信息: {}", inputPath);
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();
            
            VideoInfo info = VideoInfo.builder()
                    .duration(grabber.getLengthInTime() / 1000000.0)
                    .width(grabber.getImageWidth())
                    .height(grabber.getImageHeight())
                    .frameRate(grabber.getFrameRate())
                    .videoBitrate(grabber.getVideoBitrate())
                    .audioChannels(grabber.getAudioChannels())
                    .audioSampleRate(grabber.getSampleRate())
                    .audioBitrate(grabber.getAudioBitrate())
                    .format(grabber.getFormat())
                    .build();
            
            log.info("视频信息: {}", info);
            return info;
            
        } catch (Exception e) {
            log.error("获取视频信息失败", e);
            throw new RuntimeException("获取视频信息失败: " + e.getMessage(), e);
        }
    }

    @lombok.Builder
    @lombok.Data
    public static class VideoInfo {
        private double duration;
        private int width;
        private int height;
        private double frameRate;
        private long videoBitrate;
        private int audioChannels;
        private int audioSampleRate;
        private long audioBitrate;
        private String format;
    }

    public boolean validateVideoFile(String filePath) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(filePath)) {
            grabber.start();
            return grabber.getImageWidth() > 0 && grabber.getImageHeight() > 0;
        } catch (Exception e) {
            log.error("验证视频文件失败: {}", filePath, e);
            return false;
        }
    }

    public File convertToAudioFile(String inputPath, String outputFormat) {
        log.info("转换音频文件: input={}, format={}", inputPath, outputFormat);
        
        String outputPath = inputPath.substring(0, inputPath.lastIndexOf('.')) + "." + outputFormat;
        
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath);
             FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, 0)) {
            
            grabber.start();
            
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.setSampleRate(grabber.getSampleRate());
            recorder.setAudioChannels(grabber.getAudioChannels());
            recorder.setFormat(outputFormat);
            
            recorder.start();
            
            while (grabber.grab() != null) {
                recorder.record(grabber.grab());
            }
            
            log.info("音频文件转换成功: {}", outputPath);
            return new File(outputPath);
            
        } catch (Exception e) {
            log.error("音频文件转换失败", e);
            throw new RuntimeException("音频文件转换失败: " + e.getMessage(), e);
        }
    }
}
