package com.flmentalhealth.service;

import com.flmentalhealth.TestFixtures;
import com.flmentalhealth.dto.ProfileDtos;
import com.flmentalhealth.entity.ClientProfile;
import com.flmentalhealth.entity.InsurancePlan;
import com.flmentalhealth.entity.User;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.repository.ClientProfileRepository;
import com.flmentalhealth.repository.CountyRepository;
import com.flmentalhealth.repository.InsurancePlanRepository;
import com.flmentalhealth.repository.LanguageRepository;
import com.flmentalhealth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProfileService - the person's own stated preferences.
 *
 * WHAT IS NOT HERE IS THE POINT. There is no diagnosis, no assessment,
 * no clinical history anywhere in these tests, because there is none in
 * the entity (ADR-0004). County, language, coverage and contact
 * preference are all self-described and all optional.
 *
 * Note also that no method in this service takes a user id from a
 * request - every one takes it from the caller, which the controller
 * reads from the token. There is no parameter anyone could change to
 * reach someone else's profile, which is why there is no
 * "forbidden" test in this class: the shape of the API makes the case
 * unreachable.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService")
class ProfileServiceTest {

    @Mock private ClientProfileRepository profileRepository;
    @Mock private UserRepository userRepository;
    @Mock private CountyRepository countyRepository;
    @Mock private LanguageRepository languageRepository;
    @Mock private InsurancePlanRepository insurancePlanRepository;

    @InjectMocks private ProfileService service;

    private User client;

    @BeforeEach
    void setUp() {
        client = TestFixtures.client(100L);
    }

    private ProfileDtos.Request request(Long countyId, Long languageId,
                                        Long planId, String contact) {
        return new ProfileDtos.Request(
                "305-555-0101", countyId, languageId, planId, true, contact);
    }

    // =================================================================
    // Reading
    // =================================================================

