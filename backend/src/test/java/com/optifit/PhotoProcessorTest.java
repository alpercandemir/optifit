package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PhotoProcessorTest {

    @Test
    void resizesAndReencodesWithoutMetadataThenErasesOwnedBuffer() throws Exception {
        var input = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(input, "png", out);
        var photo = new PhotoProcessor()
                .process(new MockMultipartFile("photo", "face.png", "image/png", out.toByteArray()));
        var normalized = ImageIO.read(new ByteArrayInputStream(photo.bytes()));
        assertThat(normalized.getWidth()).isEqualTo(1600);
        assertThat(normalized.getHeight()).isEqualTo(800);
        assertThat(photo.bytes()[0] & 255).isEqualTo(255);
        photo.close();
        assertThat(photo.bytes()).containsOnly((byte) 0);
    }

    @Test
    void rejectsTinyAndMislabeledImages() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "png", out);
        assertThatThrownBy(() -> new PhotoProcessor()
                .process(new MockMultipartFile("photo", "face.png", "image/png", out.toByteArray())))
                .isInstanceOf(ApiException.class);
        out.reset();
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        assertThatThrownBy(() -> new PhotoProcessor()
                .process(new MockMultipartFile("photo", "face.jpg", "image/jpeg", out.toByteArray())))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void appliesRotationAndMirroring() {
        var image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff112233);
        var rotated = PhotoProcessor.orient(image, 6);
        assertThat(rotated.getWidth()).isEqualTo(3);
        assertThat(rotated.getHeight()).isEqualTo(2);
        assertThat(rotated.getRGB(2, 0)).isEqualTo(0xff112233);
        assertThat(PhotoProcessor.orient(image, 2).getRGB(1, 0)).isEqualTo(0xff112233);
    }
}
