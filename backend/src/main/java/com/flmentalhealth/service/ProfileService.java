package com.flmentalhealth.service;

import com.flmentalhealth.dto.ProfileDtos;
import com.flmentalhealth.entity.*;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in person's stated preferences, used to pre-fill search
 * filters.
 *
 * CONTACT AND PREFERENCE DATA ONLY - no diagnoses, no assessments, no
 * clinical history. See ADR 0004.
 *
 * Every method takes the user id from the caller, which the controller
 * reads from the JWT. There is no method here that can touch another
 * person's profile, because there is no way to name one.
 */
@Service
@Transactional(readOnly = true)
public class ProfileService {

    private static final String NOT_FOUND = " not found";

    private final ClientProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final CountyRepository countyRepository;
    private final LanguageRepository languageRepository;
    private final InsurancePlanRepository insurancePlanRepository;

    public ProfileService(ClientProfileRepository profileRepository,
                          UserRepository userRepository,
                          CountyRepository countyRepository,
                          LanguageRepository languageRepository,
                          InsurancePlanRepository insurancePlanRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.countyRepository = countyRepository;
        this.languageRepository = languageRepository;
        this.insurancePlanRepository = insurancePlanRepository;
    }

    /**
     * Returns the profile, or an empty one for a user who has never
     * saved preferences. Returning 404 for "you have not filled this in
     * yet" would make the frontend handle a non-error as an error.
     */
    public ProfileDtos.Response get(Long userId) {
        User user = findUser(userId);

        return profileRepository.findById(userId)
                .map(this::toResponse)
                .orElseGet(() -> emptyResponse(user));
    }

    /** Creates the profile on first save, updates it thereafter. */
    @Transactional
    public ProfileDtos.Response update(Long userId, ProfileDtos.Request request) {

        User user = findUser(userId);

        ClientProfile profile = profileRepository.findById(userId)
                .orElseGet(() -> new ClientProfile(user));

        profile.setPhone(request.phone());
        profile.setPrefersTelehealth(request.prefersTelehealth());
        profile.setContactPreference(parseContact(request.contactPreference()));

        // Each preference is optional; null clears it.
        profile.setPreferredCounty(
                request.preferredCountyId() == null ? null
                        : countyRepository.findById(request.preferredCountyId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "County " + request.preferredCountyId() + NOT_FOUND)));

        profile.setPreferredLanguage(
                request.preferredLanguageId() == null ? null
                        : languageRepository.findById(request.preferredLanguageId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Language " + request.preferredLanguageId() + NOT_FOUND)));

        profile.setInsurancePlan(
                request.insurancePlanId() == null ? null
                        : insurancePlanRepository.findById(request.insurancePlanId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Insurance plan " + request.insurancePlanId() + NOT_FOUND)));

        return toResponse(profileRepository.save(profile));
    }

    // ---------- helpers ----------

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User " + id + NOT_FOUND));
    }

    private ClientProfile.ContactPreference parseContact(String value) {
        if (value == null) return ClientProfile.ContactPreference.EMAIL;
        try {
            return ClientProfile.ContactPreference.valueOf(value);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException(
                    "'" + value + "' is not a valid contact preference. Valid values: "
                            + java.util.Arrays.toString(
                                    ClientProfile.ContactPreference.values()));
        }
    }

    private ProfileDtos.Response toResponse(ClientProfile p) {
        User u = p.getUser();
        return new ProfileDtos.Response(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                p.getPhone(),
                p.getPreferredCounty() == null ? null : p.getPreferredCounty().getName(),
                p.getPreferredLanguage() == null ? null : p.getPreferredLanguage().getName(),
                p.getInsurancePlan() == null ? null : p.getInsurancePlan().getName(),
                p.isPrefersTelehealth(),
                p.getContactPreference().name());
    }

    /** What a user who has saved nothing yet gets back. */
    private ProfileDtos.Response emptyResponse(User u) {
        return new ProfileDtos.Response(
                u.getId(), u.getFullName(), u.getEmail(),
                null, null, null, null, false,
                ClientProfile.ContactPreference.EMAIL.name());
    }
}
