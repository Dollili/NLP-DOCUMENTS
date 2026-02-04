package rag.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiException 클래스 테스트
 */
class ApiExceptionTest {

    @Test
    void testConstructor_기본생성자() {
        // When
        ApiException exception = new ApiException(
                ApiException.ErrorType.NETWORK_ERROR,
                "네트워크 연결 실패"
        );

        // Then
        assertEquals(ApiException.ErrorType.NETWORK_ERROR, exception.getErrorType());
        assertEquals("네트워크 오류: 네트워크 연결 실패", exception.getUserMessage());
        assertNull(exception.getCause());
        assertEquals(-1, exception.getStatusCode());
    }

    @Test
    void testConstructor_원인포함() {
        // Given
        Exception cause = new RuntimeException("원인 예외");

        // When
        ApiException exception = new ApiException(
                ApiException.ErrorType.PARSING_ERROR,
                "JSON 파싱 중 오류",
                cause
        );

        // Then
        assertEquals(ApiException.ErrorType.PARSING_ERROR, exception.getErrorType());
        assertEquals("응답 파싱 오류: JSON 파싱 중 오류", exception.getUserMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(-1, exception.getStatusCode());
    }

    @Test
    void testConstructor_상태코드포함() {
        // When
        ApiException exception = new ApiException(
                ApiException.ErrorType.RATE_LIMIT_EXCEEDED,
                "요청 횟수 초과",
                429
        );

        // Then
        assertEquals(ApiException.ErrorType.RATE_LIMIT_EXCEEDED, exception.getErrorType());
        assertEquals("API 요청 한도 초과: 요청 횟수 초과", exception.getUserMessage());
        assertEquals(429, exception.getStatusCode());
    }

    @Test
    void testGetUserMessage_정상반환() {
        // Given
        ApiException exception = new ApiException(
                ApiException.ErrorType.RATE_LIMIT_EXCEEDED,
                "1분에 10번까지만 요청 가능"
        );

        // When
        String message = exception.getUserMessage();

        // Then
        assertEquals("API 요청 한도 초과: 1분에 10번까지만 요청 가능", message);
    }

    @Test
    void testGetErrorType_정상반환() {
        // Given
        ApiException exception = new ApiException(
                ApiException.ErrorType.AUTHENTICATION_FAILED,
                "잘못된 API 키"
        );

        // When
        ApiException.ErrorType type = exception.getErrorType();

        // Then
        assertEquals(ApiException.ErrorType.AUTHENTICATION_FAILED, type);
    }

    @Test
    void testErrorType_모든타입존재확인() {
        // When & Then: 모든 ErrorType이 정의되어 있는지 확인
        assertNotNull(ApiException.ErrorType.NETWORK_ERROR);
        assertNotNull(ApiException.ErrorType.RATE_LIMIT_EXCEEDED);
        assertNotNull(ApiException.ErrorType.AUTHENTICATION_FAILED);
        assertNotNull(ApiException.ErrorType.INVALID_RESPONSE);
        assertNotNull(ApiException.ErrorType.PARSING_ERROR);
        assertNotNull(ApiException.ErrorType.SERVICE_UNAVAILABLE);
        assertNotNull(ApiException.ErrorType.UNKNOWN);
    }

    @Test
    void testErrorType_getMessage() {
        // When & Then
        assertEquals("API 인증 실패", ApiException.ErrorType.AUTHENTICATION_FAILED.getMessage());
        assertEquals("API 요청 한도 초과", ApiException.ErrorType.RATE_LIMIT_EXCEEDED.getMessage());
        assertEquals("서비스 일시 불가", ApiException.ErrorType.SERVICE_UNAVAILABLE.getMessage());
        assertEquals("네트워크 오류", ApiException.ErrorType.NETWORK_ERROR.getMessage());
        assertEquals("응답 파싱 오류", ApiException.ErrorType.PARSING_ERROR.getMessage());
        assertEquals("잘못된 응답 형식", ApiException.ErrorType.INVALID_RESPONSE.getMessage());
        assertEquals("알 수 없는 오류", ApiException.ErrorType.UNKNOWN.getMessage());
    }

    @Test
    void testGetMessage_원본메시지반환() {
        // Given
        ApiException exception = new ApiException(
                ApiException.ErrorType.NETWORK_ERROR,
                "연결 시간 초과"
        );

        // When
        String message = exception.getMessage();

        // Then
        assertEquals("연결 시간 초과", message);
    }
}
