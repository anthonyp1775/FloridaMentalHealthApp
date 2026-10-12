package com.flmentalhealth.service;

import com.flmentalhealth.dto.Dtos.CatalogDtos;
import com.flmentalhealth.entity.*;
import com.flmentalhealth.exception.ApiExceptions.DuplicateResourceException;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reference data: counties, insurance plans and organizations.
 *
 * Three near-identical CRUD surfaces, so one service rather than three
 * classes that would differ only in which repository they hold.
 *
 * Reads are open to any authenticated user because these collections
 * populate the search filters - a person cannot search without them.
 * Writes are ADMIN only, enforced at the controller.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final Sort BY_NAME = Sort.by("name").ascending();

    private final CountyRepository countyRepository;
    private final InsurancePlanRepository insurancePlanRepository;
    private final OrganizationRepository organizationRepository;

    public CatalogService(CountyRepository countyRepository,
                          InsurancePlanRepository insurancePlanRepository,
                          OrganizationRepository organizationRepository) {
        this.countyRepository = countyRepository;
        this.insurancePlanRepository = insurancePlanRepository;
        this.organizationRepository = organizationRepository;
    }

    // =================================================================
    // Reads
    //
    // These are unpaginated on purpose. The largest is 67 counties and
    // the rest are under 25 - paginating a dropdown's contents would
    // make the frontend do two round trips to fill a select element.
    // =================================================================

    public List<CatalogDtos.CountyResponse> listCounties() {
        return countyRepository.findAll(BY_NAME).stream()
                .map(c -> new CatalogDtos.CountyResponse(
                        c.getId(), c.getName(), c.getRegion(), c.getManagingEntity()))
                .toList();
    }

    public List<CatalogDtos.InsurancePlanResponse> listInsurancePlans() {
        return insurancePlanRepository.findAll(BY_NAME).stream()
                .map(p -> new CatalogDtos.InsurancePlanResponse(
                        p.getId(), p.getName(), p.getPlanType().name()))
                .toList();
    }

    /** Organizations can grow without bound, so this one IS paginated. */
    public Page<CatalogDtos.OrganizationResponse> listOrganizations(Pageable pageable) {
        return organizationRepository.findByActiveTrue(pageable)
                .map(this::toResponse);
    }

    public CatalogDtos.OrganizationResponse getOrganization(Long id) {
        return toResponse(findOrganization(id));
    }

    // =================================================================
    // Writes - ADMIN only
    // =================================================================

    @Transactional
    public CatalogDtos.OrganizationResponse createOrganization(
            CatalogDtos.OrganizationRequest request) {

        if (organizationRepository.existsByName(request.name())) {
            throw new DuplicateResourceException(
                    "An organization named '" + request.name() + "' already exists");
        }

        Organization org = new Organization();
        apply(request, org);

        return toResponse(organizationRepository.save(org));
    }

    @Transactional
    public CatalogDtos.OrganizationResponse updateOrganization(
            Long id, CatalogDtos.OrganizationRequest request) {

        Organization org = findOrganization(id);

        // Renaming is allowed, but not onto a name someone else holds.
        if (!org.getName().equals(request.name())
                && organizationRepository.existsByName(request.name())) {
            throw new DuplicateResourceException(
                    "An organization named '" + request.name() + "' already exists");
        }

        apply(request, org);
        return toResponse(org);   // managed entity - flushed on commit
    }

    // =================================================================
    // Helpers
    // =================================================================

    /**
     * Copies a request onto an entity. Shared by create and update so
     * the two can never drift - a field added here is handled by both.
     */
    private void apply(CatalogDtos.OrganizationRequest request, Organization org) {
        County county = countyRepository.findById(request.countyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "County " + request.countyId() + " not found"));

        org.setName(request.name());
        org.setOrgType(parseOrgType(request.orgType()));
        org.setCounty(county);
        org.setAddressLine1(request.addressLine1());
        org.setAddressLine2(request.addressLine2());
        org.setCity(request.city());
        org.setPostalCode(request.postalCode());
        org.setPhone(request.phone());
        org.setWebsite(request.website());
        org.setBakerActReceivingFacility(request.bakerActReceivingFacility());
    }

    /**
     * Turns the string from the request into the enum, with a message
     * that lists the valid values. Enum.valueOf's own exception just
     * says "No enum constant", which tells the caller nothing useful.
     */
    private Organization.OrgType parseOrgType(String value) {
        try {
            return Organization.OrgType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException _) {
            throw new IllegalArgumentException(
                    "'" + value + "' is not a valid organization type. Valid values: "
                            + java.util.Arrays.toString(Organization.OrgType.values()));
        }
    }

    private Organization findOrganization(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization " + id + " not found"));
    }

    private CatalogDtos.OrganizationResponse toResponse(Organization o) {
        return new CatalogDtos.OrganizationResponse(
                o.getId(),
                o.getName(),
                o.getOrgType().name(),
                o.getCounty().getName(),   // LAZY, loaded inside the transaction
                o.getCity(),
                o.getPhone(),
                o.getWebsite(),
                o.isBakerActReceivingFacility());
    }
}
