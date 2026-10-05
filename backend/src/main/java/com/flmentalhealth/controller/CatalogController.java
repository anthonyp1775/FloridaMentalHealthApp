package com.flmentalhealth.controller;

import com.flmentalhealth.dto.CatalogDtos;
import com.flmentalhealth.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reference data for the search filters.
 *
 * Reads need only a valid token - a person cannot search without the
 * lists that populate the filter dropdowns. Writes are ADMIN only,
 * enforced here with @PreAuthorize.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Catalog", description = "Counties, specialties, languages, insurance plans and organizations")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // ---------- reads ----------

    @GetMapping("/counties")
    @Operation(summary = "All 67 Florida counties with region and managing entity")
    public List<CatalogDtos.CountyResponse> counties() {
        return catalogService.listCounties();
    }

    @GetMapping("/specialties")
    @Operation(summary = "Clinical focus areas a provider can be matched on")
    public List<CatalogDtos.SpecialtyResponse> specialties() {
        return catalogService.listSpecialties();
    }

    @GetMapping("/populations")
    @Operation(summary = "Age groups and units of treatment a provider serves")
    public List<CatalogDtos.PopulationResponse> populations() {
        return catalogService.listPopulations();
    }

    @GetMapping("/languages")
    @Operation(summary = "Languages providers offer services in")
    public List<CatalogDtos.LanguageResponse> languages() {
        return catalogService.listLanguages();
    }

    @GetMapping("/insurance-plans")
    @Operation(summary = "Accepted payers, grouped by plan type")
    public List<CatalogDtos.InsurancePlanResponse> insurancePlans() {
        return catalogService.listInsurancePlans();
    }

    /**
     * Paginated, unlike the lookups above - the organization list can
     * grow without bound as the directory expands.
     */
    @GetMapping("/organizations")
    @Operation(summary = "Clinics and agencies (paginated)")
    public Page<CatalogDtos.OrganizationResponse> organizations(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return catalogService.listOrganizations(pageable);
    }

    @GetMapping("/organizations/{id}")
    @Operation(summary = "One organization by id")
    public CatalogDtos.OrganizationResponse organization(@PathVariable Long id) {
        return catalogService.getOrganization(id);
    }

    // ---------- writes: ADMIN only ----------

    /**
     * hasRole("ADMIN") matches the authority ROLE_ADMIN - Spring
     * prepends the prefix itself, which is why role names are stored
     * WITH it in the database and mapped across unchanged.
     */
    @PostMapping("/specialties")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a clinical focus area")
    public ResponseEntity<CatalogDtos.SpecialtyResponse> createSpecialty(
            @Valid @RequestBody CatalogDtos.SpecialtyRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogService.createSpecialty(request));
    }

    @PostMapping("/organizations")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a clinic or agency")
    public ResponseEntity<CatalogDtos.OrganizationResponse> createOrganization(
            @Valid @RequestBody CatalogDtos.OrganizationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogService.createOrganization(request));
    }

    @PutMapping("/organizations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a clinic or agency")
    public CatalogDtos.OrganizationResponse updateOrganization(
            @PathVariable Long id,
            @Valid @RequestBody CatalogDtos.OrganizationRequest request) {

        return catalogService.updateOrganization(id, request);
    }
}
