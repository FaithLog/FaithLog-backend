package com.faithlog.media.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

class PdfBoxDocumentValidatorTest {

	private final PdfBoxDocumentValidator validator = new PdfBoxDocumentValidator();

	@Test
	void accepts_a_plain_pdf_and_returns_page_count() throws IOException {
		byte[] pdf = plainPdf();

		var result = validator.validate(pdf, "application/pdf");

		assertThat(result.pageCount()).isEqualTo(1);
	}

	@Test
	void rejects_encrypted_pdf() throws IOException {
		byte[] pdf;
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			document.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner", "user",
				new org.apache.pdfbox.pdmodel.encryption.AccessPermission()));
			document.save(output);
			pdf = output.toByteArray();
		}

		assertThatThrownBy(() -> validator.validate(pdf, "application/pdf"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejects_javascript_open_action() throws IOException {
		byte[] pdf = plainPdf();
		try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDDocumentCatalog catalog = document.getDocumentCatalog();
			COSDictionary action = new COSDictionary();
			action.setName(COSName.S, "JavaScript");
			action.setString(COSName.JS, "app.alert('x')");
			catalog.getCOSObject().setItem(COSName.OPEN_ACTION, action);
			document.save(output);
			pdf = output.toByteArray();
		}

		assertThatThrownBy(() -> validator.validate(pdf, "application/pdf"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejects_embedded_files() throws IOException {
		byte[] pdf = plainPdf();
		try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			COSDictionary names = new COSDictionary();
			names.setItem(COSName.EMBEDDED_FILES, new COSDictionary());
			document.getDocumentCatalog().getCOSObject().setItem(COSName.NAMES, names);
			document.save(output);
			pdf = output.toByteArray();
		}

		assertThatThrownBy(() -> validator.validate(pdf, "application/pdf"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private byte[] plainPdf() throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			document.save(output);
			return output.toByteArray();
		}
	}
}
