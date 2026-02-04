package rag.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PerformanceMonitor 클래스 테스트
 */
class PerformanceMonitorTest {

    @Test
    void testFormatBytes_바이트단위() {
        // When
        String result = PerformanceMonitor.formatBytes(512);

        // Then
        assertEquals("512 B", result);
    }

    @Test
    void testFormatBytes_킬로바이트단위() {
        // When
        String result = PerformanceMonitor.formatBytes(2048); // 2KB

        // Then
        assertEquals("2.0 KB", result);
    }

    @Test
    void testFormatBytes_메가바이트단위() {
        // When
        String result = PerformanceMonitor.formatBytes(5 * 1024 * 1024); // 5MB

        // Then
        assertEquals("5.0 MB", result);
    }

    @Test
    void testFormatBytes_기가바이트단위() {
        // When
        String result = PerformanceMonitor.formatBytes(3L * 1024 * 1024 * 1024); // 3GB

        // Then
        assertEquals("3.0 GB", result);
    }

    @Test
    void testMeasureTime_정상실행() {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            // When
            PerformanceMonitor.measureTime(() -> {
                try {
                    Thread.sleep(100); // 100ms 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "테스트 작업");

            // Then
            String output = outputStream.toString();
            assertTrue(output.contains("테스트 작업 완료"));
            assertTrue(output.contains("ms"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testMeasureTime_예외발생해도실행시간측정() {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            // When & Then
            assertThrows(RuntimeException.class, () -> {
                PerformanceMonitor.measureTime(() -> {
                    throw new RuntimeException("의도된 예외");
                }, "예외 테스트");
            });

            // 예외가 발생해도 실행 시간은 측정되어야 함
            String output = outputStream.toString();
            assertTrue(output.contains("예외 테스트 완료"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testPrintMemoryUsage_정상출력() {
        // Given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            // When
            PerformanceMonitor.printMemoryUsage();

            // Then
            String output = outputStream.toString();
            assertTrue(output.contains("메모리 사용량"));
            assertTrue(output.contains("사용 중:"));
            assertTrue(output.contains("전체:"));
            assertTrue(output.contains("최대:"));
            assertTrue(output.contains("사용률:"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testForceGC_정상실행() {
        // When & Then: 예외 없이 실행되어야 함
        assertDoesNotThrow(() -> PerformanceMonitor.forceGC());
    }
}
