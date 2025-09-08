package rag.service;

import org.json.JSONArray;
import org.json.JSONObject;
import rag.model.Document;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static rag.config.AppConfig.DOCUMENTS;

public class SearchService {
    private static final String API_KEY = "";
    private static final String G_API_KEY = "";

//    private static final int TOP_K_RESULTS = 10;
//    private static final int BATCH_SIZE = 50;

    private static final Map<String, String[]> cache = new ConcurrentHashMap<>();

    public static Map<String, Object> findPath(String question) throws Exception {
        Map<String, Object> result = new HashMap<>();
        if (question == null || question.isEmpty()) {
            result.put("failed", "no question");
            return result;
        }

        long searchStart = System.currentTimeMillis();
        result.put("success", searchGoogle(question));
        long searchTime = System.currentTimeMillis() - searchStart;
        result.put("taskTime", (double) searchTime / 1000.0);
        return result;
    }

    public static String[] searchGoogle(String question) throws Exception {
        return googleGemini(question, DOCUMENTS);
    }

    public static String[] googleGemini(String question, List<Document> docs) throws Exception {
        if (cache.containsKey(question)) {
            return cache.get(question);
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        StringBuilder prompt = new StringBuilder();
        prompt.append("질문: ").append(question).append("\n");
        prompt.append("아래의 문서 경로들 중 가장 관련있는 경로 상위 10개를 결과값(경로)만 그대로 출력해줘.\n" +
                "예: 경로 ::: 유사도, 경로 ::: 유사도, 경로 ::: 유사도 ... (텍스트로 출력할 것, 유사도는 소수점 두번째 자리까지)");
        prompt.append("문서: \n");
        String[] docTexts = docs.stream().map(doc -> doc.path).toArray(String[]::new);

        for (String docText : docTexts) {
            prompt.append(docText).append("\n");
        }

        JSONObject text = new JSONObject().put("text", prompt.toString());
        JSONObject params = new JSONObject().put("parts", new JSONArray().put(text));
        JSONObject obj = new JSONObject().put("contents", new JSONArray().put(params));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent")) // google Gemini
                .header("x-goog-api-key", G_API_KEY)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(obj.toString()))
                .build();

        HttpResponse<String> response = null;
        for (int i = 0; i < 3; i++) { // 3번 시도
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                break;
            }
            if (response.statusCode() == 503) {
                System.err.println("503 오류 ::: " + (i + 1) + "번째 재시도...");
                Thread.sleep(3000);
            } else {
                throw new RuntimeException("api 호출 실패: " + response.statusCode() + "\n" + response.body());
            }
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("최대 시도 횟수 초과: " + response.statusCode());
        }
        //gemini 답변 구조
        JSONObject json = new JSONObject(response.body());
        JSONArray jsonArray = json.getJSONArray("candidates");
        JSONArray parts = jsonArray.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
        String answer = parts.getJSONObject(0).getString("text").trim();

        cache.put(question, answer.split("[,\n]"));
        return answer.split("[,\n]");
    }

    // 구 버전 - huggingface 무료 api 이용
    // 폴더 경로 한개 씩 읽으면서 질문과 유사도 측정
    // 유사도 기반 출력, 정확도 낮음
    /**************************************************************************/
    /*public static float[] getSimilarities(String question, List<Document> docs) throws Exception {
        String cacheKey = question + "_" + docs.hashCode();
        if (similarityCache.containsKey(cacheKey)) {
            return similarityCache.get(cacheKey);
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        String model = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"; // 한국어 최적
        //String model = "sentence-transformers/paraphrase-xlm-r-multilingual-v1"; // 고성능
        //String model = "sentence-transformers/distiluse-base-multilingual-cased-v2"; // 가장 빠름

        String[] docTexts = docs.stream().map(doc -> extractMeaningfulText(doc.fileName ,doc.path)).toArray(String[]::new);

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
    }*/

    // 개선된 문서 검색 메서드
    /*public static List<Document> searchDocuments(String question) throws Exception {
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

    public static boolean testAPIConnection() {
        try {
            System.out.println("API 연결 테스트 중...");

            List<Document> testDocs = Arrays.asList(new Document("print_manual.pdf", "/test/print_manual.pdf"), new Document("game_guide.txt", "/test/game_guide.txt"));

            float[] scores = getSimilarities("프린트 문서", testDocs);

            System.out.println("테스트 결과:");
            for (int i = 0; i < scores.length; i++) {
                System.out.printf("%s: %.4f\n", testDocs.get(i).fileName, scores[i]);
            }
            return true;
        } catch (Exception e) {
            System.err.println("API 테스트 실패: " + e.getMessage());
            return false;
        }
    }*/
    /**************************************************************************/

}
