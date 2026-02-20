package com.training.ai.application.util;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Objects;

public class PptUtil {

    private static final int PPT_IMG_DPI = 600;
    private static final float PDF_MARGIN = 0f;
    public static final float RATIO_16_9 = 16f / 9f;
    public static final float RATIO_4_3 = 4f / 3f;
    private static final int SCALE_FACTOR = 2;
    private static final float CANVAS_SCALE = 1.2f;

    public static byte[] convert(MultipartFile multipartFile) throws Exception {
        Assert.notNull(multipartFile, "PPT文件不能为空");
        Assert.isTrue(!multipartFile.isEmpty(), "PPT文件流为空");
        String originalFilename = multipartFile.getOriginalFilename();
        Assert.isTrue(Objects.nonNull(originalFilename) &&
                        (originalFilename.endsWith(".ppt") || originalFilename.endsWith(".pptx")),
                "仅支持PPT/PPTX格式文件");
        try (InputStream inputStream = multipartFile.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            doConvert(inputStream, outputStream, originalFilename);
            return outputStream.toByteArray();
        }
    }

    public static void convert(String pptPath, String pdfPath) throws Exception {
        File pptFile = new File(pptPath);
        Assert.isTrue(pptFile.exists() && pptFile.isFile(), "PPT文件不存在");
        File pdfFile = new File(pdfPath);
        if (!pdfFile.getParentFile().exists()) {
            boolean mkdirs = pdfFile.getParentFile().mkdirs();
            if (!mkdirs) {
                throw new FileNotFoundException("PDF输出目录创建失败");
            }
        }
        try (InputStream inputStream = new FileInputStream(pptFile);
             OutputStream outputStream = new FileOutputStream(pdfFile)) {
            doConvert(inputStream, outputStream, pptFile.getName());
        }
    }

    private static void doConvert(InputStream inputStream, OutputStream outputStream, String fileName) throws Exception {
        if (fileName.endsWith(".pptx")) {
            convertPptx(inputStream, outputStream);
        } else if (fileName.endsWith(".ppt")) {
            convertPpt(inputStream, outputStream);
        }
    }

    private static void convertPptx(InputStream inputStream, OutputStream outputStream) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(inputStream)) {
            Dimension pageSize = slideShow.getPageSize();
            
            float pdfPageWidth = pageSize.width * CANVAS_SCALE;
            float pdfPageHeight = pageSize.height * CANVAS_SCALE;
            
            Document document = new Document(new Rectangle(pdfPageWidth, pdfPageHeight));
            PdfWriter.getInstance(document, outputStream);
            document.open();
            document.setMargins(PDF_MARGIN, PDF_MARGIN, PDF_MARGIN, PDF_MARGIN);
            
            for (XSLFSlide slide : slideShow.getSlides()) {
                int scaledWidth = pageSize.width * SCALE_FACTOR;
                int scaledHeight = pageSize.height * SCALE_FACTOR;
                BufferedImage bufferedImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics2D = bufferedImage.createGraphics();
                
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics2D.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                
                Color bgColor = Color.WHITE;
                try {
                    if (slide.getBackground() != null && slide.getBackground().getFillColor() != null) {
                        bgColor = slide.getBackground().getFillColor();
                    }
                } catch (Exception e) {
                }
                graphics2D.setPaint(bgColor);
                graphics2D.fill(new Rectangle2D.Float(0, 0, scaledWidth, scaledHeight));
                
                graphics2D.scale(SCALE_FACTOR, SCALE_FACTOR);
                slide.draw(graphics2D);
                graphics2D.dispose();
                
                addImageToPdf(document, bufferedImage, pageSize.width, pageSize.height);
            }
            
            document.close();
        }
    }

    private static void convertPpt(InputStream inputStream, OutputStream outputStream) throws Exception {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(inputStream)) {
            Dimension pageSize = slideShow.getPageSize();
            
            float pdfPageWidth = pageSize.width * CANVAS_SCALE;
            float pdfPageHeight = pageSize.height * CANVAS_SCALE;
            
            Document document = new Document(new Rectangle(pdfPageWidth, pdfPageHeight));
            PdfWriter.getInstance(document, outputStream);
            document.open();
            document.setMargins(PDF_MARGIN, PDF_MARGIN, PDF_MARGIN, PDF_MARGIN);
            
            for (HSLFSlide slide : slideShow.getSlides()) {
                int scaledWidth = pageSize.width * SCALE_FACTOR;
                int scaledHeight = pageSize.height * SCALE_FACTOR;
                BufferedImage bufferedImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics2D = bufferedImage.createGraphics();
                
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics2D.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                
                Color bgColor = Color.WHITE;
                try {
                    if (slide.getBackground() != null && slide.getBackground().getFill() != null && slide.getBackground().getFill().getForegroundColor() != null) {
                        bgColor = slide.getBackground().getFill().getForegroundColor();
                    }
                } catch (Exception e) {
                }
                graphics2D.setPaint(bgColor);
                graphics2D.fill(new Rectangle2D.Float(0, 0, scaledWidth, scaledHeight));
                
                graphics2D.scale(SCALE_FACTOR, SCALE_FACTOR);
                slide.draw(graphics2D);
                graphics2D.dispose();
                
                addImageToPdf(document, bufferedImage, pageSize.width, pageSize.height);
            }
            
            document.close();
        }
    }

    private static void addImageToPdf(Document document, BufferedImage bufferedImage, float originalWidth, float originalHeight) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "png", baos);
            Image image = Image.getInstance(baos.toByteArray());
            
            float documentWidth = document.getPageSize().getWidth();
            float documentHeight = document.getPageSize().getHeight();
            
            float scaledWidth = originalWidth * CANVAS_SCALE;
            float scaledHeight = originalHeight * CANVAS_SCALE;
            
            float x = (documentWidth - scaledWidth) / 2;
            float y = (documentHeight - scaledHeight) / 2;
            
            image.scaleAbsolute(scaledWidth, scaledHeight);
            image.setAbsolutePosition(x, y);
            
            document.add(image);
            document.newPage();
        }
    }
}
