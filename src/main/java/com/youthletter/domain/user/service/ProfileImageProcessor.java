package com.youthletter.domain.user.service;

import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/**
 * 프로필 사진 정규화. DB 저장 용량을 최소화한다.
 * - JPEG/PNG: 긴 변 512px 이하 JPEG(품질 0.8)로 재인코딩. EXIF(위치정보 포함)는 제거되고 회전값은 픽셀에 반영.
 * - HEIC/HEIF/WebP: JDK가 디코딩하지 못하므로 원본을 그대로 저장하되 1MB 이하만 허용.
 * 형식은 확장자/Content-Type이 아니라 파일 시그니처로 판정한다.
 */
@Component
public class ProfileImageProcessor {
    static final int MAX_EDGE = 512;
    static final int MAX_PASSTHROUGH_BYTES = 1024 * 1024;
    private static final int MAX_SOURCE_EDGE = 12_000;
    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "heif");

    public record Processed(byte[] bytes, String contentType, Integer width, Integer height) {}

    public Processed process(byte[] input) {
        String type = detect(input);
        return switch (type) {
            case "image/jpeg", "image/png" -> reencode(input, type.equals("image/jpeg") ? exifOrientation(input) : 1);
            case "image/heic", "image/heif", "image/webp" -> {
                if (input.length > MAX_PASSTHROUGH_BYTES)
                    throw new BusinessException(ErrorCode.PROFILE_IMAGE_TOO_LARGE, "HEIC·WebP 사진은 1MB 이하만 올릴 수 있습니다");
                yield new Processed(input, type, null, null);
            }
            default -> throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        };
    }

    static String detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) return "image/png";
        if (b.length >= 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP")) return "image/webp";
        if (b.length >= 12 && ascii(b, 4, 4).equals("ftyp")) {
            String brand = ascii(b, 8, 4);
            if (HEIF_BRANDS.contains(brand)) return brand.startsWith("hei") || brand.startsWith("hev") ? "image/heic" : "image/heif";
        }
        return "unknown";
    }

    private static String ascii(byte[] b, int offset, int len) {
        return new String(b, offset, len, StandardCharsets.US_ASCII);
    }

    private Processed reencode(byte[] input, int orientation) {
        BufferedImage source = decodeBounded(input);
        BufferedImage scaled = scale(source);
        BufferedImage oriented = orient(scaled, orientation);
        byte[] jpeg = writeJpeg(oriented);
        return new Processed(jpeg, "image/jpeg", oriented.getWidth(), oriented.getHeight());
    }

    /** 원본 해상도 전체를 메모리에 올리지 않도록 헤더 크기를 먼저 읽고 서브샘플링해서 디코딩한다. */
    private BufferedImage decodeBounded(byte[] input) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new BusinessException(ErrorCode.INVALID_IMAGE);
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_SOURCE_EDGE || h > MAX_SOURCE_EDGE)
                    throw new BusinessException(ErrorCode.INVALID_IMAGE, "사진 해상도가 너무 큽니다");
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(w, h) / (MAX_EDGE * 2));
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) throw new BusinessException(ErrorCode.INVALID_IMAGE);
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException be) throw be;
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
    }

    private static BufferedImage scale(BufferedImage src) {
        double ratio = Math.min(1.0, (double) MAX_EDGE / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(src.getHeight() * ratio));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE); // 투명 PNG는 흰 배경으로 합성
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** EXIF Orientation(1~8)을 픽셀에 적용한다. 재인코딩 시 EXIF가 사라지므로 필요하다. */
    static BufferedImage orient(BufferedImage src, int orientation) {
        if (orientation < 2 || orientation > 8) return src;
        int w = src.getWidth(), h = src.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int nx, ny;
                switch (orientation) {
                    case 2 -> { nx = w - 1 - x; ny = y; }
                    case 3 -> { nx = w - 1 - x; ny = h - 1 - y; }
                    case 4 -> { nx = x; ny = h - 1 - y; }
                    case 5 -> { nx = y; ny = x; }
                    case 6 -> { nx = h - 1 - y; ny = x; }
                    case 7 -> { nx = h - 1 - y; ny = w - 1 - x; }
                    default -> { nx = y; ny = w - 1 - x; } // 8
                }
                out.setRGB(nx, ny, src.getRGB(x, y));
            }
        }
        return out;
    }

    /** JPEG APP1(Exif) IFD0의 Orientation 태그(0x0112). 없거나 손상이면 1. */
    static int exifOrientation(byte[] b) {
        try {
            int i = 2;
            while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
                int marker = b[i + 1] & 0xFF;
                int len = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
                if (marker == 0xDA || len < 2) break; // 이미지 데이터 시작
                if (marker == 0xE1 && len >= 16 && ascii(b, i + 4, 4).equals("Exif")) {
                    int tiff = i + 10;
                    boolean little = b[tiff] == 'I';
                    int ifd = tiff + readInt(b, tiff + 4, little);
                    int count = readShort(b, ifd, little);
                    for (int e = 0; e < count; e++) {
                        int entry = ifd + 2 + e * 12;
                        if (readShort(b, entry, little) == 0x0112) return readShort(b, entry + 8, little);
                    }
                    return 1;
                }
                i += 2 + len;
            }
        } catch (RuntimeException ignored) {
            // 손상된 EXIF는 회전 없이 처리
        }
        return 1;
    }

    private static int readShort(byte[] b, int at, boolean little) {
        return little ? (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8) : ((b[at] & 0xFF) << 8) | (b[at + 1] & 0xFF);
    }

    private static int readInt(byte[] b, int at, boolean little) {
        return little
                ? (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8) | ((b[at + 2] & 0xFF) << 16) | ((b[at + 3] & 0xFF) << 24)
                : ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    private static byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.8f);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
