package com.training.ai.application.service;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.fileparsing.FileParsingDownloadResponse;
import ai.z.openapi.service.fileparsing.FileParsingUploadReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
public class FileParsingService {

    @Value("${ai.bigmodel.api-key}")
    private String apiKey;

    private volatile ZhipuAiClient zhipuAiClient;
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + File.separator + "ai_file_parsing";

    // 初始化客户端（懒加载）
    private ZhipuAiClient getClient() {
        if (zhipuAiClient == null) {
            synchronized (this) {
                if (zhipuAiClient == null) {
                    zhipuAiClient = ZhipuAiClient.builder()
                            .ofZHIPU()
                            .apiKey(apiKey)
                            .build();
                    log.info("初始化 ZhipuAiClient");
                }
            }
        }
        return zhipuAiClient;
    }

    /**
     * 解析文件内容（支持 PPT、图片等）
     *
     * @param file 上传的文件
     * @return 解析后的文本内容
     */
    public String parseFileContent(MultipartFile file) throws IOException {
        // 1. 保存文件到临时目录
        File uploadDir = new File(TEMP_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        // 去掉点号，API 需要的文件类型参数通常不带点 (例如 "pdf", "docx")
        // 注意：API 文档可能要求特定格式，这里根据通常习惯处理
        String fileType = extension.startsWith(".") ? extension.substring(1) : extension;
        
        // 修正：API 要求的 fileType 参数通常不需要点号
        // 且如果是 PPTX，通常传入 pptx
        
        File tempFile = new File(uploadDir, UUID.randomUUID() + extension);
        file.transferTo(tempFile);

        log.info("开始解析文件: {}, 类型: {}", originalFilename, fileType);

        try {
            // 2. 调用 Zhipu AI SDK 进行文件解析
            FileParsingUploadReq uploadReq = FileParsingUploadReq.builder()
                    .filePath(tempFile.getAbsolutePath())
                    .fileType(fileType)
                    .toolType("prime-sync") // 使用同步解析工具
                    .build();

            // 注意：SDK 的 syncParse 方法可能抛出异常，需要捕获
            FileParsingDownloadResponse response = getClient().fileParsing().syncParse(uploadReq);

            if (response != null && response.getData() != null) {
                log.info("文件解析成功, TaskId: {}", response.getData().getTaskId());
                return response.getData().getContent();
            } else {
                log.error("文件解析返回为空");
                throw new RuntimeException("文件解析失败: API 返回为空");
            }

        } catch (Exception e) {
            log.error("文件解析过程出错", e);
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        } finally {
            // 3. 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
