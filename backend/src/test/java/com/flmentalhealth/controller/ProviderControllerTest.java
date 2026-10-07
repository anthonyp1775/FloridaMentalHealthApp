package com.flmentalhealth.controller;

import com.flmentalhealth.TestFixtures;
import com.flmentalhealth.dto.ProviderDtos;
import com.flmentalhealth.exception.ApiExceptions.DuplicateResourceException;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.service.ProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.flmentalhealth.controller.ControllerTestSupport.json;
import static com.flmentalhealth.controller.ControllerTestSupport.mockMvcFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProviderController over HTTP.
 *
 * The test that earns its place here is
 * search_withNoParametersBindsAndSucceeds. Every filter is optional, so
 * a bare /api/providers/search has to work - and in development it did
 * not: one criterion was a primitive `boolean`, Spring cannot bind an
 * absent parameter to a primitive, and the endpoint answered 400. That
 * is a binding failure, which means it can only be caught at this
 * level. A service test would have passed the whole time.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderController")
class ProviderControllerTest {

    @Mock private ProviderService providerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new ProviderController(providerService),
                TestFixtures.principal(TestFixtures.client(100L)));
    }

    private ProviderDtos.Summary summary() {
        return new ProviderDtos.Summary(
                1L, "Priya Raman", "PSYCHIATRIST",
                "Example Behavioral Health Center", "Miami-Dade",
                true, true, 3, 12);
    }

    private ProviderDtos.Response detail(int openSlots, boolean accepting) {
        return new ProviderDtos.Response(
                1L, "Priya", "Raman", "PSYCHIATRIST", "ME1",
                "Example bio.", 9,
                "Example Behavioral Health Center",
                "COMMUNITY_MENTAL_HEALTH_CENTER", "Miami-Dade", "Miami",
                true, true, accepting, openSlots, 0, 12,
                List.of("Example Commercial PPO", "Example Medicaid MCO"));
    }

    // =================================================================
    // Search
    // =================================================================

    /**
     * THE REGRESSION TEST. No query parameters at all - which is what
     * the frontend sends on first load - must bind cleanly and return
     * the unfiltered page.
     */
    @Test
    @DisplayName("GET /api/providers/search with no parameters is 200, not 400")
    void search_withNoParametersBindsAndSucceeds() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(providerService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary()), pageable, 1));

        mockMvc.perform(get("/api/providers/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Priya Raman"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Every criterion arrived null, and the flag resolved to false.
        ArgumentCaptor<ProviderDtos.SearchCriteria> captor =
                ArgumentCaptor.forClass(ProviderDtos.SearchCriteria.class);
        verify(providerService).search(captor.capture(), any());

        ProviderDtos.SearchCriteria criteria = captor.getValue();
        assertThat(criteria.countyId()).isNull();
        assertThat(criteria.acceptingOnly()).isNull();
        assertThat(criteria.acceptingOnlyOrFalse()).isFalse();
    }

    @Test
    @DisplayName("query parameters bind onto the criteria record")
    void search_bindsEveryFilter() throws Exception {
        when(providerService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/providers/search")
                        .param("countyId", "13")
                        .param("insurancePlanId", "4")
                        .param("telehealth", "true")
                        .param("acceptingOnly", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProviderDtos.SearchCriteria> captor =
                ArgumentCaptor.forClass(ProviderDtos.SearchCriteria.class);
        verify(providerService).search(captor.capture(), any());

        ProviderDtos.SearchCriteria criteria = captor.getValue();
        assertThat(criteria.countyId()).isEqualTo(13L);
        assertThat(criteria.insurancePlanId()).isEqualTo(4L);
        assertThat(criteria.telehealth()).isTrue();
        assertThat(criteria.acceptingOnlyOrFalse()).isTrue();
    }

    @Test
    @DisplayName("page and size are honoured")
    void search_honoursPaging() throws Exception {
        when(providerService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 40));

        mockMvc.perform(get("/api/providers/search")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(providerService).search(any(), captor.capture());

        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    // =================================================================
    // Detail
    // =================================================================

    @Test
    @DisplayName("GET /api/providers/{id} returns the plan list as a name array")
    void getById_returnsFullDetail() throws Exception {
        when(providerService.getById(1L)).thenReturn(detail(3, true));

        mockMvc.perform(get("/api/providers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credential").value("PSYCHIATRIST"))
                .andExpect(jsonPath("$.county").value("Miami-Dade"))
                .andExpect(jsonPath("$.insurancePlans",
                        org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.insurancePlans[0]")
                        .value("Example Commercial PPO"));
    }

    @Test
    @DisplayName("an unknown provider is 404")
    void getById_missingIs404() throws Exception {
        when(providerService.getById(999L))
                .thenThrow(new ResourceNotFoundException("Provider 999 not found"));

        mockMvc.perform(get("/api/providers/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Provider 999 not found"));
    }

    /**
     * A leftover {id} placeholder in a request URL used to produce a
     * 500. It is the caller sending something malformed, so it is a
     * 400 - and the message says which value and which parameter.
     */
    @Test
    @DisplayName("a non-numeric id is 400, not 500")
    void getById_nonNumericIdIs400() throws Exception {
        mockMvc.perform(get("/api/providers/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("not a valid value")));

        verify(providerService, never()).getById(anyLong());
    }

    // =================================================================
    // Capacity
    // =================================================================

    /**
     * The invariant, over HTTP: zero slots and "accepting new clients"
     * cannot both be true. The service throws IllegalArgumentException
     * and the handler turns that into a 400 with the reason attached.
     */
    @Test
    @DisplayName("PATCH capacity with zero slots and accepting=true is 400")
    void updateCapacity_contradictionIs400() throws Exception {
        when(providerService.updateCapacity(eq(1L), any()))
                .thenThrow(new IllegalArgumentException(
                        "A provider cannot be accepting new clients with zero open slots"));

        mockMvc.perform(patch("/api/providers/1/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProviderDtos.CapacityRequest(0, true, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "A provider cannot be accepting new clients with zero open slots"));
    }

    @Test
    @DisplayName("PATCH capacity with a valid change is 200")
    void updateCapacity_validChangeIs200() throws Exception {
        when(providerService.updateCapacity(eq(1L), any()))
                .thenReturn(detail(5, true));

        mockMvc.perform(patch("/api/providers/1/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProviderDtos.CapacityRequest(
                                5, true, "Two cancellations"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openSlots").value(5));
    }

    @Test
    @DisplayName("a negative slot count is rejected by validation")
    void updateCapacity_negativeSlotsIs400() throws Exception {
        mockMvc.perform(patch("/api/providers/1/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProviderDtos.CapacityRequest(-1, false, null))))
                .andExpect(status().isBadRequest());

        verify(providerService, never()).updateCapacity(anyLong(), any());
    }

    // =================================================================
    // Shortlist - identity comes from the token, never the URL
    // =================================================================

    /**
     * There is no user id anywhere in this request. The controller takes
     * it from the principal, which is why nobody can add to or read
     * someone else's list by changing a number.
     */
    @Test
    @DisplayName("POST save is 201 and uses the signed-in user's id, not a body field")
    void save_returns201AndUsesPrincipal() throws Exception {
        when(providerService.save(eq(100L), eq(1L), any()))
                .thenReturn(new ProviderDtos.SavedResponse(
                        5L, 1L, "Priya Raman",
                        "Example Behavioral Health Center",
                        "Close to work", "2026-10-04T12:00:00"));

        mockMvc.perform(post("/api/providers/1/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Close to work\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("Close to work"));

        verify(providerService).save(100L, 1L, "Close to work");
    }

    /** The note is optional, so an absent body must not be a 400. */
    @Test
    @DisplayName("POST save works with no body at all")
    void save_worksWithoutABody() throws Exception {
        when(providerService.save(eq(100L), eq(1L), isNull()))
                .thenReturn(new ProviderDtos.SavedResponse(
                        6L, 1L, "Priya Raman",
                        "Example Behavioral Health Center",
                        null, "2026-10-04T12:00:00"));

        mockMvc.perform(post("/api/providers/1/save"))
                .andExpect(status().isCreated());

        verify(providerService).save(100L, 1L, null);
    }

    @Test
    @DisplayName("saving the same provider twice is 409")
    void save_duplicateIs409() throws Exception {
        when(providerService.save(anyLong(), anyLong(), any()))
                .thenThrow(new DuplicateResourceException(
                        "That provider is already on your list"));

        mockMvc.perform(post("/api/providers/1/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":null}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE save is 204 with no body")
    void unsave_returns204() throws Exception {
        mockMvc.perform(delete("/api/providers/1/save"))
                .andExpect(status().isNoContent());

        verify(providerService).unsave(100L, 1L);
    }

    @Test
    @DisplayName("GET saved returns only the signed-in user's list")
    void listSaved_usesPrincipal() throws Exception {
        when(providerService.listSaved(100L)).thenReturn(List.of(
                new ProviderDtos.SavedResponse(
                        5L, 1L, "Priya Raman",
                        "Example Behavioral Health Center",
                        null, "2026-10-04T12:00:00")));

        mockMvc.perform(get("/api/providers/saved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].providerId").value(1));

        verify(providerService).listSaved(100L);
    }
}