    /**
     * "You have not filled this in yet" is not an error. Returning 404
     * would make the frontend treat an empty profile as a failure and
     * show an error screen to someone who has simply not saved
     * anything.
     */
    @Test
    @DisplayName("a user who has saved nothing gets an empty profile, not a 404")
    void get_returnsEmptyProfileWhenNoneSaved() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(client));
        when(profileRepository.findById(100L)).thenReturn(Optional.empty());

        ProfileDtos.Response response = service.get(100L);

        assertThat(response.userId()).isEqualTo(100L);
        assertThat(response.fullName()).isEqualTo("Alicia Moreno");
        assertThat(response.email()).isEqualTo("alicia.moreno@example.com");
        assertThat(response.phone()).isNull();
        assertThat(response.preferredCounty()).isNull();
        assertThat(response.preferredLanguage()).isNull();
        assertThat(response.insurancePlan()).isNull();
        assertThat(response.prefersTelehealth()).isFalse();
        // Still a usable default rather than null, so the form has
        // something to select.
        assertThat(response.contactPreference()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("a saved profile comes back with preference names, not ids")
    void get_mapsPreferenceNames() {
        ClientProfile profile = new ClientProfile(client);
        profile.setPhone("305-555-0101");
        profile.setPreferredCounty(TestFixtures.miamiDade());
        profile.setPreferredLanguage(TestFixtures.language(2L, "Spanish"));
        profile.setInsurancePlan(TestFixtures.plan(
                3L, "Example Medicaid MCO", InsurancePlan.PlanType.MEDICAID));
        profile.setPrefersTelehealth(true);
        profile.setContactPreference(ClientProfile.ContactPreference.TEXT);

        when(userRepository.findById(100L)).thenReturn(Optional.of(client));
        when(profileRepository.findById(100L)).thenReturn(Optional.of(profile));

        ProfileDtos.Response response = service.get(100L);

        assertThat(response.phone()).isEqualTo("305-555-0101");
        assertThat(response.preferredCounty()).isEqualTo("Miami-Dade");
        assertThat(response.preferredLanguage()).isEqualTo("Spanish");
        assertThat(response.insurancePlan()).isEqualTo("Example Medicaid MCO");
        assertThat(response.prefersTelehealth()).isTrue();
        assertThat(response.contactPreference()).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("an unknown user id is a 404")
    void get_throwsWhenUserMissing() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User 404");
    }

    // =================================================================
    // Writing
    // =================================================================

    /**
     * The first save creates the row. The profile shares its primary
     * key with the user (@MapsId), so a second profile for one user is
     * structurally impossible - there is no second column to be unique
     * on.
     */
    @Test
    @DisplayName("the first save creates the profile bound to that user")
    void update_createsProfileOnFirstSave() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(client));
        when(profileRepository.findById(100L)).thenReturn(Optional.empty());
        when(countyRepository.findById(13L))
                .thenReturn(Optional.of(TestFixtures.miamiDade()));
        when(languageRepository.findById(2L))
                .thenReturn(Optional.of(TestFixtures.language(2L, "Spanish")));
        when(insurancePlanRepository.findById(3L)).thenReturn(Optional.of(
                TestFixtures.plan(3L, "Example Medicaid MCO",
                        InsurancePlan.PlanType.MEDICAID)));
        when(profileRepository.save(any(ClientProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProfileDtos.Response response =
                service.update(100L, request(13L, 2L, 3L, "PHONE"));

        assertThat(response.preferredCounty()).isEqualTo("Miami-Dade");
        assertThat(response.preferredLanguage()).isEqualTo("Spanish");
        assertThat(response.contactPreference()).isEqualTo("PHONE");

        ArgumentCaptor<ClientProfile> captor =
                ArgumentCaptor.forClass(ClientProfile.class);
        verify(profileRepository).save(captor.capture());
        assertThat(captor.getValue().getUser().getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("saving again updates the existing row rather than adding one")
    void update_reusesExistingProfile() {
        ClientProfile existing = new ClientProfile(client);
        existing.setPhone("old number");

        when(userRepository.findById(100L)).thenReturn(Optional.of(client));
        when(profileRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(ClientProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.update(100L, request(null, null, null, "EMAIL"));

        assertThat(existing.getPhone()).isEqualTo("305-555-0101");

        ArgumentCaptor<ClientProfile> captor =
                ArgumentCaptor.forClass(ClientProfile.class);
        verify(profileRepository).save(captor.capture());
        // The same object, not a second profile for the same user.
        assertThat(captor.getValue()).isSameAs(existing);
    }

    /**
     * Each preference is independently optional - someone may know
     * their county but not their plan. A null id CLEARS that
     * preference rather than being ignored, so a person can take one
     * back off without clearing the rest.
     */
    @Test
    @DisplayName("null ids clear their preferences without touching the others")
    void update_nullIdsClearPreferences() {
        ClientProfile existing = new ClientProfile(client);
        existing.setPreferredCounty(TestFixtures.miamiDade());
        existing.setPreferredLanguage(TestFixtures.language(2L, "Spanish"));

        when(userRepository.findById(100L)).thenReturn(Optional.of(client));
        when(profileRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(languageRepository.findById(2L))
                .thenReturn(Optional.of(TestFixtures.language(2L, "Spanish")));
        when(profileRepository.save(any(ClientProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // County dropped, language kept.
        ProfileDtos.Response response =
                service.update(100L, request(null, 2L, null, "EMAIL"));

        assertThat(response.preferredCounty()).isNull();
        assertThat(response.preferredLanguage()).isEqualTo("Spanish");
        assertThat(response.insurancePlan()).isNull();
        // No lookup is attempted for a preference that was cleared.
        verify(countyRepository, never()).findById(any());
    }

    @Test
    @DisplayName("an unknown county id is a 404, not a silently dropped preference")
    void update_throwsWhenCountyMissing() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(client));
        when(profileRepository.findById(100L)).thenReturn(Optional.empty());
        when(countyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(100L, request(999L, null, null, "EMAIL")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("County 999");

        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unknown insurance plan id is a 404")
    void update_throwsWhenPlanMissing() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(client));
        when(profileRepository.findById(100L)).thenReturn(Optional.empty());
        when(insurancePlanRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(100L, request(null, null, 999L, "EMAIL")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Insurance plan 999");
    }

    @Test
    @DisplayName("an omitted contact preference defaults to email")
    void update_defaultsContactPreferenceToEmail() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(client));
        when(profileRepository.findById(100L)).thenReturn(Optional.empty());
        when(profileRepository.save(any(ClientProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProfileDtos.Response response =
                service.update(100L, request(null, null, null, null));

        assertThat(response.contactPreference()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("an invalid contact preference names the valid values")
    void update_rejectsUnknownContactPreference() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(client));
        when(profileRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.update(100L, request(null, null, null, "SMOKE_SIGNAL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EMAIL")
                .hasMessageContaining("PHONE")
                .hasMessageContaining("TEXT");

        verify(profileRepository, never()).save(any());
    }
}
