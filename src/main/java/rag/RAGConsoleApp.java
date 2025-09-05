/*
package rag;

import rag.model.Tracker;

import static rag.config.AppConfig.DOCUMENTS;
import static rag.service.IndexService.*;

public class RAGConsoleApp {
    private static final int MAX_DEPTHS = 4;

    public static void main(String[] args) throws Exception {
        Tracker tracker = new Tracker(0);
        long start = System.currentTimeMillis();

        if (shouldRebuildIndex()) { // 문서 인덱스
            buildIndex(tracker, null);
        } else {
            loadIndex(tracker, null);
        }

        if (DOCUMENTS.isEmpty()) {
            System.out.println("Documents are empty.\n");
            return;
        }

        long resultTime = System.currentTimeMillis() - start;
        System.out.println("총 문서 수: " + DOCUMENTS.size() + " 걸린 시간: " + resultTime + "ms :::" + " Depth: " + MAX_DEPTHS + "\n");

        testAPIConnection(); // api 연결 테스트
        findPath();
    }
}
*/
