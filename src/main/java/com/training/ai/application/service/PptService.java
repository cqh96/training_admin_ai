package com.training.ai.application.service;

import com.training.ai.application.util.PptUtil;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextRun;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author sheeran
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PptService {

    @Autowired(required = false)
    private DocumentConverter documentConverter;

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + File.separator + "ppt_images";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PptPage {
        private String imagePath;
        private String textContent;
        private int pageIndex;
    }

    public List<String> convertPptToImages(MultipartFile file) throws IOException {
        List<PptPage> pages = convertPptToPages(file);
        return pages.stream().map(PptPage::getImagePath).collect(Collectors.toList());
    }

    public void convertPptToPdf(File inputFile, File outputFile) throws IOException {
        if (documentConverter != null) {
            log.info("Using JODConverter for PDF conversion: {} -> {}", inputFile.getName(), outputFile.getName());
            try {
                documentConverter.convert(inputFile).to(outputFile).execute();
            } catch (OfficeException e) {
                throw new IOException("JODConverter conversion failed", e);
            }
        } else {
            log.warn("DocumentConverter is null, using PptUtil fallback");
            try {
                PptUtil.convert(inputFile.getAbsolutePath(), outputFile.getAbsolutePath());
            } catch (Exception e) {
                throw new IOException("PptUtil conversion failed", e);
            }
        }
    }

    public String convertToPdf(MultipartFile file) throws IOException {
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
        File tempFile = new File(taskDir, "source" + extension);
        file.transferTo(tempFile);
        
        File pdfFile = new File(taskDir, "converted.pdf");

        try {
            convertPptToPdf(tempFile, pdfFile);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
        
        return pdfFile.getAbsolutePath();
    }

    public List<PptPage> convertPptToPages(MultipartFile file) throws IOException {
        // 为每次上传创建一个独立的任务目录，避免文件名冲突
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
        File tempFile = new File(taskDir, "source" + extension);
        file.transferTo(tempFile);

        List<PptPage> pages = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(tempFile)) {
            if (".pptx".equals(extension)) {
                processPptx(fis, pages, taskDir);
            } else if (".ppt".equals(extension)) {
                processPpt(fis, pages, taskDir);
            } else {
                throw new IllegalArgumentException("不支持的文件格式: " + extension);
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
        
        return pages;
    }

    private void processPptx(FileInputStream fis, List<PptPage> pages, File outputDir) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(fis)) {
            Dimension pgsize = ppt.getPageSize();
            log.info("PPTX page size: width={}, height={}", pgsize.width, pgsize.height);
            List<XSLFSlide> slides = ppt.getSlides();

            // 计算宽高比
            double aspectRatio = (double) pgsize.height / pgsize.width;

            // 1. 直接使用 PPT 原始定义的页面大小，不进行强制放大或缩小
            // 用户要求：画布的大小等于各自每页PPT页面的大小
            int targetCanvasWidth = pgsize.width;
            int targetCanvasHeight = pgsize.height;

            // 2. 检查并限制分辨率 (OpenH264 限制约 943万像素)
            long pixelCount = (long) targetCanvasWidth * targetCanvasHeight;
            long maxPixels = 9400000; // 安全阈值

            if (pixelCount > maxPixels) {
                double resizeFactor = Math.sqrt((double) maxPixels / pixelCount);
                targetCanvasWidth = (int) (targetCanvasWidth * resizeFactor);
                targetCanvasHeight = (int) (targetCanvasHeight * resizeFactor); // 保持宽高比
                log.info("Resolution exceeds limit, scaling down. New canvas size: {}x{}", targetCanvasWidth, targetCanvasHeight);
            }

            // 3. 确保宽高是偶数 (编码器要求)
            if (targetCanvasWidth % 2 != 0) targetCanvasWidth++;
            if (targetCanvasHeight % 2 != 0) targetCanvasHeight++;

            int canvasWidth = targetCanvasWidth;
            int canvasHeight = targetCanvasHeight;

            log.info("Final target image size: {}x{} (Original: {}x{})", canvasWidth, canvasHeight, pgsize.width, pgsize.height);

            // 4. 生成 PDF（使用 JODConverter 或 PptUtil 降级方案）
            File pptxFile = File.createTempFile("pptx_", ".pptx");
            File pdfFile = new File(outputDir, "converted.pdf");
            List<String> textContents = new ArrayList<>();

            try {
                // 将输入流保存为临时文件
                Files.copy(fis, pptxFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // 使用 JODConverter 转换 PPTX 到 PDF
                if (documentConverter != null) {
                    log.info("Using JODConverter for PDF conversion");
                    documentConverter.convert(pptxFile).to(pdfFile).execute();
                } else {
                    log.warn("DocumentConverter is null, using PptUtil fallback");
                    PptUtil.convert(pptxFile.getAbsolutePath(), pdfFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.error("Failed to convert PPTX to PDF", e);
                throw new IOException("PDF conversion failed", e);
            } finally {
                if (pptxFile.exists()) {
                    pptxFile.delete();
                }
            }

            // 提取文本内容
            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                StringBuilder textContent = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        textContent.append(textShape.getText()).append(" ");
                    }
                }
                textContents.add(textContent.toString().trim());
            }

            // 5. 将 PDF 渲染为图片
            // 设置 PDFBox 字体缓存路径，避免因系统字体扫描失败导致的重复扫描和性能问题
            System.setProperty("pdfbox.fontcache", System.getProperty("java.io.tmpdir"));

            try (PDDocument pdDoc = PDDocument.load(pdfFile)) {
                PDFRenderer renderer = new PDFRenderer(pdDoc);
                float imageScale = (float) canvasWidth / pgsize.width;

                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

                try {
                    log.info("Starting PDF rendering for {} pages...", pdDoc.getNumberOfPages());
                    for (int i = 0; i < pdDoc.getNumberOfPages(); i++) {
                        BufferedImage img;
                        try {
                            img = renderer.renderImage(i, imageScale, ImageType.RGB);
                        } catch (Exception e) {
                            log.warn("Page {} rendering warning (possibly font related): {}", i + 1, e.getMessage());
                            throw new IOException("Failed to render page " + (i + 1), e);
                        }

                        String imageName = (i + 1) + ".png";
                        File imageFile = new File(outputDir, imageName);

                        // 异步保存图片
                        BufferedImage finalImg = img;
                        java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try {
                                ImageIO.write(finalImg, "png", imageFile);
                            } catch (IOException e) {
                                log.error("Failed to save image: " + imageFile, e);
                                throw new RuntimeException(e);
                            }
                        }, executor);
                        futures.add(future);

                        pages.add(PptPage.builder()
                                .imagePath(imageFile.getAbsolutePath())
                                .textContent(textContents.get(i))
                                .pageIndex(i + 1)
                                .build());
                    }

                    // 等待所有图片保存完成
                    java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
                } finally {
                    executor.shutdown();
                }
            }

            // 清理临时 PDF 文件
            if (pdfFile.exists()) {
                pdfFile.delete();
            }
        }
    }

    private void processPpt(FileInputStream fis, List<PptPage> pages, File outputDir) throws IOException {
        try (HSLFSlideShow ppt = new HSLFSlideShow(fis)) {
            Dimension pgsize = ppt.getPageSize();
            log.info("PPT page size: width={}, height={}", pgsize.width, pgsize.height);
            List<HSLFSlide> slides = ppt.getSlides();

            // 计算宽高比
            double aspectRatio = (double) pgsize.height / pgsize.width;

            // 1. 直接使用 PPT 原始定义的页面大小，不进行强制放大或缩小
            // 用户要求：画布的大小等于各自每页PPT页面的大小
            int targetCanvasWidth = pgsize.width;
            int targetCanvasHeight = pgsize.height;

            // 2. 检查并限制分辨率 (OpenH264 限制约 943万像素)
            long pixelCount = (long) targetCanvasWidth * targetCanvasHeight;
            long maxPixels = 9400000; // 安全阈值

            if (pixelCount > maxPixels) {
                double resizeFactor = Math.sqrt((double) maxPixels / pixelCount);
                targetCanvasWidth = (int) (targetCanvasWidth * resizeFactor);
                targetCanvasHeight = (int) (targetCanvasHeight * resizeFactor); // 保持宽高比
                log.info("Resolution exceeds limit, scaling down. New canvas size: {}x{}", targetCanvasWidth, targetCanvasHeight);
            }

            // 3. 确保宽高是偶数 (编码器要求)
            if (targetCanvasWidth % 2 != 0) targetCanvasWidth++;
            if (targetCanvasHeight % 2 != 0) targetCanvasHeight++;

            int canvasWidth = targetCanvasWidth;
            int canvasHeight = targetCanvasHeight;

            log.info("Final target image size: {}x{} (Original: {}x{})", canvasWidth, canvasHeight, pgsize.width, pgsize.height);

            // 4. 生成 PDF（使用 JODConverter 或 PptUtil 降级方案）
            File pptFile = File.createTempFile("ppt_", ".ppt");
            File pdfFile = new File(outputDir, "converted.pdf");
            List<String> textContents = new ArrayList<>();

            try {
                // 将输入流保存为临时文件
                Files.copy(fis, pptFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // 使用 JODConverter 转换 PPT 到 PDF
                if (documentConverter != null) {
                    log.info("Using JODConverter for PDF conversion");
                    documentConverter.convert(pptFile).to(pdfFile).execute();
                } else {
                    log.warn("DocumentConverter is null, using PptUtil fallback");
                    PptUtil.convert(pptFile.getAbsolutePath(), pdfFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.error("Failed to convert PPT to PDF", e);
                throw new IOException("PDF conversion failed", e);
            } finally {
                if (pptFile.exists()) {
                    pptFile.delete();
                }
            }

            // 提取文本内容
            for (int i = 0; i < slides.size(); i++) {
                HSLFSlide slide = slides.get(i);
                StringBuilder textContent = new StringBuilder();
                for (Shape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape) {
                        HSLFTextShape textShape = (HSLFTextShape) shape;
                        textContent.append(textShape.getText()).append(" ");
                    }
                }
                textContents.add(textContent.toString().trim());
            }

            // 5. 将 PDF 渲染为图片
            // 设置 PDFBox 字体缓存路径，避免因系统字体扫描失败导致的重复扫描和性能问题
            System.setProperty("pdfbox.fontcache", System.getProperty("java.io.tmpdir"));

            try (PDDocument pdDoc = PDDocument.load(pdfFile)) {
                PDFRenderer renderer = new PDFRenderer(pdDoc);
                float imageScale = (float) canvasWidth / pgsize.width;

                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

                try {
                    log.info("Starting PDF rendering for {} pages...", pdDoc.getNumberOfPages());
                    for (int i = 0; i < pdDoc.getNumberOfPages(); i++) {
                        BufferedImage img;
                        try {
                            img = renderer.renderImage(i, imageScale, ImageType.RGB);
                        } catch (Exception e) {
                            log.warn("Page {} rendering warning (possibly font related): {}", i + 1, e.getMessage());
                            throw new IOException("Failed to render page " + (i + 1), e);
                        }

                        String imageName = (i + 1) + ".png";
                        File imageFile = new File(outputDir, imageName);

                        // 异步保存图片
                        BufferedImage finalImg = img;
                        java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try {
                                ImageIO.write(finalImg, "png", imageFile);
                            } catch (IOException e) {
                                log.error("Failed to save image: " + imageFile, e);
                                throw new RuntimeException(e);
                            }
                        }, executor);
                        futures.add(future);

                        pages.add(PptPage.builder()
                                .imagePath(imageFile.getAbsolutePath())
                                .textContent(textContents.get(i))
                                .pageIndex(i + 1)
                                .build());
                    }

                    // 等待所有图片保存完成
                    java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
                } finally {
                    executor.shutdown();
                }
            }

            // 清理临时 PDF 文件
            if (pdfFile.exists()) {
                pdfFile.delete();
            }
        }
    }

    private void generatePdfFromPptx(FileInputStream fis, File pdfFile) throws IOException {
        // 为了保证清晰度，使用 2 倍分辨率渲染
        final int SCALE_FACTOR = 2;

        try (XMLSlideShow ppt = new XMLSlideShow(fis)) {
            Dimension pgsize = ppt.getPageSize();
            List<XSLFSlide> slides = ppt.getSlides();

            // 增加 PDF 页面边距，下边距比上边距更大以避免底部内容被裁剪
            final float TOP_MARGIN_RATIO = 0.05f; // 上边距 5%
            final float BOTTOM_MARGIN_RATIO = 0.20f; // 下边距 20%
            float pdfPageWidth = (float)pgsize.width * (1 + TOP_MARGIN_RATIO);
            float pdfPageHeight = (float)pgsize.height * (1 + TOP_MARGIN_RATIO + BOTTOM_MARGIN_RATIO);
            float marginX = pdfPageWidth * TOP_MARGIN_RATIO / 2;
            float marginY = pdfPageHeight * TOP_MARGIN_RATIO;

            try (PDDocument pdDoc = new PDDocument()) {
                for (XSLFSlide slide : slides) {
                    // 创建比 PPT 页面稍大的 PDF 页面，预留边距
                    PDPage pdPage = new PDPage(new PDRectangle(pdfPageWidth, pdfPageHeight));
                    pdDoc.addPage(pdPage);

                    // 使用 2 倍分辨率渲染 PPT 幻灯片到图片，提高清晰度
                    int scaledWidth = (int)pgsize.width * SCALE_FACTOR;
                    int scaledHeight = (int)pgsize.height * SCALE_FACTOR;
                    BufferedImage img = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = img.createGraphics();

                    // 设置高质量渲染参数
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                    // 尝试获取并填充 PPT 背景色，如果失败则默认白色
                    Color bgColor = Color.WHITE;
                    try {
                         if (slide.getBackground() != null && slide.getBackground().getFillColor() != null) {
                             bgColor = slide.getBackground().getFillColor();
                         }
                    } catch (Exception e) {
                        // 忽略获取背景色异常，降级为白色
                    }
                    graphics.setPaint(bgColor);
                    graphics.fill(new Rectangle2D.Float(0, 0, scaledWidth, scaledHeight));

                    // 应用缩放 (ScaleX=100%, ScaleY=100%)，不压缩保持原始比例
                    graphics.scale(SCALE_FACTOR, SCALE_FACTOR);

                    // 直接使用 Apache POI 渲染幻灯片，这样能保持原始字体
                    slide.draw(graphics);
                    graphics.dispose();

                    // 将图片添加到 PDF，在页面中居中绘制，避免边缘被裁剪
                    PDImageXObject pdImage = LosslessFactory.createFromImage(pdDoc, img);
                    try (PDPageContentStream contentStream = new PDPageContentStream(pdDoc, pdPage)) {
                        contentStream.drawImage(pdImage, marginX, marginY, (float)pgsize.width, (float)pgsize.height);
                    }
                }
                pdDoc.save(pdfFile);
            } catch (Exception e) {
                log.error("Failed to generate PDF", e);
                throw new IOException("PDF generation failed", e);
            }
        }
    }

    private void generatePdfFromPpt(FileInputStream fis, File pdfFile) throws IOException {
        // 为了保证清晰度，使用 2 倍分辨率渲染
        final int SCALE_FACTOR = 2;

        try (HSLFSlideShow ppt = new HSLFSlideShow(fis)) {
            Dimension pgsize = ppt.getPageSize();
            List<HSLFSlide> slides = ppt.getSlides();

            // 增加 PDF 页面边距，下边距比上边距更大以避免底部内容被裁剪
            final float TOP_MARGIN_RATIO = 0.05f; // 上边距 5%
            final float BOTTOM_MARGIN_RATIO = 0.20f; // 下边距 20%
            float pdfPageWidth = (float)pgsize.width * (1 + TOP_MARGIN_RATIO);
            float pdfPageHeight = (float)pgsize.height * (1 + TOP_MARGIN_RATIO + BOTTOM_MARGIN_RATIO);
            float marginX = pdfPageWidth * TOP_MARGIN_RATIO / 2;
            float marginY = pdfPageHeight * TOP_MARGIN_RATIO;

            try (PDDocument pdDoc = new PDDocument()) {
                for (HSLFSlide slide : slides) {
                    // 创建比 PPT 页面稍大的 PDF 页面，预留边距
                    PDPage pdPage = new PDPage(new PDRectangle(pdfPageWidth, pdfPageHeight));
                    pdDoc.addPage(pdPage);

                    // 使用 2 倍分辨率渲染 PPT 幻灯片到图片，提高清晰度
                    int scaledWidth = (int)pgsize.width * SCALE_FACTOR;
                    int scaledHeight = (int)pgsize.height * SCALE_FACTOR;
                    BufferedImage img = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = img.createGraphics();
                    
                    // 设置高质量渲染参数
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                    // 尝试获取并填充 PPT 背景色，如果失败则默认白色
                    Color bgColor = Color.WHITE;
                    try {
                         if (slide.getBackground() != null && slide.getBackground().getFill() != null && slide.getBackground().getFill().getForegroundColor() != null) {
                             bgColor = slide.getBackground().getFill().getForegroundColor();
                         }
                    } catch (Exception e) {
                        // 忽略获取背景色异常，降级为白色
                    }
                    graphics.setPaint(bgColor);
                    graphics.fill(new Rectangle2D.Float(0, 0, scaledWidth, scaledHeight));

                    // 应用缩放 (ScaleX=100%, ScaleY=100%)，不压缩保持原始比例
                    graphics.scale(SCALE_FACTOR, SCALE_FACTOR);

                    // 直接使用 Apache POI 渲染幻灯片，这样能保持原始字体
                    slide.draw(graphics);
                    graphics.dispose();

                    // 将图片添加到 PDF，在页面中居中绘制，避免边缘被裁剪
                    PDImageXObject pdImage = LosslessFactory.createFromImage(pdDoc, img);
                    try (PDPageContentStream contentStream = new PDPageContentStream(pdDoc, pdPage)) {
                        contentStream.drawImage(pdImage, marginX, marginY, (float)pgsize.width, (float)pgsize.height);
                    }
                }
                pdDoc.save(pdfFile);
            } catch (Exception e) {
                log.error("Failed to generate PDF", e);
                throw new IOException("PDF generation failed", e);
            }
        }
    }

    /**
     * 将 MultipartFile 类型的 PPT 文件转换为 PDF，返回 PDF 文件的字节数组
     *
     * @param pptFile 上传的 PPT 文件
     * @return PDF 文件的字节数组
     * @throws IOException   文件读写异常
     * @throws OfficeException 转换过程中的异常
     */
    public byte[] convertPptToPdf(MultipartFile pptFile) throws IOException, OfficeException {
        if (documentConverter == null) {
            try {
                return PptUtil.convert(pptFile);
            } catch (Exception e) {
                throw new IOException("PDF conversion failed using fallback method", e);
            }
        }
        // 创建临时文件用于存储上传的 PPT
        Path tempInput = Files.createTempFile("input_", ".pptx");
        Path tempOutput = Files.createTempFile("output_", ".pdf");

        try {
            // 将上传的文件内容写入临时输入文件
            pptFile.transferTo(tempInput.toFile());

            // 执行转换：输入 PPT，输出 PDF
            documentConverter.convert(tempInput.toFile()).to(tempOutput.toFile()).execute();

            // 读取转换后的 PDF 字节数组
            return Files.readAllBytes(tempOutput);
        } finally {
            // 清理临时文件
            Files.deleteIfExists(tempInput);
            Files.deleteIfExists(tempOutput);
        }
    }

    public List<PptPage> convertPdfToPages(File pdfFile, File outputDir, List<String> textContents) throws IOException {
        List<PptPage> pages = new ArrayList<>();

        System.setProperty("pdfbox.fontcache", System.getProperty("java.io.tmpdir"));

        try (PDDocument pdDoc = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(pdDoc);
            PDPage firstPage = pdDoc.getPage(0);
            PDRectangle pageSize = firstPage.getMediaBox();
            float pdfWidth = pageSize.getWidth();
            float pdfHeight = pageSize.getHeight();

            // 目标分辨率宽度设置为 2560 (2.5K 分辨率)
            float targetWidth = 2560f;
            float imageScale = targetWidth / pdfWidth;
            
            // 确保缩放比例至少为 1.0，且不超过 4.0 (防止过大内存溢出)
            if (imageScale < 1.0f) imageScale = 1.0f;
            if (imageScale > 4.0f) imageScale = 4.0f;
            
            log.info("PDF rendering scale: {}, target width: {}", imageScale, targetWidth);

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

            try {
                log.info("Starting PDF rendering for {} pages...", pdDoc.getNumberOfPages());
                for (int i = 0; i < pdDoc.getNumberOfPages(); i++) {
                    BufferedImage img;
                    try {
                        img = renderer.renderImage(i, imageScale, ImageType.RGB);
                    } catch (Exception e) {
                        log.warn("Page {} rendering warning (possibly font related): {}", i + 1, e.getMessage());
                        throw new IOException("Failed to render page " + (i + 1), e);
                    }

                    String imageName = (i + 1) + ".png";
                    File imageFile = new File(outputDir, imageName);

                    BufferedImage finalImg = img;
                    java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            ImageIO.write(finalImg, "png", imageFile);
                        } catch (IOException e) {
                            log.error("Failed to save image: " + imageFile, e);
                            throw new RuntimeException(e);
                        }
                    }, executor);
                    futures.add(future);

                    String textContent = i < textContents.size() ? textContents.get(i) : "";
                    pages.add(PptPage.builder()
                            .imagePath(imageFile.getAbsolutePath())
                            .textContent(textContent)
                            .pageIndex(i + 1)
                            .build());
                }

                java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            } finally {
                executor.shutdown();
            }
        }

        return pages;
    }

    public String convertPptToPdfPath(MultipartFile file) throws IOException {
        return convertToPdf(file);
    }
}
