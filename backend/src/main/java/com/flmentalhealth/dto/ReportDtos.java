package com.flmentalhealth.dto;

/** ADMIN reporting, plus the shared error body. */
public class ReportDtos {

    public record ReferralSummary(
            String from, String to,
            long total, long pending, long accepted,
            long waitlisted, long declined, long withdrawn) {}

    /**
     * The access-gap report: how many providers currently accepting
     * clients take each plan type. The Medicaid vs commercial split is
     * the most useful single number this system produces.
     */
    public record AccessGapRow(String planType, long acceptingProviders) {}

    /** Capacity by county - where the waits actually are. */
    public record CountyCapacityRow(String county, String region,
                                    long providers, long openSlots,
                                    long waitlisted) {}

    /** Consistent error body returned by GlobalExceptionHandler. */
    public record ErrorResponse(String timestamp, int status, String error,
                                String message, String path) {}

    /**
     * Same shape plus per-field detail, for validation failures.
     * Keeping the first five fields identical means a client can parse
     * either one the same way.
     */
    public record ValidationErrorResponse(String timestamp, int status, String error,
                                          String message, String path,
                                          java.util.Map<String, String> fieldErrors) {}
}
