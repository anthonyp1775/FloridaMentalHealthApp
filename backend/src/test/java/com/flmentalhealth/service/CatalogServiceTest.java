package com.flmentalhealth.service;

import com.flmentalhealth.TestFixtures;
import com.flmentalhealth.dto.CatalogDtos;
import com.flmentalhealth.entity.County;
import com.flmentalhealth.entity.InsurancePlan;
import com.flmentalhealth.entity.Organization;
import com.flmentalhealth.exception.ApiExceptions.DuplicateResourceException;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.repository.*;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CatalogService - the reference data the search filters are built from.
 *
 * Three near-identical read surfaces plus two write paths. The reads
 * look trivial, and mostly are, but two things about them matter enough
 * to pin down: they come back SORTED (a dropdown that reorders itself
 * between page loads is unusable), and counties carry the region and
 * Managing Entity that make this directory specific to Florida rather
 * than generic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogService")
class CatalogServiceTest {

    @Mock private CountyRepository countyRepository;
    @Mock private InsurancePlanRepository insurancePlanRepository;
    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks private CatalogService service;

    private final Pageable pageable = PageRequest.of(0, 20);

    private CatalogDtos.OrganizationRequest orgRequest(String name, String type) {
        return new CatalogDtos.OrganizationRequest(
                name, type, 13L,
                "1 Example Way", null, "Miami", "33101",
                "305-555-0100", "https://example.org", false);
    }

    // =================================================================
    // Reads
    // =================================================================

    /**
     * The region and Managing Entity are what make a county row useful.
     * Florida administers state-funded behavioral health through seven
     * regional Managing Entities, so "which one covers this county" is
     * a real question with a real answer.
     */
    @Test
    @DisplayName("counties carry region and managing entity, sorted by name")
    void listCounties_carriesRegionAndManagingEntity() {
        when(countyRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(TestFixtures.miamiDade()));

        List<CatalogDtos.CountyResponse> result = service.listCounties();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Miami-Dade");
        assertThat(result.get(0).region()).isEqualTo("Southern");
        assertThat(result.get(0).managingEntity())
                .isEqualTo("Example Managing Entity");

        // Sorted at the database, not in the browser.
        ArgumentCaptor<Sort> captor = ArgumentCaptor.forClass(Sort.class);
        verify(countyRepository).findAll(captor.capture());
        assertThat(captor.getValue().getOrderFor("name")).isNotNull();
        assertThat(captor.getValue().getOrderFor("name").isAscending()).isTrue();
    }

    /**
     * Plan TYPE is the field the access-gap report groups on, so it has
     * to survive the mapping as a string the caller can read.
     */
    @Test
    @DisplayName("insurance plans expose the plan type")
    void listInsurancePlans_includesPlanType() {
        when(insurancePlanRepository.findAll(any(Sort.class))).thenReturn(List.of(
                TestFixtures.plan(1L, "Example Medicaid MCO",
                        InsurancePlan.PlanType.MEDICAID)));

        List<CatalogDtos.InsurancePlanResponse> result = service.listInsurancePlans();

        assertThat(result.get(0).name()).isEqualTo("Example Medicaid MCO");
        assertThat(result.get(0).planType()).isEqualTo("MEDICAID");
    }

    /**
     * Organizations are the one catalog that can grow without bound, so
     * this is the one that is paginated - and it must exclude
     * deactivated organizations.
     */
    @Test
    @DisplayName("organizations are paginated and exclude deactivated ones")
    void listOrganizations_onlyActiveAndPaged() {
        Page<Organization> page = new PageImpl<>(
                List.of(TestFixtures.organization()), pageable, 1);
        when(organizationRepository.findByActiveTrue(pageable)).thenReturn(page);

        Page<CatalogDtos.OrganizationResponse> result =
                service.listOrganizations(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).county()).isEqualTo("Miami-Dade");
        assertThat(result.getContent().get(0).orgType())
                .isEqualTo("COMMUNITY_MENTAL_HEALTH_CENTER");
        verify(organizationRepository).findByActiveTrue(pageable);
    }

    @Test
    @DisplayName("an unknown organization id is a 404")
    void getOrganization_throwsWhenMissing() {
        when(organizationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrganization(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Organization 404");
    }

    // =================================================================
    // Writes
    // =================================================================

    @Test
    @DisplayName("an unknown county id is a 404, not a null foreign key")
    void createOrganization_throwsWhenCountyMissing() {
        when(organizationRepository.existsByName("Example New Clinic"))
                .thenReturn(false);
        when(countyRepository.findById(13L)).thenReturn(Optional.empty());

        var req = orgRequest("Example New Clinic", "FQHC");
        assertThatThrownBy(() -> service.createOrganization(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("County 13");
    }

    /**
     * Enum.valueOf's own message is "No enum constant ...", which tells
     * the caller nothing about what they could have sent instead.
     */
    @Test
    @DisplayName("an invalid organization type lists the valid ones")
    void createOrganization_rejectsUnknownType() {
        County county = TestFixtures.miamiDade();
        when(organizationRepository.existsByName("Example New Clinic"))
                .thenReturn(false);
        when(countyRepository.findById(13L)).thenReturn(Optional.of(county));

        var req = orgRequest("Example New Clinic", "SPACE_STATION");
        assertThatThrownBy(() -> service.createOrganization(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid organization type")
                .hasMessageContaining("HOSPITAL");
    }

    @Test
    @DisplayName("a duplicate organization name is a 409")
    void createOrganization_rejectsDuplicateName() {
        when(organizationRepository.existsByName("Example Behavioral Health Center"))
                .thenReturn(true);

        var req = orgRequest("Example Behavioral Health Center", "HOSPITAL");
        assertThatThrownBy(() -> service.createOrganization(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(organizationRepository, never()).save(any());
    }

    /**
     * Saving a form without changing the name must not trip the
     * duplicate check against the organization's own row. This is the
     * bug every "unique name" rule has until someone tests for it.
     */
    @Test
    @DisplayName("an organization may be saved under its own existing name")
    void updateOrganization_allowsKeepingOwnName() {
        Organization existing = TestFixtures.organization();
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(countyRepository.findById(13L))
                .thenReturn(Optional.of(TestFixtures.miamiDade()));

        CatalogDtos.OrganizationResponse response = service.updateOrganization(1L,
                orgRequest("Example Behavioral Health Center", "HOSPITAL"));

        assertThat(response.orgType()).isEqualTo("HOSPITAL");
        assertThat(response.city()).isEqualTo("Miami");
        // The name did not change, so no uniqueness check fires.
        verify(organizationRepository, never())
                .existsByName("Example Behavioral Health Center");
    }

    @Test
    @DisplayName("an organization may not be renamed onto another's name")
    void updateOrganization_rejectsTakingAnotherName() {
        Organization existing = TestFixtures.organization();
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(organizationRepository.existsByName("Example Rival Clinic"))
                .thenReturn(true);

        var req = orgRequest("Example Rival Clinic", "HOSPITAL");
        assertThatThrownBy(() -> service.updateOrganization(1L, req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        // The rejected name was never written onto the entity.
        assertThat(existing.getName())
                .isEqualTo("Example Behavioral Health Center");
    }

    @Test
    @DisplayName("updating an unknown organization is a 404")
    void updateOrganization_throwsWhenMissing() {
        when(organizationRepository.findById(404L)).thenReturn(Optional.empty());

        var req = orgRequest("Example New Clinic", "HOSPITAL");
        assertThatThrownBy(() -> service.updateOrganization(404L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Organization 404");
    }
}
