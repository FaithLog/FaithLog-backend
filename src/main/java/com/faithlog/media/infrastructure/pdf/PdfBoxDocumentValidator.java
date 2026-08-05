package com.faithlog.media.infrastructure.pdf;

import com.faithlog.media.service.port.PdfDocumentValidatorPort;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxDocumentValidator implements PdfDocumentValidatorPort {

	private static final Set<String> FORBIDDEN_KEYS = Set.of(
		"JavaScript", "JS", "EmbeddedFiles", "EF", "OpenAction", "AA", "Launch", "RichMedia"
	);

	@Override
	public ValidatedPdf validate(byte[] source, String declaredContentType) {
		if (!"application/pdf".equals(declaredContentType)
			|| source == null
			|| source.length < 5
			|| source[0] != '%'
			|| source[1] != 'P'
			|| source[2] != 'D'
			|| source[3] != 'F'
			|| source[4] != '-') {
			throw new IllegalArgumentException("invalid PDF signature");
		}
		try (PDDocument document = Loader.loadPDF(source)) {
			if (document.isEncrypted()) {
				throw new IllegalArgumentException("encrypted PDF is not supported");
			}
			var visited = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
			if (containsActiveContent(document.getDocument().getTrailer(), visited)) {
				throw new IllegalArgumentException("active PDF content is not supported");
			}
			return new ValidatedPdf(document.getNumberOfPages());
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException illegalArgumentException) {
				throw illegalArgumentException;
			}
			throw new IllegalArgumentException("invalid PDF document", exception);
		}
	}

	private boolean containsActiveContent(COSBase value, Set<COSBase> visited) {
		if (value == null || !visited.add(value)) {
			return false;
		}
		if (value instanceof COSObject object) {
			return containsActiveContent(object.getObject(), visited);
		}
		if (value instanceof COSArray array) {
			for (COSBase item : array) {
				if (containsActiveContent(item, visited)) {
					return true;
				}
			}
			return false;
		}
		if (value instanceof COSDictionary dictionary) {
			for (COSName key : dictionary.keySet()) {
				if (FORBIDDEN_KEYS.contains(key.getName())
					|| containsActiveContent(dictionary.getDictionaryObject(key), visited)) {
					return true;
				}
			}
		}
		return false;
	}
}
