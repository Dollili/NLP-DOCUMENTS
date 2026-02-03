package rag.exception;

/**
 * 인덱스 관련 예외
 * - 인덱스 생성 실패
 * - 인덱스 로드 실패
 * - 파일 I/O 오류
 */
public class IndexException extends Exception {
    private final ErrorType errorType;

    public enum ErrorType {
        SAVE_FAILED("인덱스 저장 실패"),
        LOAD_FAILED("인덱스 로드 실패"),
        INVALID_PATH("잘못된 경로"),
        EMPTY_DOCUMENTS("저장할 문서 없음"),
        FILE_CORRUPTED("파일 손상"),
        PERMISSION_DENIED("파일 접근 권한 없음");

        private final String message;

        ErrorType(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public IndexException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public IndexException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getUserMessage() {
        return errorType.getMessage() + ": " + getMessage();
    }
}
