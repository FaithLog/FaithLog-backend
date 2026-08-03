package com.faithlog.media.infrastructure.image;

import com.faithlog.media.domain.entity.MediaAsset;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

@Component
public class ThumbnailatorImageVariantProcessor {

	private static final int MAX_DIMENSION = 4096;
	private static final int THUMBNAIL_MAX_WIDTH = 480;
	private static final int DETAIL_MAX_WIDTH = 1600;

	public ProcessedImageVariants process(byte[] source, String declaredContentType) {
		if (source == null || source.length == 0 || source.length > MediaAsset.MAX_INPUT_BYTES) {
			throw new IllegalArgumentException("image byte size is invalid");
		}
		String expectedFormat = switch (declaredContentType) {
			case "image/jpeg" -> "JPEG";
			case "image/png" -> "PNG";
			default -> throw new IllegalArgumentException("unsupported image content type");
		};
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new IllegalArgumentException("image cannot be decoded");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				String actualFormat = reader.getFormatName().toUpperCase(java.util.Locale.ROOT);
				if (!actualFormat.equals(expectedFormat)
					&& !(expectedFormat.equals("JPEG") && actualFormat.equals("JPG"))) {
					throw new IllegalArgumentException("declared image type does not match content");
				}
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
					throw new IllegalArgumentException("image dimensions are invalid");
				}
				BufferedImage decoded = reader.read(0);
				BufferedImage thumbnail = resize(decoded, THUMBNAIL_MAX_WIDTH);
				BufferedImage detail = resize(decoded, DETAIL_MAX_WIDTH);
				return new ProcessedImageVariants(
					encodeJpeg(thumbnail),
					encodeJpeg(detail),
					width,
					height,
					thumbnail.getWidth(),
					thumbnail.getHeight(),
					detail.getWidth(),
					detail.getHeight(),
					"image/jpeg"
				);
			} finally {
				reader.dispose();
			}
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("image processing failed", exception);
		}
	}

	private BufferedImage resize(BufferedImage source, int maxWidth) throws Exception {
		if (source.getWidth() <= maxWidth) {
			return Thumbnails.of(source).scale(1.0).asBufferedImage();
		}
		return Thumbnails.of(source).width(maxWidth).asBufferedImage();
	}

	private byte[] encodeJpeg(BufferedImage image) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		Thumbnails.of(image).scale(1.0).outputFormat("jpg").outputQuality(0.85).toOutputStream(output);
		return output.toByteArray();
	}
}
