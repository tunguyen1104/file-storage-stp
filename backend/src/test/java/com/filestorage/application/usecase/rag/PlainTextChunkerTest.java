package com.filestorage.application.usecase.rag;

import com.filestorage.domain.model.rag.RagTextChunk;
import com.filestorage.infrastructure.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlainTextChunkerTest {

    @Test
    void blankTextProducesNoChunks() {
        PlainTextChunker chunker = chunker(50, 10);

        assertTrue(chunker.chunk(" \n\t ").isEmpty());
    }

    @Test
    void shortTextProducesOneChunk() {
        PlainTextChunker chunker = chunker(50, 10);

        List<RagTextChunk> chunks = chunker.chunk("hello\r\nworld");

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).index());
        assertEquals("hello\nworld", chunks.get(0).content());
    }

    @Test
    void longTextSplitsWithOverlap() {
        PlainTextChunker chunker = chunker(50, 8);
        String text = "A".repeat(30) + "\n\n" + "B".repeat(30) + "\n\n" + "C".repeat(30);

        List<RagTextChunk> chunks = chunker.chunk(text);

        assertTrue(chunks.size() > 1);
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index());
            assertFalse(chunks.get(i).content().isBlank());
            assertTrue(chunks.get(i).content().length() <= 50);
        }
        assertTrue(chunks.get(1).startOffset() < chunks.get(0).endOffset());
    }

    @Test
    void overlapMustBeSmallerThanChunkSize() {
        RagProperties properties = new RagProperties();
        properties.getChunks().setSizeChars(100);
        properties.getChunks().setOverlapChars(100);

        assertThrows(IllegalStateException.class, () -> new PlainTextChunker(properties));
    }

    private static PlainTextChunker chunker(int sizeChars, int overlapChars) {
        RagProperties properties = new RagProperties();
        properties.getChunks().setSizeChars(sizeChars);
        properties.getChunks().setOverlapChars(overlapChars);
        return new PlainTextChunker(properties);
    }
}
