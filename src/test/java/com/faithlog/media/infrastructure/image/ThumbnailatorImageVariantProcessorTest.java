package com.faithlog.media.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ThumbnailatorImageVariantProcessorTest {

	@Test
	void validates_png_magic_and_generates_bounded_jpeg_variants() throws Exception {
		ThumbnailatorImageVariantProcessor processor = new ThumbnailatorImageVariantProcessor();

		var result = processor.process(image("png", 2000, 1000), "image/png");

		assertThat(result.sourceWidth()).isEqualTo(2000);
		assertThat(result.sourceHeight()).isEqualTo(1000);
		assertThat(result.thumbnailWidth()).isEqualTo(480);
		assertThat(result.detailWidth()).isEqualTo(1600);
		assertThat(result.thumbnailBytes()).startsWith((byte) 0xFF, (byte) 0xD8);
		assertThat(result.detailBytes()).startsWith((byte) 0xFF, (byte) 0xD8);
	}

	@Test
	void rejects_forged_mime_malformed_bytes_and_oversized_dimensions() throws Exception {
		ThumbnailatorImageVariantProcessor processor = new ThumbnailatorImageVariantProcessor();

		assertThatThrownBy(() -> processor.process(image("png", 10, 10), "image/jpeg"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> processor.process(new byte[] {1, 2, 3}, "image/png"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> processor.process(image("png", 4097, 1), "image/png"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private byte[] image(String format, int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(Color.BLUE);
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, format, output);
		return output.toByteArray();
	}
}
