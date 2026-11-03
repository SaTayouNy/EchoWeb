package org.example.echo.exception;

import org.example.echo.entity.MediaKind;
import org.example.echo.entity.PreferenceType;
import org.example.echo.model.ApiModels;
import org.example.echo.tmdb.TmdbTrendingWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiModels.ErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(error(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiModels.ErrorResponse> handleMethodArg(MethodArgumentNotValidException ex) {
        List<ApiModels.ErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(new ApiModels.ErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        return ResponseEntity.status(422).body(error("VALIDATION_ERROR", "Validation failed", details));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ApiModels.ErrorResponse> handleConstraint(jakarta.validation.ConstraintViolationException ex) {
        List<ApiModels.ErrorDetail> details = ex.getConstraintViolations().stream()
            .map(v -> new ApiModels.ErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
        return ResponseEntity.status(422).body(error("VALIDATION_ERROR", "Validation failed", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiModels.ErrorResponse> handleJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(error("BAD_REQUEST", "Invalid JSON payload", null));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiModels.ErrorResponse> handleBadRequest(Exception ex) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            if (TmdbTrendingWindow.class.equals(mismatch.getRequiredType())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(
                    "BAD_REQUEST",
                    "Invalid request parameters",
                    List.of(new ApiModels.ErrorDetail(mismatch.getName(), "must be one of: day, week"))
                ));
            }

            if (MediaKind.class.equals(mismatch.getRequiredType())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(
                    "BAD_REQUEST",
                    "Invalid request parameters",
                    List.of(new ApiModels.ErrorDetail(mismatch.getName(), "must be one of: movie, tv"))
                ));
            }
            if (PreferenceType.class.equals(mismatch.getRequiredType())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(
                    "BAD_REQUEST", "Invalid request parameters",
                    List.of(new ApiModels.ErrorDetail(mismatch.getName(), "must be one of: movie, tv, actor, director"))
                ));
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(error("BAD_REQUEST", "Invalid request parameters", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiModels.ErrorResponse> handleConflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error("CONFLICT", "Resource already exists", null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiModels.ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(error("UNSUPPORTED_MEDIA_TYPE", "Unsupported request content type", null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiModels.ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders())
            .body(error("METHOD_NOT_ALLOWED", "Unsupported request method", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiModels.ErrorResponse> handleResourceNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Resource not found", null));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiModels.ErrorResponse> handleAccessDenied(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(error("FORBIDDEN", "You do not have permission to perform this action", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiModels.ErrorResponse> handleAny(Exception ex) {
        log.error("Unhandled request exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error("INTERNAL_ERROR", "Internal server error", null));
    }

    private ApiModels.ErrorResponse error(String code, String message, List<ApiModels.ErrorDetail> details) {
        return new ApiModels.ErrorResponse(new ApiModels.ErrorPayload(code, message, details));
    }
}
