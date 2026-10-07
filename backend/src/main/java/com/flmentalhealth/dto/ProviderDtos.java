package com.flmentalhealth.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public class ProviderDtos {

    /**
     * Everything the search page can filter on. All fields optional.
     *
     * Every field is an OBJECT type, never a primitive. Spring binds an
     * absent query parameter to null, and null cannot be converted to a
     * primitive - a bare /search with no parameters would fail with 400
     * on the one primitive field. Boolean also lets the service tell
     * "not specified" apart from "explicitly false".
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
