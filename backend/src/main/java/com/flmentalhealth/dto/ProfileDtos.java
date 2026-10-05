package com.flmentalhealth.dto;

import jakarta.validation.constraints.Size;

/** The person's own stated preferences. Nothing clinical. */
public class ProfileDtos {

    public record Request(
            @Size(max = 30) String phone,
            Long preferredCountyId,
            Long preferredLanguageId,
            Long insurancePlanId,
            boolean prefersTelehealth,
            String contactPreference) {}

    public record Response(
            Long userId, String fullName, String email, String phone,
            String preferredCounty, String preferredLanguage,
            String insurancePlan, boolean prefersTelehealth,
            String contactPreference) {}
}
