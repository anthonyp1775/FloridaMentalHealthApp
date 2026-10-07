package com.flmentalhealth.service;

import com.flmentalhealth.dto.ProviderDtos;
import com.flmentalhealth.entity.*;
import com.flmentalhealth.exception.ApiExceptions.DuplicateResourceException;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provider search, detail, shortlists, and directory management.
 *
 * The search is the core of the application - everything else exists to
 * support someone finding a clinician they can actually see.
 */
@Service
@Transactional(readOnly = true)
public class ProviderService {

    private static final String NOT_FOUND = " not found";

    private final ProviderRepository providerRepository;
    private final OrganizationRepository organizationRepository;
    private final InsurancePlanRepository insurancePlanRepository;
    private final SavedProviderRepository savedProviderRepository;
    private final UserRepository userRepository;

    public ProviderService(ProviderRepository providerRepository,
                           OrganizationRepository organizationRepository,
                           InsurancePlanRepository insurancePlanRepository,
                           SavedProviderRepository savedProviderRepository,
                           UserRepository userRepository) {
        this.providerRepository = providerRepository;
        this.organizationRepository = organizationRepository;
        this.insurancePlanRepository = insurancePlanRepository;
        this.savedProviderRepository = savedProviderRepository;
        this.userRepository = userRepository;
    }

    // =================================================================
    // Search
    // =================================================================

    /**
     * Every filter is optional. A null parameter disables its clause, so
     * one query handles all 16 combinations of the four filters rather
     * than a combinatorial pile of derived method names.
     *
     * The repository query fetches organization and county eagerly, so
     * mapping 20 results does not fire 40 extra SELECTs.
     */
    public Page<ProviderDtos.Summary> search(ProviderDtos.SearchCriteria criteria,
                                             Pageable pageable) {
        return providerRepository.search(
                        criteria.countyId(),
                        criteria.insurancePlanId(),
                        criteria.telehealth(),
                        criteria.acceptingOnlyOrFalse(),
                        pageable)
                .map(this::toSummary);
    }

    public ProviderDtos.Response getById(Long id) {
        return toResponse(findActive(id));
    }

    // =================================================================
    // Shortlist - a user's own saved providers
    // =================================================================

