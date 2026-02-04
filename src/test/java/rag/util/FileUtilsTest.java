package rag.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileUtils 클래스 테스트
 */
class FileUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void testIsFolderInvalid_존재하는폴더() throws IOException {
        // Given: 실제 폴더 생성
        Path folder = tempDir.resolve("test_folder");
        Files.createDirectory(folder);

        // When & Then: 폴더가 유효해야 함
        assertFalse(FileUtils.isFolderInvalid(folder.toString()));
    }

    @Test
    void testIsFolderInvalid_존재하지않는폴더() {
        // Given: 존재하지 않는 경로
        String nonExistentPath = tempDir.resolve("non_existent").toString();

        // When & Then: 폴더가 유효하지 않아야 함
        assertTrue(FileUtils.isFolderInvalid(nonExistentPath));
    }

    @Test
    void testIsFolderInvalid_파일인경우() throws IOException {
        // Given: 파일 생성 (폴더 아님)
        Path file = tempDir.resolve("test_file.txt");
        Files.createFile(file);

        // When & Then: 파일도 exists()는 true이고 canRead()도 true이므로 
        // isFolderInvalid는 false를 반환 (구현상 파일/폴더 구분하지 않음)
        assertFalse(FileUtils.isFolderInvalid(file.toString()));
    }

    @Test
    void testIsFolderInvalid_빈문자열() {
        // When & Then: 빈 문자열은 존재하지 않으므로 유효하지 않아야 함
        assertTrue(FileUtils.isFolderInvalid(""));
        assertTrue(FileUtils.isFolderInvalid("   "));
    }

    @Test
    void testOpenFolderInExplorer_빈문자열() {
        // When: 빈 문자열로 폴더 열기 시도
        boolean result = FileUtils.openFolderInExplorer("");
        
        // Then: 결과값이 boolean이어야 함 (구현에 따라 true/false 가능)
        // 빈 문자열은 일반적으로 false를 반환할 것으로 예상
        assertFalse(result);
    }

    @Test
    void testOpenFolderInExplorer_null입력() {
        // When: null 입력
        boolean result = FileUtils.openFolderInExplorer(null);
        
        // Then: null은 실패해야 함
        assertFalse(result);
    }

    @Test
    void testOpenFolderInExplorer_존재하지않는폴더() {
        // Given: 존재하지 않는 경로
        String nonExistentPath = "C:\\NonExistent\\Folder\\Path";
        
        // When
        boolean result = FileUtils.openFolderInExplorer(nonExistentPath);
        
        // Then: 존재하지 않는 폴더는 열 수 없으므로 false
        assertFalse(result);
    }

    @Test
    void testIsSystemDirectory_시스템폴더확인() {
        // When & Then
        assertTrue(FileUtils.isSystemDirectory(new File(".git")));
        assertTrue(FileUtils.isSystemDirectory(new File("system32")));
        assertTrue(FileUtils.isSystemDirectory(new File("windows")));
        assertTrue(FileUtils.isSystemDirectory(new File("temp")));
        assertTrue(FileUtils.isSystemDirectory(new File("cache")));
        assertFalse(FileUtils.isSystemDirectory(new File("Documents")));
        assertFalse(FileUtils.isSystemDirectory(new File("MyFolder")));
    }

    @Test
    void testAllowed_허용된확장자() {
        // Given
        File txtFile = new File("test.txt");
        File pdfFile = new File("test.pdf");
        File docxFile = new File("test.docx");
        File exeFile = new File("test.exe");
        File htmlFile = new File("test.html");

        // When & Then
        assertTrue(FileUtils.allowed(txtFile));
        assertTrue(FileUtils.allowed(pdfFile));
        assertTrue(FileUtils.allowed(docxFile));
        assertTrue(FileUtils.allowed(exeFile));
        assertTrue(FileUtils.allowed(htmlFile));
    }

    @Test
    void testAllowed_허용되지않은확장자() {
        // Given
        File jpgFile = new File("test.jpg");
        File mp3File = new File("test.mp3");
        File zipFile = new File("test.zip");

        // When & Then
        assertFalse(FileUtils.allowed(jpgFile));
        assertFalse(FileUtils.allowed(mp3File));
        assertFalse(FileUtils.allowed(zipFile));
    }

    @Test
    void testIsFolderInvalid_depth기반_정상() throws IOException {
        // Given
        Path folder = tempDir.resolve("test_folder_depth");
        Files.createDirectory(folder);

        try {
            // When & Then: depth가 MAX_DEPTHS(4) 이하면 유효
            assertFalse(FileUtils.isFolderInvalid(0, folder.toFile()));
            assertFalse(FileUtils.isFolderInvalid(4, folder.toFile()));
        } finally {
            // 명시적으로 폴더 삭제 시도
            Files.deleteIfExists(folder);
        }
    }

    @Test
    void testIsFolderInvalid_depth기반_초과() throws IOException {
        // Given
        Path folder = tempDir.resolve("test_folder_depth2");
        Files.createDirectory(folder);

        try {
            // When & Then: depth가 MAX_DEPTHS(4)를 초과하면 유효하지 않음
            assertTrue(FileUtils.isFolderInvalid(5, folder.toFile()));
            assertTrue(FileUtils.isFolderInvalid(10, folder.toFile()));
        } finally {
            // 명시적으로 폴더 삭제 시도
            Files.deleteIfExists(folder);
        }
    }
}
