package rag.service;

import org.json.JSONArray;
import org.json.JSONObject;
import rag.exception.ApiException;
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

    public static Map<String, Object> findPath(String question) {
        Map<String, Object> result = new HashMap<>();
        
        // 입력 검증
        if (question == null || question.trim().isEmpty()) {
            result.put("error", "검색어를 입력해주세요.");
            return result;
        }

        try {
            long searchStart = System.currentTimeMillis();
            String[] searchResult = searchGoogle(question);
            long searchTime = System.currentTimeMillis() - searchStart;
            
            result.put("success", searchResult);
            result.put("taskTime", (double) searchTime / 1000.0);
        } catch (ApiException e) {
            System.err.println("API 오류 발생: " + e.getUserMessage());
            result.put("error", e.getUserMessage());
        } catch (Exception e) {
            System.err.println("검색 중 오류 발생: " + e.getMessage());
            result.put("error", "검색 중 오류가 발생했습니다: " + e.getMessage());
        }
        
        return result;
    }

    public static String[] searchGoogle(String question) throws ApiException {
        return googleGemini(question, DOCUMENTS);
    }

    public static String[] googleGemini(String question, List<Document> docs) throws ApiException {
        if (cache.containsKey(question)) {
            return cache.get(question);
        }

        // API 키 검증
        if (G_API_KEY == null || G_API_KEY.trim().isEmpty()) {
            throw new ApiException(
                ApiException.ErrorType.AUTHENTICATION_FAILED,
                "환경변수 'api.key'를 확인해주세요."
            );
        }

        // 문서 검증
        if (docs == null || docs.isEmpty()) {
            throw new ApiException(
                ApiException.ErrorType.INVALID_RESPONSE,
                "검색할 문서가 없습니다."
            );
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 프롬프트 생성
        StringBuilder prompt = new StringBuilder();
        prompt.append("질문: ").append(question).append("\n");
        prompt.append("아래의 문서 경로들 중 가장 관련있는 경로 상위 10개를 결과값(경로)만 그대로 출력해줘.\n");
        prompt.append("예: 경로 ::: 유사도, 경로 ::: 유사도, 경로 ::: 유사도 ... ");
        prompt.append("(텍스트로 출력할 것, 유사도는 소수점 두번째 자리까지, 사담은 넣지 않기)\n");
        prompt.append("문서: \n");
        String[] docTexts = docs.stream().map(doc -> doc.path).toArray(String[]::new);

        for (String docText : docTexts) {
            prompt.append(docText).append("\n");
        }

        JSONObject text = new JSONObject().put("text", prompt.toString());
        JSONObject params = new JSONObject().put("parts", new JSONArray().put(text));
        JSONObject obj = new JSONObject().put("contents", new JSONArray().put(params));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"))
                .header("x-goog-api-key", G_API_KEY)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(obj.toString()))
                .build();

        // 재시도 로직
        HttpResponse<String> response = null;
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    break;
                } else if (response.statusCode() == 503) {
                    System.err.println("서비스 일시 불가(503) - " + (i + 1) + "번째 재시도...");
                    if (i < maxRetries - 1) {
                        Thread.sleep(3000);
                    } else {
                        throw new ApiException(
                            ApiException.ErrorType.SERVICE_UNAVAILABLE,
                            "서비스가 일시적으로 사용 불가능합니다. 잠시 후 다시 시도해주세요.",
                            503
                        );
                    }
                } else if (response.statusCode() == 401) {
                    throw new ApiException(
                        ApiException.ErrorType.AUTHENTICATION_FAILED,
                        "API 키를 확인해주세요.",
                        401
                    );
                } else if (response.statusCode() == 429) {
                    throw new ApiException(
                        ApiException.ErrorType.RATE_LIMIT_EXCEEDED,
                        "잠시 후 다시 시도해주세요.",
                        429
                    );
                } else {
                    throw new ApiException(
                        ApiException.ErrorType.UNKNOWN,
                        String.format("상태 코드: %d, 응답: %s", response.statusCode(), response.body()),
                        response.statusCode()
                    );
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(
                    ApiException.ErrorType.NETWORK_ERROR,
                    "API 호출 중 인터럽트 발생",
                    e
                );
            } catch (ApiException e) {
                throw e; // ApiException은 그대로 전파
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    throw new ApiException(
                        ApiException.ErrorType.NETWORK_ERROR,
                        e.getMessage(),
                        e
                    );
                }
                System.err.println("재시도 중... (" + (i + 1) + "/" + maxRetries + ")");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ApiException(
                        ApiException.ErrorType.NETWORK_ERROR,
                        "재시도 중 인터럽트 발생",
                        ie
                    );
                }
            }
        }

        if (response == null || response.statusCode() != 200) {
            throw new ApiException(
                ApiException.ErrorType.NETWORK_ERROR,
                String.format("최대 재시도 횟수 초과 (상태 코드: %d)", 
                            response != null ? response.statusCode() : -1)
            );
        }

        // 응답 파싱
        try {
            JSONObject json = new JSONObject(response.body());
            
            if (!json.has("candidates")) {
                throw new ApiException(
                    ApiException.ErrorType.INVALID_RESPONSE,
                    "'candidates' 필드가 없습니다."
                );
            }
            
            JSONArray jsonArray = json.getJSONArray("candidates");
            if (jsonArray.length() == 0) {
                throw new ApiException(
                    ApiException.ErrorType.INVALID_RESPONSE,
                    "응답에 결과가 없습니다."
                );
            }
            
            JSONObject candidate = jsonArray.getJSONObject(0);
            if (!candidate.has("content")) {
                throw new ApiException(
                    ApiException.ErrorType.INVALID_RESPONSE,
                    "'content' 필드가 없습니다."
                );
            }
            
            JSONArray parts = candidate.getJSONObject("content").getJSONArray("parts");
            if (parts.length() == 0) {
                throw new ApiException(
                    ApiException.ErrorType.INVALID_RESPONSE,
                    "응답에 내용이 없습니다."
                );
            }
            
            String answer = parts.getJSONObject(0).getString("text").trim();

            // 결과 검증 및 반환
            if (docTexts.length < 10) {
                return docTexts;
            }
            
            // AI 응답이 비어있는 경우
            if (answer.isEmpty()) {
                System.err.println("AI 응답이 비어있습니다.");
                return new String[0];
            }
            
            // 부정적 응답 감지 (개선된 버전)
            String lowerAnswer = answer.toLowerCase();
            if (lowerAnswer.contains("없습니다") || 
                lowerAnswer.contains("실패") || 
                lowerAnswer.contains("죄송") || 
                lowerAnswer.contains("찾을 수 없") ||
                lowerAnswer.contains("않았습니다")) {
                System.out.println("관련 문서를 찾지 못했습니다.");
                return new String[0];
            }
            
            // 결과 파싱 및 캐싱
            String[] results = answer.split("[,\n]");
            if (results.length > 0) {
                cache.put(question, results);
            }
            
            return results;
            
        } catch (org.json.JSONException e) {
            throw new ApiException(
                ApiException.ErrorType.PARSING_ERROR,
                e.getMessage() + "\n응답 내용: " + response.body(),
                e
            );
        }
    }
}
