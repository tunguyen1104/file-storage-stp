package com.filestorage.adapter.out.analysis;

import com.filestorage.domain.port.out.ContentExtractionPort;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Apache Tika implementation of {@link ContentExtractionPort}.
 *
 * <p>The MIME type is trusted as-is — it has already been validated before
 * the file was persisted to the database. Setting {@code Content-Type} in
 * metadata causes {@code AutoDetectParser} to dispatch directly to the
 * matching parser without any re-detection overhead.
 *
 * <p>A {@link BodyContentHandler} bounded by {@code maxChars} stops
 * extraction at the configured limit. When the limit is reached Tika raises
 * {@code WriteLimitReachedException} (a subclass of {@link SAXException});
 * we catch it and return the partial text rather than failing the indexing
 * attempt.
 */
@Component
@Slf4j
public class TikaContentExtractor implements ContentExtractionPort {

    @Override
    public String extractText(InputStream input, String mimeType, int maxChars) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be positive");
        }

        BodyContentHandler handler = new BodyContentHandler(maxChars);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, mimeType);

        AutoDetectParser parser = new AutoDetectParser();
        ParseContext ctx = new ParseContext();

        try {
            parser.parse(input, handler, metadata, ctx);
        } catch (SAXException e) {
            if (isWriteLimitReached(e)) {
                log.info("Tika extraction reached maxChars={} for mimeType={}; returning partial content", maxChars, mimeType);
                return handler.toString();
            }
            log.warn("Tika SAX error while extracting content (mimeType={}): {}", mimeType, e.getMessage());
            return handler.toString();
        } catch (TikaException | IOException e) {
            log.warn("Tika extraction failed (mimeType={}): {}", mimeType, e.getMessage());
            return handler.toString();
        }

        return handler.toString();
    }

    private static boolean isWriteLimitReached(Throwable t) {
        String simpleName = t.getClass().getSimpleName();
        if ("WriteLimitReachedException".equals(simpleName)) {
            return true;
        }
        Throwable cause = t.getCause();
        return cause != null && cause != t && isWriteLimitReached(cause);
    }
}
