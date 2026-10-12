package com.flmentalhealth.exception;

import com.flmentalhealth.dto.Dtos.ReportDtos;
import com.flmentalhealth.exception.ApiExceptions.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns every exception into one consistent JSON shape.
 *
 * The value of this class is that the frontend never has to guess what
 * a failure looks like. A 404 from the provider endpoint and a 409 from
 * the referral endpoint have the same five fields, so one error handler
 * in api.js covers the whole API.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- 404 ----------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // ---------- 409 ----------

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateReferralException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleDuplicateReferral(
            DuplicateReferralException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ---------- 400 ----------

    @ExceptionHandler(NoCapacityException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleNoCapacity(
            NoCapacityException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * A path variable or query parameter that could not be converted to
     * the declared type - /referrals/abc/decision, or a leftover {id}
     * placeholder. The caller sent something malformed, so 400, not 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        Class<?> requiredType = ex.getRequiredType();
        String required = requiredType == null
                ? "the expected type"
                : requiredType.getSimpleName();

        return build(HttpStatus.BAD_REQUEST,
                "'" + ex.getValue() + "' is not a valid value for '"
                        + ex.getName() + "' - expected " + required,
                request);
    }

    /** Bean validation failures, with the offending fields named. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ReportDtos.ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ReportDtos.ValidationErrorResponse body =
                new ReportDtos.ValidationErrorResponse(
                        LocalDateTime.now(ZoneId.systemDefault()).toString(),
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "Validation failed",
                        request.getRequestURI(),
                        fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    // ---------- 401 / 403 ----------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        // Deliberately generic - see AuthService.login().
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleForbidden(
            ForbiddenOperationException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "You do not have access to this resource",
                     request);
    }

    // ---------- framework errors that already know their status ----------

    /**
     * No handler matched the path.
     *
     * This MUST be declared explicitly. Spring Boot 3.2+ throws
     * NoResourceFoundException for an unmatched request, and without
     * this method the catch-all below would swallow it and report 500 -
     * turning every 404 in the application into a server error.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleNoHandler(
            NoResourceFoundException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND,
                     "No endpoint " + request.getMethod() + " "
                             + request.getRequestURI(),
                     request);
    }

    /**
     * Wrong HTTP method -> 405.
     *
     * MUST be declared explicitly. HttpRequestMethodNotSupportedException
     * IMPLEMENTS the ErrorResponse interface but does not EXTEND
     * ErrorResponseException, so the handler below never matched it and
     * the catch-all reported 500. An interface cannot be used in
     * @ExceptionHandler because it is not a Throwable - hence the
     * specific handlers here.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        Set<HttpMethod> methods = ex.getSupportedHttpMethods();
        String supported = methods == null
                ? "another method"
                : methods.toString();

        return build(HttpStatus.METHOD_NOT_ALLOWED,
                request.getMethod() + " is not supported on "
                        + request.getRequestURI() + " - try " + supported,
                request);
    }

    /** Body present but unparseable, e.g. malformed JSON -> 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        return build(HttpStatus.BAD_REQUEST,
                "Request body is missing or not valid JSON", request);
    }

    /** A required query parameter was not supplied -> 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        return build(HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing",
                request);
    }

    /** Wrong Content-Type -> 415. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type " + ex.getContentType()
                        + " is not supported - use application/json",
                request);
    }

    /**
     * Anything else Spring raised that already carries its own status.
     * Honor the status it chose rather than flattening it to 500.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleErrorResponse(
            ErrorResponseException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return build(status, ex.getMessage(), request);
    }

    // ---------- 500 ----------

    /**
     * The catch-all. The exception is logged in full server-side, but
     * the client gets a generic message - a stack trace in a response
     * body tells an attacker about your framework versions and internal
     * structure.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ReportDtos.ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception on {} {}",
                request.getMethod(), request.getRequestURI(), ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                     "An unexpected error occurred", request);
    }

    // ---------- helper ----------

    private ResponseEntity<ReportDtos.ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request) {

        ReportDtos.ErrorResponse body = new ReportDtos.ErrorResponse(
                LocalDateTime.now(ZoneId.systemDefault()).toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());

        return ResponseEntity.status(status).body(body);
    }
}
