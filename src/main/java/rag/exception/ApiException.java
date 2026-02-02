package rag.exception;

/**
 * API 호출 관련 예외
 * - API 키 오류
 * - 네트워크 오류
 * - 응답 파싱 오류
 */
public class ApiException extends Exception {
    private final int statusCode;
    private final ErrorType errorType;

    public enum ErrorType {
        AUTHENTICATION_FAILED("API 인증 실패"),
        RATE_LIMIT_EXCEEDED("API 요청 한도 초과"),
        SERVICE_UNAVAILABLE("서비스 일시 불가"),
        NETWORK_ERROR("네트워크 오류"),
        PARSING_ERROR("응답 파싱 오류"),
        INVALID_RESPONSE("잘못된 응답 형식"),
        UNKNOWN("알 수 없는 오류");

        private final String message;

        ErrorType(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public ApiException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.statusCode = -1;
    }

    public ApiException(ErrorType errorType, String message, int statusCode) {
        super(message);
        this.errorType = errorType;
        this.statusCode = statusCode;
    }

    public ApiException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getUserMessage() {
        return errorType.getMessage() + ": " + getMessage();
    }
}
