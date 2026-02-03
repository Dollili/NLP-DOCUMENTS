package rag.service;

import com.google.genai.Client;
import com.google.genai.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rag.exception.ApiException;
import rag.model.Document;
import rag.util.LRUCache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static rag.config.AppConfig.DOCUMENTS;
import static rag.config.CacheConfig.*;

public class SearchService {
    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    
    private static final String G_API_KEY = "";

    // LRU 캐시
    private static final LRUCache<String, String[]> cache = new LRUCache<>(SEARCH_CACHE_SIZE);

    // 캐시 통계 출력을 위한 카운터
    private static int searchCount = 0;

    // Client 재사용 (리소스 절약)
    private static final Client HTTP_CLIENT = Client.builder()
            .apiKey(G_API_KEY)
            .build();

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

            // 캐시 통계 출력 (주기적으로)
            searchCount++;
            if (searchCount % STATS_PRINT_INTERVAL == 0) {
                synchronized (cache) {
                    cache.printStats();
                }
            }

        } catch (ApiException e) {
            logger.error("API 오류 발생: {}", e.getUserMessage());
            result.put("error", e.getUserMessage());
        } catch (Exception e) {
            logger.error("검색 중 오류 발생", e);
            result.put("error", "검색 중 오류가 발생했습니다: " + e.getMessage());
        }

        return result;
    }

    public static String[] searchGoogle(String question) throws ApiException {
        return googleGemini(question, DOCUMENTS);
    }

    public static String[] googleGemini(String question, List<Document> docs) throws ApiException {
        // 캐시 확인
        if (cache.containsKey(question)) {
            logger.info("캐시에서 결과 반환: {}", question);
            return cache.get(question);
        }

        // API 키 검증
        if (G_API_KEY == null || G_API_KEY.trim().isEmpty()) {
            throw new ApiException(
                    ApiException.ErrorType.AUTHENTICATION_FAILED,
                    "API 키를 확인해주세요."
            );
        }

        // 문서 검증
        if (docs == null || docs.isEmpty()) {
            throw new ApiException(
                    ApiException.ErrorType.INVALID_RESPONSE,
                    "검색할 문서가 없습니다."
            );
        }

        // 문서 수가 너무 많으면 제한
        List<Document> limitedDocs = docs;
        boolean isLimited = false;
        if (docs.size() > MAX_DOCUMENTS_IN_PROMPT) {
            logger.warn("문서 수가 {}개로 너무 많습니다. {}개로 제한합니다.", 
                       docs.size(), MAX_DOCUMENTS_IN_PROMPT);
            limitedDocs = docs.subList(0, MAX_DOCUMENTS_IN_PROMPT);
            isLimited = true;
        }

        // 프롬프트 생성
        StringBuilder prompt = new StringBuilder();
        prompt.append("질문: ").append(question).append("\n\n");
        prompt.append("아래의 문서 경로들 중 가장 관련있는 경로 상위 ").append(TOP_K_RESULTS)
              .append("개를 결과값(경로)만 그대로 출력해줘.\n");
        prompt.append("예: 경로 ::: 유사도, 경로 ::: 유사도, 경로 ::: 유사도 ...\n");
        prompt.append("(텍스트로 출력할 것, 유사도는 소수점 두번째 자리까지, 사담은 넣지 않기)\n\n");

        if (isLimited) {
            prompt.append("주의: 전체 ").append(docs.size()).append("개 중 상위 ")
                  .append(MAX_DOCUMENTS_IN_PROMPT).append("개만 제공됨\n\n");
        }
        
        prompt.append("문서 목록:\n");
        String docList = limitedDocs.stream()
                .map(doc -> doc.path)
                .collect(Collectors.joining("\n"));
        prompt.append(docList);

        // 프롬프트 크기 로깅
        int promptLength = prompt.length();
        logger.debug("프롬프트 크기: {} 문자, 문서 수: {}", promptLength, limitedDocs.size());

        // Content 객체 생성 (올바른 방법)
        Content content = Content.fromParts(
                Part.fromText(prompt.toString())
        );

        // GenerateContentConfig 설정
        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.1F)  // 낮은 온도로 일관성 있는 결과
                .maxOutputTokens(2048)
                .build();

        // 재시도 로직
        GenerateContentResponse response = null;
        int maxRetries = 3;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                // 올바른 API 호출 방법
                response = HTTP_CLIENT.models.generateContent(
                    "gemini-3-flash-preview",
                    content,
                    config
                );

                // 응답 검증
                if (response.candidates().isPresent() && !response.candidates().get().isEmpty()) {
                    Candidate candidate = response.candidates().get().get(0);
                    
                    // finishReason이 Optional로 감싸져 있으므로 안전하게 처리
                    if (candidate.finishReason().isPresent()) {
                        String finishReason = candidate.finishReason().get().toString();
                        logger.debug("Finish Reason: {}", finishReason);

                        if ("STOP".equals(finishReason)) {
                            break; // 성공 시 루프 탈출
                        } else {
                            // SAFETY(차단), MAX_TOKENS(잘림) 등의 상태 처리
                            String errorMsg = "모델 생성 상태 비정상: " + finishReason;
                            if ("SAFETY".equals(finishReason)) {
                                errorMsg += " (안전 필터에 의해 차단됨)";
                            } else if ("MAX_TOKENS".equals(finishReason)) {
                                errorMsg += " (최대 토큰 수 초과)";
                            }
                            throw new ApiException(
                                    ApiException.ErrorType.INVALID_RESPONSE,
                                    errorMsg
                            );
                        }
                    } else {
                        // finishReason이 없는 경우 - 정상적으로 처리
                        logger.debug("Finish Reason: 없음 (정상 처리)");
                        break;
                    }
                } else {
                    throw new ApiException(
                            ApiException.ErrorType.INVALID_RESPONSE,
                            "응답에 candidates가 없습니다."
                    );
                }

            } catch (ApiException e) {
                throw e; // ApiException은 그대로 전파
            } catch (Exception e) {
                String errorStatus = "UNKNOWN_ERROR";
                int httpCode = -1;

                // 에러 메시지 분석
                String errorMessage = e.getMessage();
                if (errorMessage != null) {
                    if (errorMessage.contains("429") || errorMessage.contains("RESOURCE_EXHAUSTED")) {
                        errorStatus = "RATE_LIMIT_EXCEEDED";
                        httpCode = 429;
                    } else if (errorMessage.contains("403") || errorMessage.contains("PERMISSION_DENIED")) {
                        errorStatus = "INVALID_API_KEY";
                        httpCode = 403;
                    } else if (errorMessage.contains("503") || errorMessage.contains("UNAVAILABLE")) {
                        errorStatus = "SERVICE_UNAVAILABLE";
                        httpCode = 503;
                    }
                }

                logger.warn("에러 발생 ({}/{}): {} (코드: {})", 
                           i + 1, maxRetries, errorStatus, httpCode);

                // 재시도 가능한 에러인 경우
                if (i < maxRetries - 1 && (httpCode == 429 || httpCode >= 500)) {
                    try {
                        long waitTime = (long) Math.pow(2, i) * 1000; // 지수 백오프
                        logger.info("재시도 대기 중... ({}ms)", waitTime);
                        Thread.sleep(waitTime);
                        continue;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ApiException(
                                ApiException.ErrorType.NETWORK_ERROR,
                                "재시도 중 인터럽트 발생",
                                ie
                        );
                    }
                }
                
                // 최종 실패
                throw new ApiException(
                        ApiException.ErrorType.NETWORK_ERROR,
                        "최종 실패: " + errorMessage,
                        e
                );
            }
        }

        if (response == null) {
            throw new ApiException(
                    ApiException.ErrorType.NETWORK_ERROR,
                    "최대 재시도 횟수 초과"
            );
        }

        // 응답 파싱
        try {
            String answer = response.text(); // 간단한 text() 메서드 사용
            
            if (answer == null || answer.trim().isEmpty()) {
                logger.warn("AI 응답이 비어있습니다.");
                return new String[0];
            }

            // 부정적 응답 감지
            String lowerAnswer = answer.toLowerCase();
            if (lowerAnswer.contains("없습니다") || lowerAnswer.contains("실패") || 
                lowerAnswer.contains("죄송") || lowerAnswer.contains("찾을 수 없") || 
                lowerAnswer.contains("않았습니다")) {
                logger.info("관련 문서를 찾지 못했습니다: {}", answer);
                return new String[0];
            }

            // 문서가 TOP_K_RESULTS보다 적으면 모두 반환
            if (limitedDocs.size() < TOP_K_RESULTS) {
                return limitedDocs.stream()
                        .map(doc -> doc.path)
                        .toArray(String[]::new);
            }

            // 결과 파싱
            String[] results = answer.split("[,\n]");
            
            // 빈 결과 제거 및 트림
            results = java.util.Arrays.stream(results)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
            
            if (results.length > 0) {
                // 캐시에 저장
                synchronized (cache) {
                    cache.put(question, results);
                }
                logger.info("검색 결과 캐시에 저장: {} ({}개)", question, results.length);
            }

            return results;

        } catch (Exception e) {
            throw new ApiException(
                    ApiException.ErrorType.PARSING_ERROR,
                    "응답 파싱 중 오류: " + e.getMessage() + "\n응답 내용: " + 
                    (response.text() != null ? response.text().substring(0, Math.min(200, response.text().length())) : "null"),
                    e
            );
        }
    }
}
