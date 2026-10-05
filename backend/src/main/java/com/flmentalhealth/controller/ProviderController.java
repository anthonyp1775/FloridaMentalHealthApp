package com.flmentalhealth.controller;

import com.flmentalhealth.dto.ProviderDtos;
import com.flmentalhealth.security.UserPrincipal;
import com.flmentalhealth.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Provider search, detail, shortlists, and directory management.
 *
 * NOTE ON IDENTITY: the shortlist endpoints take the user id from
 * @AuthenticationPrincipal - the JWT - never from a path variable or
 * request body. That is what makes it impossible to read or modify
 * someone else's list by changing a number in the URL (NFR-7).
 */
@RestController
@RequestMapping("/api/providers")
@Tag(name = "Providers", description = "Search the directory and manage shortlists")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    // ---------- search ----------

    /**
     * Every filter is optional; omitting one disables that clause.
     * Combining them narrows with AND.
     *
     * Spring binds the query parameters straight into the record, so
     * the signature stays readable no matter how many filters exist.
     */
    @GetMapping("/search")
    @Operation(summary = "Search providers by county, focus, language, insurance, population and modality")
    public Page<ProviderDtos.Summary> search(
            ProviderDtos.SearchCriteria criteria,
            @PageableDefault(size = 20) Pageable pageable) {

        return providerService.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full detail for one provider")
    public ProviderDtos.Response getById(@PathVariable Long id) {
        return providerService.getById(id);
    }

    // ---------- shortlist ----------

    @GetMapping("/saved")
    @Operation(summary = "The signed-in user's saved providers")
    public List<ProviderDtos.SavedResponse> listSaved(
            @AuthenticationPrincipal UserPrincipal principal) {

        return providerService.listSaved(principal.getId());
    }

    @PostMapping("/{id}/save")
    @Operation(summary = "Add a provider to the signed-in user's list")
    public ResponseEntity<ProviderDtos.SavedResponse> save(
            @PathVariable Long id,
            @RequestBody(required = false) SaveNote body,
            @AuthenticationPrincipal UserPrincipal principal) {

        String note = (body == null) ? null : body.note();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(providerService.save(principal.getId(), id, note));
    }

    @DeleteMapping("/{id}/save")
    @Operation(summary = "Remove a provider from the signed-in user's list")
    public ResponseEntity<Void> unsave(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        providerService.unsave(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Optional body for the save endpoint. */
    public record SaveNote(String note) {}

    // ---------- directory management: ADMIN only ----------

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a provider to the directory")
    public ResponseEntity<ProviderDtos.Response> create(
            @Valid @RequestBody ProviderDtos.Request request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(providerService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a provider's details")
    public ProviderDtos.Response update(
            @PathVariable Long id,
            @Valid @RequestBody ProviderDtos.Request request) {

        return providerService.update(id, request);
    }

    /**
     * PATCH rather than PUT: this changes two fields, not the whole
     * resource, and it is the operation a navigator performs most.
     */
    @PatchMapping("/{id}/capacity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Adjust intake capacity and accepting status")
    public ProviderDtos.Response updateCapacity(
            @PathVariable Long id,
            @Valid @RequestBody ProviderDtos.CapacityRequest request) {

        return providerService.updateCapacity(id, request);
    }

    /** Soft delete - the record survives, it just leaves the directory. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a provider (soft delete)")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        providerService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
