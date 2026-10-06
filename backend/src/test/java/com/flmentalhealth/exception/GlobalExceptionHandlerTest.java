package com.flmentalhealth.exception;

import com.flmentalhealth.dto.ReportDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two handlers that exist because Spring's defaults got them wrong.
 *
 * HttpRequestMethodNotSupportedException *implements* the ErrorResponse
 * interface but does not *extend* ErrorResponseException, so a generic
 * handler never matched it and the catch-all reported 500 where the
 * answer is 405. Nothing proved that fix until these tests.
 *
 * Both handlers read a value that Spring declares @Nullable, so each is
 * exercised twice - once with the value present and once without. The
 * null path is the one that would throw a NullPointerException inside
 * an exception handler, which is about the worst place for one.
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("a path variable of the wrong type -> 400 naming the expected type")
    void typeMismatch_namesTheExpectedType() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/referrals/abc");
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", null, new NumberFormatException("abc"));

        ResponseEntity<ReportDtos.ErrorResponse> response =
                handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .contains("abc")
                .contains("id")
                .contains("Long");
    }

    @Test
    @DisplayName("... and degrades gracefully when the required type is unknown")
    void typeMismatch_toleratesAnUnknownRequiredType() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/referrals/abc");
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", null, "id", null, new NumberFormatException("abc"));

        ResponseEntity<ReportDtos.ErrorResponse> response =
                handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("the expected type");
    }

    @Test
    @DisplayName("the wrong verb on a real path -> 405, not 500")
    void methodNotSupported_returns405AndSuggestsTheRightVerb() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("DELETE", "/api/reports/access-gap");
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET"));

        ResponseEntity<ReportDtos.ErrorResponse> response =
                handler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .contains("DELETE")
                .contains("/api/reports/access-gap")
                .contains("GET");
    }

    @Test
    @DisplayName("... and still 405 when Spring does not say which verbs are allowed")
    void methodNotSupported_toleratesAnUnknownSupportedSet() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("DELETE", "/api/reports/access-gap");
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<ReportDtos.ErrorResponse> response =
                handler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("another method");
    }
}
