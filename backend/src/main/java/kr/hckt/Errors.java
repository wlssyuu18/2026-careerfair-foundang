package kr.hckt;

import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class Errors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> expected(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "요청을 확인해 주세요." : e.getReason())); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> upload() { return ResponseEntity.status(413).body(Map.of("message", "8MB 이하의 이미지를 올려주세요.")); }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class, org.springframework.web.bind.MissingServletRequestParameterException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("message", "입력 형식을 확인해 주세요.")); }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unknown(Exception e) {
        LoggerFactory.getLogger(Errors.class).error("Request failed: {}", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Map.of("message", "처리 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요."));
    }
}
