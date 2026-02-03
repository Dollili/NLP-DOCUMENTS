package rag.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU (Least Recently Used) 캐시 구현
 * 최대 크기를 초과하면 가장 오래 사용되지 않은 항목을 자동으로 제거합니다.
 * 
 * @param <K> 키 타입
 * @param <V> 값 타입
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;
    private long hits = 0;
    private long misses = 0;

    /**
     * LRU 캐시 생성
     * 
     * @param maxSize 최대 항목 수
     */
    public LRUCache(int maxSize) {
        super(maxSize + 1, 0.75f, true); // accessOrder = true로 LRU 동작
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        boolean shouldRemove = size() > maxSize;
        if (shouldRemove) {
            System.out.println("캐시 용량 초과: 가장 오래된 항목 제거 - " + eldest.getKey());
        }
        return shouldRemove;
    }

    @Override
    public V get(Object key) {
        V value = super.get(key);
        if (value != null) {
            hits++;
        } else {
            misses++;
        }
        return value;
    }

    /**
     * 캐시 히트율 반환
     * 
     * @return 0.0 ~ 1.0 사이의 히트율
     */
    public double getHitRate() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    /**
     * 캐시 통계 출력
     */
    public void printStats() {
        long total = hits + misses;
        System.out.println("=== 캐시 통계 ===");
        System.out.println("총 요청: " + total);
        System.out.println("히트: " + hits);
        System.out.println("미스: " + misses);
        System.out.println("히트율: " + String.format("%.2f%%", getHitRate() * 100));
        System.out.println("현재 크기: " + size() + "/" + maxSize);
    }

    /**
     * 캐시 통계 초기화
     */
    public void resetStats() {
        hits = 0;
        misses = 0;
    }
}
