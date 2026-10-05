package com.flmentalhealth.controller;

import com.flmentalhealth.dto.ProfileDtos;
import com.flmentalhealth.security.UserPrincipal;
import com.flmentalhealth.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The signed-in person's own preferences.
 *
 * Note there is no {id} anywhere in these paths. The identity comes
 * from the token, so there is no parameter anyone could change to read
 * or edit someone else's profile.
 */
@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile", description = "The signed-in user's stated preferences")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "Read your own profile")
    public ProfileDtos.Response get(@AuthenticationPrincipal UserPrincipal principal) {
        return profileService.get(principal.getId());
    }

    @PutMapping
    @Operation(summary = "Create or update your own profile")
    public ProfileDtos.Response update(
            @Valid @RequestBody ProfileDtos.Request request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return profileService.update(principal.getId(), request);
    }
}
