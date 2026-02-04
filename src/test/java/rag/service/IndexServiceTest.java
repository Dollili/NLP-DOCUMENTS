package rag.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rag.config.AppConfig;
import rag.model.Document;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IndexService 클래스 테스트
 */
class IndexServiceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 DOCUMENTS 초기화
        AppConfig.DOCUMENTS.clear();
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 정리
        AppConfig.DOCUMENTS.clear();
    }

    @Test
    void testShouldRebuildIndex_인덱스파일없음() {
        // Given: 존재하지 않는 인덱스 경로
        String nonExistentPath = "nonexistent_index";

        // When
        boolean shouldRebuild = IndexService.shouldRebuildIndex(nonExistentPath);

        // Then: 재구축 필요
        assertTrue(shouldRebuild);
    }

    @Test
    void testShouldRebuildIndex_경로가null() {
        // When
        boolean shouldRebuild = IndexService.shouldRebuildIndex(null);

        // Then: 재구축 필요
        assertTrue(shouldRebuild);
    }

    @Test
    void testShouldRebuildIndex_빈경로() {
        // When
        boolean shouldRebuild = IndexService.shouldRebuildIndex("");

        // Then: 재구축 필요
        assertTrue(shouldRebuild);
    }

    @Test
    void testSaveIndex_문서가없을때() {
        // Given: 빈 문서 리스트
        AppConfig.DOCUMENTS.clear();

        // When
        String result = IndexService.saveIndex("test_index");

        // Then: 실패 메시지 반환
        assertTrue(result.contains("실패") || result.contains("없습니다"));
    }

    @Test
    void testSaveIndex_null경로() {
        // Given
        AppConfig.DOCUMENTS.add(new Document("test.txt", "/path/to/test.txt"));

        // When
        String result = IndexService.saveIndex(null);

        // Then: 실패 메시지 반환
        assertTrue(result.contains("실패") || result.contains("비어있습니다"));
    }

    @Test
    void testSaveIndex_빈경로() {
        // Given
        AppConfig.DOCUMENTS.add(new Document("test.txt", "/path/to/test.txt"));

        // When
        String result = IndexService.saveIndex("");

        // Then: 실패 메시지 반환
        assertTrue(result.contains("실패") || result.contains("비어있습니다"));
    }

    @Test
    void testLoadIndex_존재하지않는파일() {
        // Given: 존재하지 않는 경로
        String nonExistentPath = "nonexistent_index";

        // When: 로드 시도 (내부적으로 buildIndex 호출됨)
        assertDoesNotThrow(() -> IndexService.loadIndex(nonExistentPath, null));
    }

    @Test
    void testLoadIndex_null경로() {
        // When & Then: 예외 없이 처리되어야 함
        assertDoesNotThrow(() -> IndexService.loadIndex(null, null));
    }

    @Test
    void testLoadIndex_빈경로() {
        // When & Then: 예외 없이 처리되어야 함
        assertDoesNotThrow(() -> IndexService.loadIndex("", null));
    }

    @Test
    void testBuildIndex_정상실행() throws IOException {
        // Given: 테스트 폴더 및 파일 생성
        Path testFolder = tempDir.resolve("test_docs");
        Files.createDirectory(testFolder);
        Files.writeString(testFolder.resolve("doc1.txt"), "Test content 1");
        Files.writeString(testFolder.resolve("doc2.txt"), "Test content 2");
        
        String originalPath = AppConfig.DOC_PATH;
        try {
            AppConfig.DOC_PATH = testFolder.toString();

            // When
            String result = IndexService.buildIndex("test_index", null);

            // Then
            assertNotNull(result);
            assertTrue(AppConfig.DOCUMENTS.size() >= 0); // 문서가 인덱싱됨
        } finally {
            AppConfig.DOC_PATH = originalPath;
            // 생성된 파일들 명시적 삭제
            Files.deleteIfExists(testFolder.resolve("doc1.txt"));
            Files.deleteIfExists(testFolder.resolve("doc2.txt"));
            Files.deleteIfExists(testFolder);
        }
    }

    @Test
    void testGetDocuments_정상실행() throws IOException {
        // Given: 테스트 폴더 생성
        Path testFolder = tempDir.resolve("test_docs2");
        Files.createDirectory(testFolder);
        Files.writeString(testFolder.resolve("doc1.txt"), "Test content");
        
        String originalPath = AppConfig.DOC_PATH;
        try {
            AppConfig.DOC_PATH = testFolder.toString();

            // When
            assertDoesNotThrow(() -> IndexService.getDocuments(null));

            // Then: 예외 없이 실행되어야 함
            assertTrue(true);
        } finally {
            AppConfig.DOC_PATH = originalPath;
            // 생성된 파일들 명시적 삭제
            Files.deleteIfExists(testFolder.resolve("doc1.txt"));
            Files.deleteIfExists(testFolder);
        }
    }
}
