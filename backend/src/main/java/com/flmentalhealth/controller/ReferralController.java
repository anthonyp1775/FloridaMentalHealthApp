package com.flmentalhealth.controller;

import com.flmentalhealth.dto.Dtos.ReferralDtos;
import com.flmentalhealth.entity.ReferralRequest;
import com.flmentalhealth.security.UserPrincipal;
import com.flmentalhealth.service.ReferralService;
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

/**
 * The referral workflow - both sides of it.
 *
 * A client submits, tracks and withdraws their own requests. A
 * navigator works a queue and resolves them. Same controller, enforced
 * by role and by ownership checks in the service.
 */
@RestController
@RequestMapping("/api/referrals")
@Tag(name = "Referrals", description = "Submit, track and resolve referral requests")
public class ReferralController {

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    // ---------- client side ----------

    @PostMapping
    @Operation(summary = "Submit a referral request to a provider")
    public ResponseEntity<ReferralDtos.Response> submit(
            @Valid @RequestBody ReferralDtos.Request request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(referralService.submit(principal.getId(), request));
    }

    @GetMapping("/mine")
    @Operation(summary = "The signed-in user's own referrals, newest first")
    public Page<ReferralDtos.Response> mine(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {

        return referralService.listMine(principal.getId(), pageable);
    }

    /**
     * Readable by the person who submitted it, or by staff. The check
     * lives in the service - it is a rule about the domain, not about
     * HTTP - so the controller just reports who is asking.
     */
    @GetMapping("/{id}")
    @Operation(summary = "One referral with its full status history")
    public ReferralDtos.Response getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return referralService.getById(id, principal.getId(), isAdmin);
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw your own pending referral")
    public ReferralDtos.Response withdraw(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        return referralService.withdraw(id, principal.getId());
    }

    // ---------- navigator side: ADMIN only ----------

    /**
     * Oldest first, deliberately. The queue exists so that nothing ages
     * out of sight, which a newest-first ordering would guarantee.
     */
    @GetMapping("/queue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Referrals awaiting review, oldest first")
    public Page<ReferralDtos.Response> queue(
            @RequestParam(defaultValue = "PENDING") ReferralRequest.Status status,
            @PageableDefault(size = 20) Pageable pageable) {

        return referralService.queue(status, pageable);
    }

    /**
     * Resolve a referral.
     *
     * The response reports the status that was ACTUALLY applied. Asking
     * for ACCEPTED when the provider's last slot has gone returns
     * WAITLISTED - the caller is never told something different from
     * what happened.
     */
    @PostMapping("/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Accept, waitlist or decline a pending referral")
    public ReferralDtos.Response decide(
            @PathVariable Long id,
            @Valid @RequestBody ReferralDtos.DecisionRequest decision,
            @AuthenticationPrincipal UserPrincipal principal) {

        return referralService.decide(id, decision, principal.getId());
    }
}
