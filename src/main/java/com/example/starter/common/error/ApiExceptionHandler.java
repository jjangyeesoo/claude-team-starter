package com.example.starter.common.error;

import com.example.starter.memo.domain.MemoNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    List<String> details =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("INVALID_INPUT", "요청 값이 올바르지 않습니다.", details));
  }

  @ExceptionHandler(MemoNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(MemoNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("MEMO_NOT_FOUND", e.getMessage(), List.of()));
  }
}
