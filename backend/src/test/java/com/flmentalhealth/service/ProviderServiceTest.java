package com.flmentalhealth.service;

import com.flmentalhealth.TestFixtures;
import com.flmentalhealth.dto.ProviderDtos;
import com.flmentalhealth.entity.*;
import com.flmentalhealth.exception.ApiExceptions.DuplicateResourceException;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProviderService - search, detail, shortlists and directory upkeep.
 *
 * Two rules here are worth more than the rest of the class:
 *
 *   1. A provider can never be "accepting new clients" with zero open
 *      slots. It is enforced on EVERY write path, so it is tested on
 *      every write path.
 *   2. An absent search filter must disable its clause rather than
 *      match nothing. The 400 that `boolean acceptingOnly` caused in
 *      development is pinned down by
 *      search_absentAcceptingOnlyMeansFalse.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderService")
class ProviderServiceTest {

    @Mock private ProviderRepository providerRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private SpecialtyRepository specialtyRepository;
    @Mock private PopulationRepository populationRepository;
    @Mock private LanguageRepository languageRepository;
    @Mock private InsurancePlanRepository insurancePlanRepository;
    @Mock private SavedProviderRepository savedProviderRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ProviderService service;

    private Provider provider;
    private User client;
    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        provider = TestFixtures.provider(1L);      // 3 slots, accepting
        client = TestFixtures.client(100L);
    }

    /** The minimum valid create/update request: no collections. */
    private ProviderDtos.Request request(String credential,
                                         int openSlots,
                                         boolean accepting,
                                         String licenseNumber) {
        return new ProviderDtos.Request(
                "Priya", "Raman", credential, licenseNumber,
                1L, "Example bio.", 9,
                true, true, accepting, openSlots, 12,
                null, null, null, null);
    }

    // =================================================================
    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("passes every filter through and maps to the summary shape")
        void search_mapsSummary() {
            Page<Provider> page = new PageImpl<>(List.of(provider), pageable, 1);
            when(providerRepository.search(eq(13L), eq(2L), eq(3L), eq(4L), eq(5L),
                    eq(true), eq(true), eq(pageable))).thenReturn(page);

            Page<ProviderDtos.Summary> result = service.search(
                    new ProviderDtos.SearchCriteria(13L, 2L, 3L, 4L, 5L, true, true),
                    pageable);

            assertThat(result.getContent()).hasSize(1);
            ProviderDtos.Summary summary = result.getContent().get(0);
            assertThat(summary.id()).isEqualTo(1L);
            assertThat(summary.fullName()).isEqualTo("Priya Raman");
            assertThat(summary.credential()).isEqualTo("PSYCHIATRIST");
            assertThat(summary.organization())
                    .isEqualTo("Example Behavioral Health Center");
            assertThat(summary.county()).isEqualTo("Miami-Dade");
            assertThat(summary.openSlots()).isEqualTo(3);
            assertThat(summary.acceptingNewClients()).isTrue();
            assertThat(summary.typicalWaitDays()).isEqualTo(12);
        }

        /**
         * A bare /api/providers/search sends no parameters at all, so
         * every criterion arrives null. The flag must resolve to false,
         * and the other six must stay null so the query leaves their
         * clauses off.
         *
         * This is the regression test for a real 400 in development:
         * the field was a primitive `boolean`, and Spring cannot bind
         * an absent parameter to a primitive.
         */
        @Test
        @DisplayName("an absent acceptingOnly means false, and absent filters stay null")
        void search_absentAcceptingOnlyMeansFalse() {
            when(providerRepository.search(isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), eq(false), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(provider), pageable, 1));

            Page<ProviderDtos.Summary> result = service.search(
                    new ProviderDtos.SearchCriteria(
                            null, null, null, null, null, null, null),
                    pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(providerRepository).search(isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), eq(false), eq(pageable));
        }

        @Test
        @DisplayName("an explicit acceptingOnly=false is still false, not null")
        void search_explicitFalseStaysFalse() {
            when(providerRepository.search(isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), eq(false), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            service.search(new ProviderDtos.SearchCriteria(
                    null, null, null, null, null, null, false), pageable);

            verify(providerRepository).search(isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), eq(false), eq(pageable));
        }

        @Test
        @DisplayName("no matches is an empty page, not an exception")
        void search_emptyResultIsNotAnError() {
            when(providerRepository.search(any(), any(), any(), any(), any(),
                    any(), anyBoolean(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            Page<ProviderDtos.Summary> result = service.search(
                    new ProviderDtos.SearchCriteria(99L, null, null, null, null,
                            null, true),
                    pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // =================================================================
    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns full detail with collections sorted by name")
        void getById_sortsCollectionNames() {
            // Added out of order, and stored in a HashSet, so the sort
            // in the mapper is what makes the response stable.
            provider.addSpecialty(TestFixtures.specialty(1L, "PTSD & Trauma"));
            provider.addSpecialty(TestFixtures.specialty(2L, "Anxiety Disorders"));
            provider.addLanguage(TestFixtures.language(1L, "Spanish"));
            provider.addLanguage(TestFixtures.language(2L, "English"));
            provider.addInsurancePlan(TestFixtures.plan(
                    1L, "Example Medicaid MCO", InsurancePlan.PlanType.MEDICAID));

            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

            ProviderDtos.Response response = service.getById(1L);

            assertThat(response.specialties())
                    .containsExactly("Anxiety Disorders", "PTSD & Trauma");
            assertThat(response.languages())
                    .containsExactly("English", "Spanish");
            assertThat(response.insurancePlans())
                    .containsExactly("Example Medicaid MCO");
            assertThat(response.county()).isEqualTo("Miami-Dade");
            assertThat(response.orgType())
                    .isEqualTo("COMMUNITY_MENTAL_HEALTH_CENTER");
            assertThat(response.waitlistCount()).isZero();
        }

        /**
         * A deactivated provider must read as NOT FOUND to a searching
         * user. Returning them with a flag would put someone on the
         * phone to a clinician who has left.
         */
        @Test
        @DisplayName("a deactivated provider is a 404 to a searching user")
        void getById_hidesDeactivatedProvider() {
            provider.setActive(false);
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

            assertThatThrownBy(() -> service.getById(1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("unknown id is a 404")
        void getById_throwsWhenMissing() {
            when(providerRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("404");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("the capacity invariant")
    class CapacityInvariant {

        /**
         * THE RULE THE DIRECTORY DEPENDS ON. Saying someone is
         * available when they are not is worse than saying nothing, so
         * this combination is rejected rather than stored.
         */
        @Test
        @DisplayName("updateCapacity refuses accepting=true with zero open slots")
        void updateCapacity_rejectsAcceptingWithNoSlots() {
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

            assertThatThrownBy(() -> service.updateCapacity(1L,
                    new ProviderDtos.CapacityRequest(0, true, "oops")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(
                            "cannot be accepting new clients with zero open slots");

            // Nothing was changed on the way to failing.
            assertThat(provider.getOpenSlots()).isEqualTo(3);
            assertThat(provider.isAcceptingNewClients()).isTrue();
        }

        @Test
        @DisplayName("zero slots is fine as long as accepting is false")
        void updateCapacity_allowsZeroSlotsWhenClosed() {
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

            ProviderDtos.Response response = service.updateCapacity(1L,
                    new ProviderDtos.CapacityRequest(0, false, "Books closed"));

            assertThat(response.openSlots()).isZero();
            assertThat(response.acceptingNewClients()).isFalse();
        }

        @Test
        @DisplayName("a valid change is applied to the managed entity")
        void updateCapacity_appliesValidChange() {
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

            ProviderDtos.Response response = service.updateCapacity(1L,
                    new ProviderDtos.CapacityRequest(5, true, "Two cancellations"));

            assertThat(response.openSlots()).isEqualTo(5);
            assertThat(provider.getOpenSlots()).isEqualTo(5);
            // No explicit save call - the entity is managed, so the
            // change flushes on commit. Asserting on the entity rather
            // than on a save() verification is the honest check here.
            verify(providerRepository, never()).save(any());
        }

        /** The same guard, reached through create rather than PATCH. */
        @Test
        @DisplayName("create refuses the same contradiction")
        void create_rejectsAcceptingWithNoSlots() {
            when(organizationRepository.findById(1L))
                    .thenReturn(Optional.of(TestFixtures.organization()));

            assertThatThrownBy(() -> service.create(
                    request("LCSW", 0, true, "SW9001")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("zero open slots");

            verify(providerRepository, never()).save(any());
        }

        /** And once more through update. */
        @Test
        @DisplayName("update refuses the same contradiction")
        void update_rejectsAcceptingWithNoSlots() {
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(organizationRepository.findById(1L))
                    .thenReturn(Optional.of(TestFixtures.organization()));

            assertThatThrownBy(() -> service.update(1L,
                    request("LCSW", 0, true, "ME1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("zero open slots");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("directory management")
    class Directory {

        @Test
        @DisplayName("create saves the provider and returns full detail")
        void create_savesProvider() {
            when(organizationRepository.findById(1L))
                    .thenReturn(Optional.of(TestFixtures.organization()));
            when(providerRepository.existsByLicenseNumber("SW9001")).thenReturn(false);
            when(providerRepository.save(any(Provider.class)))
                    .thenAnswer(invocation -> {
                        Provider saved = invocation.getArgument(0);
                        saved.setId(77L);     // stands in for @GeneratedValue
                        return saved;
                    });

            ProviderDtos.Response response =
                    service.create(request("LCSW", 4, true, "SW9001"));

            assertThat(response.id()).isEqualTo(77L);
            assertThat(response.credential()).isEqualTo("LCSW");
            assertThat(response.openSlots()).isEqualTo(4);
            // Empty collections come back as empty lists, never null.
            assertThat(response.specialties()).isEmpty();
            assertThat(response.populations()).isEmpty();
        }

        @Test
        @DisplayName("a license number already in the directory is a 409")
        void create_rejectsDuplicateLicense() {
            when(providerRepository.existsByLicenseNumber("ME1")).thenReturn(true);

            assertThatThrownBy(() -> service.create(
                    request("PSYCHIATRIST", 2, true, "ME1")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already registered");

            verify(providerRepository, never()).save(any());
        }

        @Test
        @DisplayName("an unknown organization is a 404, not a null foreign key")
        void create_throwsWhenOrganizationMissing() {
            when(providerRepository.existsByLicenseNumber("SW9002")).thenReturn(false);
            when(organizationRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(
                    request("LCSW", 2, true, "SW9002")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Organization 1");
        }

        @Test
        @DisplayName("an invalid credential lists the valid ones")
        void create_rejectsUnknownCredential() {
            when(providerRepository.existsByLicenseNumber("XX1")).thenReturn(false);
            when(organizationRepository.findById(1L))
                    .thenReturn(Optional.of(TestFixtures.organization()));

            assertThatThrownBy(() -> service.create(
                    request("WIZARD", 2, true, "XX1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a valid credential")
                    .hasMessageContaining("PSYCHIATRIST");
        }

        /**
         * Silently dropping an id that does not exist would let a typo
         * quietly remove a filter from a provider's profile - they would
         * stop appearing in searches for a specialty they still hold.
         */
        @Test
        @DisplayName("an unknown specialty id fails loudly instead of being dropped")
        void create_failsOnUnknownSpecialtyId() {
            when(providerRepository.existsByLicenseNumber("SW9003")).thenReturn(false);
            when(organizationRepository.findById(1L))
                    .thenReturn(Optional.of(TestFixtures.organization()));
            when(specialtyRepository.findById(888L)).thenReturn(Optional.empty());

            ProviderDtos.Request withBadSpecialty = new ProviderDtos.Request(
                    "Priya", "Raman", "LCSW", "SW9003", 1L, null, 3,
                    true, true, true, 2, 10,
                    List.of(888L), null, null, null);

            assertThatThrownBy(() -> service.create(withBadSpecialty))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Specialty 888");
        }

        @Test
        @DisplayName("update may keep the provider's own license number")
        void update_allowsKeepingOwnLicense() {
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(organizationRepository.findById(1L))
                    .thenReturn(Optional.of(TestFixtures.organization()));

            ProviderDtos.Response response =
                    service.update(1L, request("PSYCHIATRIST", 2, true, "ME1"));

            assertThat(response.licenseNumber()).isEqualTo("ME1");
            // No duplicate check fires when the value has not changed.
            verify(providerRepository, never()).existsByLicenseNumber("ME1");
        }

        @Test
        @DisplayName("update may not take a license number someone else holds")
        void update_rejectsTakingAnotherLicense() {
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(providerRepository.existsByLicenseNumber("ME9999")).thenReturn(true);

            assertThatThrownBy(() -> service.update(1L,
                    request("PSYCHIATRIST", 2, true, "ME9999")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("update replaces collections rather than merging them")
        void update_replacesCollections() {
            provider.addSpecialty(TestFixtures.specialty(1L, "PTSD & Trauma"));

            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(organizationRepository.findById(1L))
                    .thenReturn(Optional.of(TestFixtures.organization()));
            when(specialtyRepository.findById(2L))
                    .thenReturn(Optional.of(TestFixtures.specialty(2L, "Depression")));

            ProviderDtos.Request swap = new ProviderDtos.Request(
                    "Priya", "Raman", "PSYCHIATRIST", "ME1", 1L, null, 9,
                    true, true, true, 3, 12,
                    List.of(2L), null, null, null);

            ProviderDtos.Response response = service.update(1L, swap);

            // PTSD is gone because it was left out of the request. That
            // is what PUT means - the body is the complete intended set.
            assertThat(response.specialties()).containsExactly("Depression");
        }

        /**
         * SOFT delete. A provider referenced by referral history can
         * never be removed - the foreign key is ON DELETE RESTRICT -
         * and losing that history to delete a row would be the wrong
         * trade.
         */
        @Test
        @DisplayName("deactivate hides the provider and closes intake, without deleting")
        void deactivate_isSoftAndClosesIntake() {
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

            service.deactivate(1L);

            assertThat(provider.isActive()).isFalse();
            assertThat(provider.isAcceptingNewClients()).isFalse();
            verify(providerRepository, never()).delete(any());
            verify(providerRepository, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("deactivating an unknown provider is a 404")
        void deactivate_throwsWhenMissing() {
            when(providerRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deactivate(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("shortlist")
    class Shortlist {

        @Test
        @DisplayName("saving records the note and the provider")
        void save_recordsNote() {
            when(savedProviderRepository.existsByUserIdAndProviderId(100L, 1L))
                    .thenReturn(false);
            when(userRepository.findById(100L)).thenReturn(Optional.of(client));
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(savedProviderRepository.save(any(SavedProvider.class)))
                    .thenAnswer(invocation -> {
                        SavedProvider saved = invocation.getArgument(0);
                        saved.setId(5L);
                        saved.setCreatedAt(java.time.LocalDateTime.now());
                        return saved;
                    });

            ProviderDtos.SavedResponse response =
                    service.save(100L, 1L, "Speaks Spanish, close to work");

            assertThat(response.id()).isEqualTo(5L);
            assertThat(response.providerId()).isEqualTo(1L);
            assertThat(response.fullName()).isEqualTo("Priya Raman");
            assertThat(response.note()).isEqualTo("Speaks Spanish, close to work");
            assertThat(response.savedAt()).isNotNull();

            // The entity is built from the token's user, never from a
            // user id in the request body.
            ArgumentCaptor<SavedProvider> captor =
                    ArgumentCaptor.forClass(SavedProvider.class);
            verify(savedProviderRepository).save(captor.capture());
            assertThat(captor.getValue().getUser().getId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("saving the same provider twice is a 409")
        void save_rejectsDuplicate() {
            when(savedProviderRepository.existsByUserIdAndProviderId(100L, 1L))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.save(100L, 1L, null))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already on your list");

            verify(savedProviderRepository, never()).save(any());
        }

        @Test
        @DisplayName("a deactivated provider cannot be added to a list")
        void save_rejectsInactiveProvider() {
            provider.setActive(false);

            when(savedProviderRepository.existsByUserIdAndProviderId(100L, 1L))
                    .thenReturn(false);
            when(userRepository.findById(100L)).thenReturn(Optional.of(client));
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

            assertThatThrownBy(() -> service.save(100L, 1L, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("listSaved returns only that user's entries")
        void listSaved_mapsEntries() {
            SavedProvider saved = TestFixtures.savedProvider(
                    5L, client, provider, "Close to work");
            when(savedProviderRepository.findByUserIdOrderByCreatedAtDesc(100L))
                    .thenReturn(List.of(saved));

            List<ProviderDtos.SavedResponse> result = service.listSaved(100L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).note()).isEqualTo("Close to work");
            assertThat(result.get(0).organization())
                    .isEqualTo("Example Behavioral Health Center");
            verify(savedProviderRepository).findByUserIdOrderByCreatedAtDesc(100L);
        }

        @Test
        @DisplayName("removing something that is not on the list is a 404")
        void unsave_throwsWhenNotOnList() {
            when(savedProviderRepository.findByUserIdAndProviderId(100L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.unsave(100L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not on your list");
        }

        @Test
        @DisplayName("removing deletes exactly the caller's own row")
        void unsave_deletesOwnRow() {
            SavedProvider saved = TestFixtures.savedProvider(
                    5L, client, provider, null);
            when(savedProviderRepository.findByUserIdAndProviderId(100L, 1L))
                    .thenReturn(Optional.of(saved));

            service.unsave(100L, 1L);

            verify(savedProviderRepository).delete(saved);
        }
    }
}
