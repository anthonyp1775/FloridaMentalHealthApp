package com.flmentalhealth.dto;

import jakarta.validation.constraints.*;

/**
 * Reference data the search filters are built from: counties, insurance
 * plans and organizations. All read-heavy and near-static; writes are
 * ADMIN only.
 */
public class CatalogDtos {

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
