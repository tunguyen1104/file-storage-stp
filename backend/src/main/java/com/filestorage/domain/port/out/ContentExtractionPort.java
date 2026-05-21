package com.filestorage.domain.port.out;

import java.io.InputStream;

public interface ContentExtractionPort {
    String extractText(InputStream input, String mimeType, int maxChars);
}
