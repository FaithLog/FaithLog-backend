package com.faithlog.media.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.faithlog.media.service.port.ImageVariantProcessorPort;
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
	void bounds_portrait_png_and_jpeg_variants_by_their_longest_dimension() throws Exception {
		ThumbnailatorImageVariantProcessor processor = new ThumbnailatorImageVariantProcessor();

		for (String format : new String[] {"png", "jpg"}) {
			var narrowPortrait = processor.process(
				image(format, 400, 4096),
				format.equals("png") ? "image/png" : "image/jpeg"
			);
			var widePortrait = processor.process(
				image(format, 1000, 4096),
				format.equals("png") ? "image/png" : "image/jpeg"
			);

			assertVariantBounds(narrowPortrait);
			assertVariantBounds(widePortrait);
		}
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

	private void assertVariantBounds(ImageVariantProcessorPort.ProcessedVariants result) {
		assertThat(Math.max(result.thumbnailWidth(), result.thumbnailHeight())).isLessThanOrEqualTo(480);
		assertThat(Math.max(result.detailWidth(), result.detailHeight())).isLessThanOrEqualTo(1600);
		assertThat((double) result.thumbnailWidth() / result.thumbnailHeight())
			.isCloseTo((double) result.sourceWidth() / result.sourceHeight(), within(0.01));
		assertThat((double) result.detailWidth() / result.detailHeight())
			.isCloseTo((double) result.sourceWidth() / result.sourceHeight(), within(0.01));
	}
}
