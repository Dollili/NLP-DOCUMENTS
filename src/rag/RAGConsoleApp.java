package rag;

import rag.model.Document;

import static rag.config.AppConfig.DOCUMENTS;
import static rag.service.IndexService.*;
import static rag.service.SearchService.testAPIConnection;

public class RAGConsoleApp {
    private static final int MAX_DEPTHS = 4;

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();

        if (shouldRebuildIndex()) { // 문서 인덱스
            buildIndex();
        } else {
            loadIndex();
        }

        if (DOCUMENTS.isEmpty()) {
            System.out.println("Documents are empty.\n");
            return;
        }

        for (Document document : DOCUMENTS) {
            document.printDocument();
        }

        long resultTime = System.currentTimeMillis() - start;
        System.out.println("총 문서 수: " + DOCUMENTS.size() + " 걸린 시간: " + resultTime + "ms :::" + " Depth: " + MAX_DEPTHS + "\n");

        testAPIConnection(); // api 연결 테스트
        //findPath();
    }
}
