package com.flmentalhealth.dto;

import jakarta.validation.constraints.*;

/**
 * Reference data the search filters are built from: counties,
 * specialties, populations, languages, insurance plans, organizations.
 * All read-heavy and near-static; writes are ADMIN only.
 */
public class CatalogDtos {

    public record CountyResponse(Long id, String name, String region,
                                 String managingEntity) {}

    public record SpecialtyRequest(@NotBlank @Size(max = 80) String name,
                                   @Size(max = 255) String description) {}
    public record SpecialtyResponse(Long id, String name, String description) {}

    public record PopulationResponse(Long id, String name, String ageRange) {}

    public record LanguageResponse(Long id, String name, String isoCode) {}

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
