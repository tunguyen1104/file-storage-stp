package com.filestorage.application.usecase.rag;

import com.filestorage.domain.model.rag.RagTextChunk;
import com.filestorage.infrastructure.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlainTextChunker {

    private static final double MIN_BOUNDARY_RATIO = 0.60;

    private final int chunkSize;
    private final int overlap;

    public PlainTextChunker(RagProperties ragProperties) {
        this.chunkSize = ragProperties.getChunks().getSizeChars();
        this.overlap = ragProperties.getChunks().getOverlapChars();
        if (overlap >= chunkSize) {
            throw new IllegalStateException("app.rag.chunks.overlap-chars must be smaller than size-chars");
        }
    }

    public List<RagTextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = normalize(text).trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<RagTextChunk> chunks = new ArrayList<>();
        int cursor = 0;
        while (cursor < normalized.length()) {
            int maxEnd = Math.min(cursor + chunkSize, normalized.length());
            int end = chooseChunkEnd(normalized, cursor, maxEnd);
            if (end <= cursor) {
                end = maxEnd;
            }

            String content = normalized.substring(cursor, end).trim();
            if (!content.isEmpty()) {
                chunks.add(new RagTextChunk(chunks.size(), content, cursor, end));
            }

            if (end >= normalized.length()) {
                break;
            }

            int next = Math.max(end - overlap, cursor + 1);
            while (next < normalized.length() && Character.isWhitespace(normalized.charAt(next))) {
                next++;
            }
            cursor = next;
        }
        return List.copyOf(chunks);
    }

    private int chooseChunkEnd(String text, int start, int maxEnd) {
        if (maxEnd >= text.length()) {
            return maxEnd;
        }

        int minEnd = start + Math.max(1, (int) ((maxEnd - start) * MIN_BOUNDARY_RATIO));

        int paragraphEnd = lastParagraphBoundary(text, minEnd, maxEnd);
        if (paragraphEnd > start) {
            return paragraphEnd;
        }

        int sentenceEnd = lastSentenceBoundary(text, minEnd, maxEnd);
        if (sentenceEnd > start) {
            return sentenceEnd;
        }

        int lineEnd = lastLineBoundary(text, minEnd, maxEnd);
        return lineEnd > start ? lineEnd : maxEnd;
    }

    private static int lastParagraphBoundary(String text, int minEnd, int maxEnd) {
        int idx = text.lastIndexOf("\n\n", Math.max(0, maxEnd - 2));
        return idx >= minEnd ? idx + 2 : -1;
    }

    private static int lastSentenceBoundary(String text, int minEnd, int maxEnd) {
        for (int i = maxEnd - 1; i >= minEnd; i--) {
            if (isSentenceEnd(text.charAt(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    private static int lastLineBoundary(String text, int minEnd, int maxEnd) {
        int idx = text.lastIndexOf('\n', Math.max(0, maxEnd - 1));
        return idx >= minEnd ? idx + 1 : -1;
    }

    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '?' || c == '!' || c == '。' || c == '？' || c == '！';
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
