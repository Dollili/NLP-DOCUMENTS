package rag.config;

public class CacheConfig {
    public static final int SEARCH_CACHE_SIZE = 50;

    public static final int STATS_PRINT_INTERVAL = 20;

    public static final int MAX_DOCUMENTS_IN_PROMPT = 1000;

    public static final int TOP_K_RESULTS = 10;

    private CacheConfig() {
    }
}
