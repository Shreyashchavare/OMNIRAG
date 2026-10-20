package com.omragul.processing.exception;

import com.omragul.processing.dto.response.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleJobNotFound(
            JobNotFoundException ex,
            HttpServletRequest request
    ) {
        log.warn("Job not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDto.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error("JOB_NOT_FOUND")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(DuplicateJobException.class)
    public ResponseEntity<ErrorResponseDto> handleDuplicateJob(
            DuplicateJobException ex,
            HttpServletRequest request
    ) {
        log.warn("Duplicate processing job requested: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponseDto.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.CONFLICT.value())
                        .error("DUPLICATE_JOB")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalStateTransition(
            IllegalStateTransitionException ex,
            HttpServletRequest request
    ) {
        log.warn("Illegal state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("ILLEGAL_STATE_TRANSITION")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(MaxRetriesExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleMaxRetriesExceeded(
            MaxRetriesExceededException ex,
            HttpServletRequest request
    ) {
        log.warn("Max retries exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("MAX_RETRIES_EXCEEDED")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ErrorResponseDto> handleDownstreamServiceException(
            DownstreamServiceException ex,
            HttpServletRequest request
    ) {
        log.error("Downstream service communication failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponseDto.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.BAD_GATEWAY.value())
                        .error("DOWNSTREAM_SERVICE_ERROR")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(PermanentProcessingException.class)
    public ResponseEntity<ErrorResponseDto> handlePermanentProcessingException(
            PermanentProcessingException ex,
            HttpServletRequest request
    ) {
        log.warn("Permanent processing failure: [code={}] {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponseDto.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                        .error(ex.getErrorCode() != null ? ex.getErrorCode() : "UNPROCESSABLE_ENTITY")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> String.format("%s: %s", err.getField(), err.getDefaultMessage()))
                .collect(Collectors.toList());

        log.warn("Validation failure on request to {}: {}", request.getRequestURI(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("VALIDATION_ERROR")
                        .message("Invalid request payload")
                        .details(details)
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(ProcessingException.class)
    public ResponseEntity<ErrorResponseDto> handleProcessingException(
            ProcessingException ex,
            HttpServletRequest request
    ) {
        log.error("General processing domain exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("PROCESSING_ERROR")
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unexpected error occurred while processing request to {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error("INTERNAL_SERVER_ERROR")
                        .message("An unexpected error occurred processing the request")
                        .path(request.getRequestURI())
                        .build());
    }
}