    public List<ProviderDtos.SavedResponse> listSaved(Long userId) {
        return savedProviderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toSavedResponse)
                .toList();
    }

    @Transactional
    public ProviderDtos.SavedResponse save(Long userId, Long providerId, String note) {

        if (savedProviderRepository.existsByUserIdAndProviderId(userId, providerId)) {
            throw new DuplicateResourceException(
                    "That provider is already on your list");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User " + userId + NOT_FOUND));

        Provider provider = findActive(providerId);

        SavedProvider saved = savedProviderRepository.save(
                new SavedProvider(user, provider, note));

        return toSavedResponse(saved);
    }

    @Transactional
    public void unsave(Long userId, Long providerId) {
        SavedProvider saved = savedProviderRepository
                .findByUserIdAndProviderId(userId, providerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "That provider is not on your list"));

        savedProviderRepository.delete(saved);
    }

    // =================================================================
    // Directory management - ADMIN only
    // =================================================================

    @Transactional
    public ProviderDtos.Response create(ProviderDtos.Request request) {

        if (request.licenseNumber() != null
                && providerRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new DuplicateResourceException(
                    "License number " + request.licenseNumber()
                            + " is already registered");
        }

        Provider provider = new Provider();
        apply(request, provider);

        return toResponse(providerRepository.save(provider));
    }

    @Transactional
    public ProviderDtos.Response update(Long id, ProviderDtos.Request request) {
        Provider provider = findAny(id);

        // Changing the license number is allowed, but not onto one that
        // belongs to someone else.
        if (request.licenseNumber() != null
                && !request.licenseNumber().equals(provider.getLicenseNumber())
                && providerRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new DuplicateResourceException(
                    "License number " + request.licenseNumber()
                            + " is already registered");
        }

        apply(request, provider);
        return toResponse(provider);
    }

    /**
     * The endpoint a navigator actually uses day to day.
     *
     * Note the guard: a provider cannot be marked as accepting new
     * clients with zero open slots. That contradiction is what makes a
     * directory untrustworthy - someone calls, and the answer is no.
     */
    @Transactional
    public ProviderDtos.Response updateCapacity(
            Long id, ProviderDtos.CapacityRequest request) {

        Provider provider = findAny(id);

        if (request.acceptingNewClients() && request.openSlots() == 0) {
            throw new IllegalArgumentException(
                    "A provider cannot be accepting new clients with zero open slots");
        }

        provider.setOpenSlots(request.openSlots());
        provider.setAcceptingNewClients(request.acceptingNewClients());

        return toResponse(provider);
    }

    /**
     * SOFT delete. A provider referenced by referral history can never
     * be removed - the foreign key is ON DELETE RESTRICT - and losing
     * that history to delete a row would be the wrong trade. Flipping
     * the flag hides them from search and leaves the record intact.
     */
    @Transactional
    public void deactivate(Long id) {
        Provider provider = findAny(id);
        provider.setActive(false);
        provider.setAcceptingNewClients(false);
    }

    // =================================================================
    // Helpers
    // =================================================================

    /** Shared by create and update, so the two cannot drift apart. */
    private void apply(ProviderDtos.Request request, Provider provider) {

        Organization org = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization " + request.organizationId() + NOT_FOUND));

        provider.setFirstName(request.firstName());
        provider.setLastName(request.lastName());
        provider.setCredential(parseCredential(request.credential()));
        provider.setLicenseNumber(request.licenseNumber());
        provider.setOrganization(org);
        provider.setBio(request.bio());
        provider.setYearsExperience(request.yearsExperience());
        provider.setOffersTelehealth(request.offersTelehealth());
        provider.setOffersInPerson(request.offersInPerson());
        provider.setTypicalWaitDays(request.typicalWaitDays());

        // Same invariant as updateCapacity - enforced on every write path.
        if (request.acceptingNewClients() && request.openSlots() == 0) {
            throw new IllegalArgumentException(
                    "A provider cannot be accepting new clients with zero open slots");
        }
        provider.setOpenSlots(request.openSlots());
        provider.setAcceptingNewClients(request.acceptingNewClients());

        /*
         * The collection is REPLACED, not merged. The request carries the
         * complete intended set, so removing a plan is simply leaving its
         * id out - which is what a client expects from PUT.
         */
        provider.setInsurancePlans(resolve(request.insurancePlanIds(),
                insurancePlanRepository::findById, "Insurance plan"));
    }

    /**
     * Turns a list of ids into a set of entities, failing loudly on any
     * id that does not exist. Silently dropping an unknown id would let
     * a typo quietly remove a filter from a provider's profile.
     */
    /*
     * Deliberately Function<Long, ...> rather than the specialised
     * LongFunction. The ids arrive from JSON with no element-level
     * validation, so a null in the list is possible: LongFunction would
     * unbox it into a bare NullPointerException, where findById(null)
     * raises an error that says what went wrong. The boxing this rule
     * objects to costs nothing on lists this size.
     */
    @SuppressWarnings("java:S4276")
    private <T> Set<T> resolve(List<Long> ids,
                               java.util.function.Function<Long, java.util.Optional<T>> finder,
                               String label) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        Set<T> resolved = new HashSet<>();
        for (Long id : ids) {
            resolved.add(finder.apply(id).orElseThrow(
                    () -> new ResourceNotFoundException(label + " " + id + NOT_FOUND)));
        }
        return resolved;
    }

    private Provider.Credential parseCredential(String value) {
        try {
            return Provider.Credential.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException _) {
            throw new IllegalArgumentException(
                    "'" + value + "' is not a valid credential. Valid values: "
                            + java.util.Arrays.toString(Provider.Credential.values()));
        }
    }

    /** Active providers only - what a searching user should ever see. */
    private Provider findActive(Long id) {
        Provider provider = findAny(id);
        if (!provider.isActive()) {
            throw new ResourceNotFoundException("Provider " + id + NOT_FOUND);
        }
        return provider;
    }

    /** Includes deactivated providers - for ADMIN edit paths. */
    private Provider findAny(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Provider " + id + NOT_FOUND));
    }

    // ---------- mapping ----------

    private ProviderDtos.Summary toSummary(Provider p) {
        return new ProviderDtos.Summary(
                p.getId(),
                p.getFullName(),
                p.getCredential().name(),
                p.getOrganization().getName(),
                p.getOrganization().getCounty().getName(),
                p.isOffersTelehealth(),
                p.isAcceptingNewClients(),
                p.getOpenSlots(),
                p.getTypicalWaitDays());
    }

    /**
     * Full detail. The insurance collection is lazy, so it loads here -
     * inside the transaction, which is the only place it is safe with
     * open-in-view disabled.
     */
    private ProviderDtos.Response toResponse(Provider p) {
        Organization o = p.getOrganization();

        return new ProviderDtos.Response(
                p.getId(),
                p.getFirstName(),
                p.getLastName(),
                p.getCredential().name(),
                p.getLicenseNumber(),
                p.getBio(),
                p.getYearsExperience(),
                o.getName(),
                o.getOrgType().name(),
                o.getCounty().getName(),
                o.getCity(),
                p.isOffersTelehealth(),
                p.isOffersInPerson(),
                p.isAcceptingNewClients(),
                p.getOpenSlots(),
                p.getWaitlistCount(),
                p.getTypicalWaitDays(),
                names(p.getInsurancePlans(), InsurancePlan::getName));
    }

    /** Sorted so the UI renders the same order every time. */
    private <T> List<String> names(Set<T> items,
                                   java.util.function.Function<T, String> nameOf) {
        return items.stream()
                .map(nameOf)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private ProviderDtos.SavedResponse toSavedResponse(SavedProvider s) {
        Provider p = s.getProvider();
        return new ProviderDtos.SavedResponse(
                s.getId(),
                p.getId(),
                p.getFullName(),
                p.getOrganization().getName(),
                s.getNote(),
                s.getCreatedAt().toString());
    }
}
