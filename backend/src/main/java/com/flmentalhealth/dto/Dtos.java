package com.flmentalhealth.dto;

import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

/**
 * Every request and response shape in the API, in one file.
 *
 * All of them are Java records: immutable, no boilerplate, and the
 * compiler generates equals/hashCode/toString. Request records carry
 * the validation annotations that @Valid triggers in the controllers;
 * response records carry only the fields a client should see.
 *
 * WHY ONE FILE. These are 190 lines of field declarations with no
 * behavior between them, so five files meant five places to look for
 * something whose definition is one line long. They are grouped into
 * nested classes by resource, which keeps every call site reading the
 * same as before - ProviderDtos.Summary, ReferralDtos.Response - while
 * the package holds a single file.
 *
 * WHY RECORDS AND NOT ENTITIES. Two reasons, and the second is the one
 * people forget. Returning a JPA entity would serialize whatever it
 * holds, including User.password. And it would make the database schema
 * part of the public API, so renaming a column becomes a breaking
 * change for the frontend. These also FLATTEN: a provider's county
 * lives two hops away on organization.county.name, and the client gets
 * it as one string.
 */
public final class Dtos {

    private Dtos() { }   // a namespace, never instantiated

    // =================================================================
    // Authentication
    // =================================================================

    public static final class AuthDtos {

        private AuthDtos() { }

        public record LoginRequest(
                @NotBlank @Email String email,
                @NotBlank String password) {}

        public record RegisterRequest(
                @NotBlank @Size(max = 50) String firstName,
                @NotBlank @Size(max = 50) String lastName,
                @NotBlank @Email @Size(max = 120) String email,
                @NotBlank @Size(min = 8, max = 72) String password) {}

        public record AuthResponse(String token, String email,
                                   String fullName, List<String> roles) {}
    }

    // =================================================================
    // Reference data: counties, insurance plans, organizations
    //
    // Read-heavy and near-static; writes are ADMIN only.
    // =================================================================

    public static final class CatalogDtos {

        private CatalogDtos() { }

        public record CountyResponse(Long id, String name, String region,
                                     String managingEntity) {}

        public record InsurancePlanResponse(Long id, String name, String planType) {}

        public record OrganizationRequest(
                @NotBlank @Size(max = 160) String name,
                @NotBlank String orgType,
                @NotNull Long countyId,
                String addressLine1, String addressLine2,
                String city, String postalCode,
                String phone, String website,
                boolean bakerActReceivingFacility) {}

        public record OrganizationResponse(
                Long id, String name, String orgType, String county,
                String city, String phone, String website,
                boolean bakerActReceivingFacility) {}
    }

    // =================================================================
    // Providers
    // =================================================================

    public static final class ProviderDtos {

        private ProviderDtos() { }

        /**
         * Everything the search page can filter on. All fields optional.
         *
         * Every field is an OBJECT type, never a primitive. Spring binds
         * an absent query parameter to null, and null cannot be
         * converted to a primitive - a bare /search with no parameters
         * would fail with 400 on the one primitive field. Boolean also
         * lets the service tell "not specified" apart from "explicitly
         * false".
         */
        public record SearchCriteria(
                Long countyId,
                Long insurancePlanId,
                Boolean telehealth,
                Boolean acceptingOnly) {

            /** Null means "not specified", which for this flag means false. */
            public boolean acceptingOnlyOrFalse() {
                return Boolean.TRUE.equals(acceptingOnly);
            }
        }

        /** Compact shape for result lists. */
        public record Summary(
                Long id, String fullName, String credential,
                String organization, String county,
                boolean offersTelehealth, boolean acceptingNewClients,
                int openSlots, Integer typicalWaitDays) {}

        /** Full detail for the provider page. */
        public record Response(
                Long id, String firstName, String lastName, String credential,
                String licenseNumber, String bio, int yearsExperience,
                String organization, String orgType, String county, String city,
                boolean offersTelehealth, boolean offersInPerson,
                boolean acceptingNewClients, int openSlots, int waitlistCount,
                Integer typicalWaitDays,
                List<String> insurancePlans) {}

        public record Request(
                @NotBlank @Size(max = 60) String firstName,
                @NotBlank @Size(max = 60) String lastName,
                @NotBlank String credential,
                @Size(max = 30) String licenseNumber,
                @NotNull Long organizationId,
                String bio,
                @PositiveOrZero int yearsExperience,
                boolean offersTelehealth,
                boolean offersInPerson,
                boolean acceptingNewClients,
                @PositiveOrZero int openSlots,
                Integer typicalWaitDays,
                List<Long> insurancePlanIds) {}

        /** ADMIN adjusts intake capacity. */
        public record CapacityRequest(@PositiveOrZero int openSlots,
                                      boolean acceptingNewClients,
                                      @Size(max = 255) String note) {}

        public record SavedResponse(Long id, Long providerId, String fullName,
                                    String organization, String note,
                                    String savedAt) {}
    }

    // =================================================================
    // Referrals
    // =================================================================

    public static final class ReferralDtos {

        private ReferralDtos() { }

        /** What a person submits. Free text only - no clinical questions. */
        public record Request(
                @NotNull Long providerId,
                @Size(max = 2000) String message,
                @NotBlank String preferredContact) {}

        /** What staff send back when resolving one. */
        public record DecisionRequest(
                @NotBlank String status,            // ACCEPTED | WAITLISTED | DECLINED
                @Size(max = 255) String note) {}

        public record Response(
                Long id, String status,
                Long providerId, String providerName, String organization,
                String clientName, String message, String preferredContact,
                String submittedAt, String resolvedAt, String resolvedBy,
                List<HistoryEntry> history) {}

        /** One row of the audit trail, for the status timeline component. */
        public record HistoryEntry(
                String fromStatus, String toStatus,
                String note, String changedBy, String createdAt) {}
    }

    // =================================================================
    // Reports, plus the shared error body
    // =================================================================

    public static final class ReportDtos {

        private ReportDtos() { }

        public record ReferralSummary(
                String from, String to,
                long total, long pending, long accepted,
                long waitlisted, long declined, long withdrawn) {}

        /**
         * The access-gap report: how many providers currently accepting
         * clients take each plan type. Counts distinct providers, not
         * plans, so a clinician contracted with four Medicaid MCOs
         * counts once toward MEDICAID.
         *
         * The query reports the distribution it finds. It does not
         * assume which way the distribution runs - notably, the seeded
         * dataset happens to show MEDICAID above COMMERCIAL, which is
         * the opposite of the usual real-world finding. That is a
         * property of 35 rows of sample data, not of the report.
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
         * Keeping the first five fields identical means a client can
         * parse either one the same way.
         */
        public record ValidationErrorResponse(String timestamp, int status, String error,
                                              String message, String path,
                                              Map<String, String> fieldErrors) {}
    }
}
