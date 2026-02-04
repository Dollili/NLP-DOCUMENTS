package rag.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LRUCache 클래스 테스트
 */
class LRUCacheTest {

    private LRUCache<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new LRUCache<>(3); // 최대 3개 항목
    }

    @Test
    void testPut_정상저장() {
        // When
        cache.put("key1", "value1");

        // Then
        assertTrue(cache.containsKey("key1"));
        assertEquals("value1", cache.get("key1"));
    }

    @Test
    void testGet_존재하는키() {
        // Given
        cache.put("key1", "value1");

        // When
        String value = cache.get("key1");

        // Then
        assertEquals("value1", value);
    }

    @Test
    void testGet_존재하지않는키() {
        // When
        String value = cache.get("nonexistent");

        // Then
        assertNull(value);
    }

    @Test
    void testContainsKey_존재하는키() {
        // Given
        cache.put("key1", "value1");

        // When & Then
        assertTrue(cache.containsKey("key1"));
    }

    @Test
    void testContainsKey_존재하지않는키() {
        // When & Then
        assertFalse(cache.containsKey("nonexistent"));
    }

    @Test
    void testLRU_용량초과시가장오래된항목제거() {
        // Given: 3개까지만 저장 가능
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // When: 4번째 항목 추가
        cache.put("key4", "value4");

        // Then: 가장 오래된 key1은 제거되어야 함
        assertFalse(cache.containsKey("key1"));
        assertTrue(cache.containsKey("key2"));
        assertTrue(cache.containsKey("key3"));
        assertTrue(cache.containsKey("key4"));
    }

    @Test
    void testLRU_접근시최근사용항목으로갱신() {
        // Given
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // When: key1 접근 (최근 사용으로 변경)
        cache.get("key1");

        // Then: key4 추가 시 key2가 제거되어야 함 (key1은 최근 사용됨)
        cache.put("key4", "value4");

        assertTrue(cache.containsKey("key1")); // 접근했으므로 유지
        assertFalse(cache.containsKey("key2")); // 가장 오래됨
        assertTrue(cache.containsKey("key3"));
        assertTrue(cache.containsKey("key4"));
    }

    @Test
    void testClear_모든항목제거() {
        // Given
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // When
        cache.clear();

        // Then
        assertFalse(cache.containsKey("key1"));
        assertFalse(cache.containsKey("key2"));
    }

    @Test
    void testSize_정상카운트() {
        // Given
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // When & Then
        assertEquals(2, cache.size());
    }

    @Test
    void testGetHitRate_캐시히트율계산() {
        // Given
        cache.put("key1", "value1");

        // When
        cache.get("key1"); // hit
        cache.get("key2"); // miss
        cache.get("key1"); // hit

        // Then: 2 hit / 3 total = 66.666...%
        double hitRate = cache.getHitRate();
        // 2/3 = 0.6666...이므로 0.66에서 0.67 사이
        assertTrue(hitRate >= 0.66 && hitRate <= 0.67, 
                   "Expected hit rate between 0.66 and 0.67, but was " + hitRate);
    }

    @Test
    void testGetHitRate_요청없을때() {
        // When: 아무 요청도 없음
        double hitRate = cache.getHitRate();

        // Then: 0.0 반환
        assertEquals(0.0, hitRate);
    }

    @Test
    void testGetHitRate_모두히트() {
        // Given
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // When: 모든 요청이 hit
        cache.get("key1");
        cache.get("key2");
        cache.get("key1");

        // Then: 히트율 100%
        assertEquals(1.0, cache.getHitRate());
    }

    @Test
    void testGetHitRate_모두미스() {
        // When: 모든 요청이 miss
        cache.get("key1");
        cache.get("key2");
        cache.get("key3");

        // Then: 히트율 0%
        assertEquals(0.0, cache.getHitRate());
    }

    @Test
    void testResetStats_통계초기화() {
        // Given
        cache.put("key1", "value1");
        cache.get("key1"); // hit
        cache.get("key2"); // miss

        // When
        cache.resetStats();

        // Then
        assertEquals(0.0, cache.getHitRate());
    }

    @Test
    void testPrintStats_예외없이실행() {
        // Given
        cache.put("key1", "value1");
        cache.get("key1");
        cache.get("key2");

        // When & Then: 예외 없이 실행되어야 함
        assertDoesNotThrow(() -> cache.printStats());
    }
}
