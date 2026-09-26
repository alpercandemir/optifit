package com.optifit.service;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.optifit.exception.ApiException;

@Component
public class PhotoProcessor {

    private static final long MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000;
    private static final int MIN_DIMENSION = 160;
    private static final int MAX_DIMENSION = 1600;
    private static final int MAX_CONCURRENT_DECODERS = 2;
    private final Semaphore decoders = new Semaphore(MAX_CONCURRENT_DECODERS);

    public record Photo(byte[] bytes) implements AutoCloseable {

        @Override
        public void close() {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public Photo process(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
            throw ApiException.badRequest("Select a photo between 1 byte and 10 MB.");
        }
        if (!decoders.tryAcquire()) {
            throw new ApiException(429, "BUSY", "Photo processing is busy. Please try again shortly.");
        }
        byte[] original = null;
        try {
            original = file.getBytes();
            // Memory-backed input: never create an ImageIO disk cache containing personal
            // photos.
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw ApiException.badRequest("The photo could not be read. Select a JPEG, PNG or WebP image.");
                }
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    var format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    var expected = switch (format) {
                        case "jpeg", "jpg" -> "image/jpeg";
                        case "png" -> "image/png";
                        case "webp" -> "image/webp";
                        default -> "";
                    };
                    if (expected.isEmpty() || !expected.equals(file.getContentType())) {
                        throw ApiException.badRequest("The declared file type does not match the image content.");
                    }
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if ((long) width * height > MAX_PIXELS || width < MIN_DIMENSION || height < MIN_DIMENSION) {
                        throw ApiException.badRequest(
                                "The photo must be at least 160 by 160 pixels and no larger than 20 megapixels.");
                    }
                    return normalize(reader.read(0), orientation(original));
                } finally {
                    reader.dispose();
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("The photo could not be processed. Try another photo.");
        } finally {
            if (original != null) {
                Arrays.fill(original, (byte) 0);
            }
            decoders.release();
        }
    }

    private int orientation(byte[] original) {
        try {
            var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(original));
            var exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception ignored) {
            // Missing or malformed orientation metadata does not invalidate the image.
        }
        return 1;
    }

    private Photo normalize(BufferedImage source, int orientation) throws IOException {
        BufferedImage scaled = null;
        BufferedImage normalized = null;
        try {
            scaled = resize(source);
            normalized = orient(scaled, orientation);
            var bytes = new ByteArrayOutputStream();
            try (var output = new MemoryCacheImageOutputStream(bytes)) {
                if (!ImageIO.write(normalized, "jpeg", output)) {
                    throw new IOException("JPEG encoder is unavailable");
                }
            }
            return new Photo(bytes.toByteArray());
        } finally {
            source.flush();
            if (normalized != null) {
                normalized.flush();
            }
            if (scaled != null) {
                scaled.flush();
            }
        }
    }

    private BufferedImage resize(BufferedImage source) {
        double ratio = Math.min(1, (double) MAX_DIMENSION / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) (source.getWidth() * ratio));
        int height = Math.max(1, (int) (source.getHeight() * ratio));
        var resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = resized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    public static BufferedImage orient(BufferedImage image, int orientation) {
        int w = image.getWidth(), h = image.getHeight();
        if (orientation < 2 || orientation > 8) {
            return image;
        }
        var out = new BufferedImage(orientation >= 5 ? h : w, orientation >= 5 ? w : h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx = x, dy = y;
                switch (orientation) {
                    case 2 -> dx = w - 1 - x;
                    case 3 -> {
                        dx = w - 1 - x;
                        dy = h - 1 - y;
                    }
                    case 4 -> dy = h - 1 - y;
                    case 5 -> {
                        dx = y;
                        dy = x;
                    }
                    case 6 -> {
                        dx = h - 1 - y;
                        dy = x;
                    }
                    case 7 -> {
                        dx = h - 1 - y;
                        dy = w - 1 - x;
                    }
                    case 8 -> {
                        dx = y;
                        dy = w - 1 - x;
                    }
                }
                out.setRGB(dx, dy, image.getRGB(x, y));
            }
        }
        return out;
    }
}
