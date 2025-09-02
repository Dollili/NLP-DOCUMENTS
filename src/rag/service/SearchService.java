package rag.service;

import org.json.JSONArray;
import org.json.JSONObject;
import rag.model.Document;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static rag.config.AppConfig.DOCUMENTS;
import static rag.config.AppConfig.MAX_WORKER;

public class SearchService {
    private static final String API_KEY = "API 키";

    private static final int TOP_K_RESULTS = 10;
    private static final int BATCH_SIZE = 50;

    private static final ConcurrentHashMap<String, float[]> similarityCache = new ConcurrentHashMap<>();

    public static void findPath() throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n찾고 싶은 내용을 입력하세요 (종료: exit):");
            String question = scanner.nextLine().trim();

            if (question.equals("exit")) {
                break;
            }

            if (question.isEmpty()) {
                continue;
            }

            long searchStart = System.currentTimeMillis();
            List<Document> results = searchDocuments(question);
            long searchTime = System.currentTimeMillis() - searchStart;
            System.out.println("\n관련 문서 (상위 " + Math.min(TOP_K_RESULTS, results.size()));
            System.out.println("검색 시간: " + searchTime + "ms");

            for (int i = 0; i < Math.min(TOP_K_RESULTS, results.size()); i++) {
                Document doc = results.get(i);
                doc.printDocument();
            }
        }
        scanner.close();
    }

    public static float[] getSimilarities(String question, List<Document> docs) throws Exception {
        String cacheKey = question + "_" + docs.hashCode();
        if (similarityCache.containsKey(cacheKey)) {
            return similarityCache.get(cacheKey);
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String model = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"; // 한국어 최적
        //String model = "sentence-transformers/paraphrase-xlm-r-multilingual-v1"; // 고성능
        //String model = "sentence-transformers/distiluse-base-multilingual-cased-v2"; // 가장 빠름

        String[] docTexts = docs.stream().map(doc -> doc.fileName + " " + doc.path).toArray(String[]::new);

        JSONObject params = new JSONObject().put("source_sentence", question).put("sentences", docTexts);
        JSONObject obj = new JSONObject().put("inputs", params);

        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(URI.create("https://router.huggingface.co/hf-inference/models/" + model + "/pipeline/sentence-similarity"))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(obj.toString()))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("api 호출 실패: " + response.statusCode() + "\n" + response.body());
            }

            String body = response.body();
            JSONArray json = new JSONArray(body);

            float[] similar = new float[json.length()];
            for (int i = 0; i < json.length(); i++) {
                similar[i] = (float) json.getDouble(i);
            }
            //캐싱
            similarityCache.put(cacheKey, similar);
            return similar;
        } catch (Exception e) {
            System.err.println("유사도 개선 오류: " + e.getMessage());
            return new float[docs.size()];
        }
    }

    // 개선된 문서 검색 메서드
    public static List<Document> searchDocuments(String question) throws Exception {
        System.out.println("탐색 중...");

        ExecutorService executor = Executors.newFixedThreadPool(MAX_WORKER);
        List<Future<Void>> futures = new ArrayList<>();

        for (int start = 0; start < DOCUMENTS.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, DOCUMENTS.size());
            List<Document> batch = DOCUMENTS.subList(start, end);

            futures.add(executor.submit(() -> {
                try {
                    float[] scores = getSimilarities(question, batch);
                    for (int i = 0; i < batch.size(); i++) {
                        batch.get(i).score = scores[i];
                    }
                } catch (Exception e) {
                    System.err.println("배치 처리 오류: " + e.getMessage());
                    for (Document doc : batch) {
                        doc.score = 0.0f;
                    }
                }
                return null;
            }));
        }

        // 모든 배치 완료 대기
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                System.err.println("Future 실행 오류: " + e.getMessage());
            }
        }
        executor.shutdown();

        return DOCUMENTS.stream().sorted((d1, d2) -> Float.compare(d2.score, d1.score)).collect(Collectors.toList());
    }

    public static void testAPIConnection() {
        try {
            System.out.println("API 연결 테스트 중...");

            List<Document> testDocs = Arrays.asList(new Document("print_manual.pdf", "/test/print_manual.pdf"), new Document("game_guide.txt", "/test/game_guide.txt"));

            float[] scores = getSimilarities("프린트 문서", testDocs);

            System.out.println("테스트 결과:");
            for (int i = 0; i < scores.length; i++) {
                System.out.printf("%s: %.4f\n", testDocs.get(i).fileName, scores[i]);
            }

        } catch (Exception e) {
            System.err.println("API 테스트 실패: " + e.getMessage());
        }
    }

}
