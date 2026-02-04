package rag.util;

/**
 * 성능 모니터링 유틸리티
 * 실행 시간 측정 및 메모리 사용량 추적
 */
public class PerformanceMonitor {
    
    /**
     * 현재 메모리 사용량 출력
     */
    public static void printMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        System.out.println("=== 메모리 사용량 ===");
        System.out.println("사용 중: " + formatBytes(usedMemory));
        System.out.println("전체: " + formatBytes(totalMemory));
        System.out.println("최대: " + formatBytes(maxMemory));
        System.out.println("사용률: " + String.format("%.1f%%", (double) usedMemory / maxMemory * 100));
    }
    
    /**
     * 바이트를 읽기 쉬운 형식으로 변환
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * 코드 실행 시간 측정
     * 
     * @param task 실행할 작업
     * @param taskName 작업 이름
     */
    public static void measureTime(Runnable task, String taskName) {
        long startTime = System.currentTimeMillis();
        
        try {
            task.run();
        } finally {
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;
            System.out.println(taskName + " 완료: " + elapsedTime + "ms");
        }
    }
    
    /**
     * GC 실행 및 메모리 정리
     */
    public static void forceGC() {
        System.out.println("가비지 컬렉션 실행 중...");
        System.gc();
        try {
            Thread.sleep(100); // GC가 완료될 시간 제공
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
