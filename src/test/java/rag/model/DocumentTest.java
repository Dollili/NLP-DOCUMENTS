package rag.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Document 클래스 테스트
 */
class DocumentTest {

    @Test
    void testConstructor_정상생성() {
        // When
        Document doc = new Document("test.txt", "/path/to/file");

        // Then
        assertEquals("test.txt", doc.fileName);
        assertEquals("/path/to/file", doc.path);
        assertEquals(0f, doc.score);
    }

    @Test
    void testConstructor_null값허용() {
        // When
        Document doc = new Document(null, null);

        // Then
        assertNull(doc.fileName);
        assertNull(doc.path);
        assertEquals(0f, doc.score);
    }

    @Test
    void testScore_기본값() {
        // Given
        Document doc = new Document("test.txt", "/path/to/file");

        // When & Then
        assertEquals(0f, doc.score);
    }

    @Test
    void testScore_값설정() {
        // Given
        Document doc = new Document("test.txt", "/path/to/file");

        // When
        doc.score = 0.95f;

        // Then
        assertEquals(0.95f, doc.score);
    }

    @Test
    void testPrintDocument_예외없이실행() {
        // Given
        Document doc = new Document("test.txt", "/path/to/file");
        doc.score = 0.85f;

        // When & Then: 예외 없이 실행되어야 함
        assertDoesNotThrow(() -> doc.printDocument());
    }

    @Test
    void testSerialization_Serializable구현확인() {
        // Given
        Document doc = new Document("test.txt", "/path/to/file");

        // When & Then: Serializable 인터페이스 구현 확인
        assertTrue(doc instanceof java.io.Serializable);
    }
}
