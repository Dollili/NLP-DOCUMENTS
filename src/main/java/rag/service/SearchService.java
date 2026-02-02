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
    private static final String G_API_KEY = System.getenv("api.key");

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
                "예: 경로 ::: 유사도, 경로 ::: 유사도, 경로 ::: 유사도 ... (텍스트로 출력할 것, 유사도는 소수점 두번째 자리까지, 사담은 넣지 않기)");
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

        if (docTexts.length < 10) {
            return docTexts;
        } else if (answer.contains("없습니다") || answer.contains("실패") || answer.contains("죄송") || answer.contains("않았습니다")) {
            return new String[0];
        } else {
            cache.put(question, answer.split("[,\n]"));
            return answer.split("[,\n]");
        }
    }
}
