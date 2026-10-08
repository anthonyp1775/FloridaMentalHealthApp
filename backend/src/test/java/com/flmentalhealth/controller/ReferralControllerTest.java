package com.flmentalhealth.controller;

import com.flmentalhealth.TestFixtures;
import com.flmentalhealth.dto.Dtos.ReferralDtos;
import com.flmentalhealth.entity.ReferralRequest;
import com.flmentalhealth.exception.ApiExceptions.DuplicateReferralException;
import com.flmentalhealth.exception.ApiExceptions.ForbiddenOperationException;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.service.ReferralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.flmentalhealth.controller.ControllerTestSupport.json;
import static com.flmentalhealth.controller.ControllerTestSupport.mockMvcFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReferralController over HTTP - both sides of the workflow.
 *
 * The one to point at in a demo is
 * decide_reportsTheStatusActuallyApplied. A navigator POSTs
 * {"status":"ACCEPTED"} and gets back "WAITLISTED", because the
 * provider's last slot had gone. The response reports what happened,
 * not what was asked for, and this test is what stops a future change
 * from quietly echoing the request back instead.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReferralController")
class ReferralControllerTest {

    @Mock private ReferralService referralService;

    /** Signed in as the client who owns the referrals below. */
    private MockMvc asClient;

    /** Signed in as a navigator: ROLE_USER and ROLE_ADMIN. */
    private MockMvc asNavigator;

    @BeforeEach
    void setUp() {
        ReferralController controller = new ReferralController(referralService);
        asClient = mockMvcFor(controller,
                TestFixtures.principal(TestFixtures.client(100L)));
        asNavigator = mockMvcFor(controller,
                TestFixtures.principal(TestFixtures.navigator(200L)));
    }

    private ReferralDtos.Response response(String status, String resolvedBy,
                                            List<ReferralDtos.HistoryEntry> history) {
        return new ReferralDtos.Response(
                55L, status, 1L, "Priya Raman",
                "Example Behavioral Health Center", "Alicia Moreno",
                "Weekday evenings if possible", "EMAIL",
                "2026-10-01T09:00:00",
                resolvedBy == null ? null : "2026-10-04T14:00:00",
                resolvedBy,
                history);
    }

    private ReferralDtos.HistoryEntry creationRow() {
        return new ReferralDtos.HistoryEntry(
                null, "PENDING", "Request submitted by client",
                "Alicia Moreno", "2026-10-01T09:00:00");
    }

    // =================================================================
    // Submitting
    // =================================================================

    /**
     * No user id in the request body. The controller takes it from the
     * principal, which is what makes it impossible to submit a referral
     * as somebody else.
     */
    @Test
    @DisplayName("POST /api/referrals is 201 and attributes it to the signed-in user")
    void submit_returns201AndUsesPrincipal() throws Exception {
        when(referralService.submit(eq(100L), any()))
                .thenReturn(response("PENDING", null, List.of(creationRow())));

        asClient.perform(post("/api/referrals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReferralDtos.Request(
                                1L, "Weekday evenings if possible", "EMAIL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.clientName").value("Alicia Moreno"))
                .andExpect(jsonPath("$.history[0].toStatus").value("PENDING"))
                .andExpect(jsonPath("$.history[0].fromStatus")
                        .value(org.hamcrest.Matchers.nullValue()));

        verify(referralService).submit(eq(100L), any());
    }

    @Test
    @DisplayName("a second open request to the same provider is 409")
    void submit_duplicateIs409() throws Exception {
        when(referralService.submit(anyLong(), any()))
                .thenThrow(new DuplicateReferralException(
                        "You already have a pending request with this provider"));

        asClient.perform(post("/api/referrals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReferralDtos.Request(1L, null, "EMAIL"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "You already have a pending request with this provider"));
    }

    @Test
    @DisplayName("a missing providerId is rejected by validation, before the service")
    void submit_missingProviderIdIs400() throws Exception {
        asClient.perform(post("/api/referrals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\",\"preferredContact\":\"EMAIL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.providerId").exists());

        verify(referralService, never()).submit(anyLong(), any());
    }

    @Test
    @DisplayName("a blank preferredContact is rejected by validation")
    void submit_blankContactIs400() throws Exception {
        asClient.perform(post("/api/referrals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":1,\"preferredContact\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.preferredContact").exists());

        verify(referralService, never()).submit(anyLong(), any());
    }

    // =================================================================
    // The decision - the case worth demonstrating
    // =================================================================

    /**
     * THE DEMO. The request asks for ACCEPTED. The provider's last slot
     * went between submission and review, so the service waitlists it
     * instead - and the 200 carries WAITLISTED, with the reason in the
     * history row.
     *
     * Silently returning the status that was requested would be worse
     * than failing: the navigator would tell the client they had an
     * appointment that does not exist.
     */
    @Test
    @DisplayName("POST decision reports the status actually applied, not the one asked for")
    void decide_reportsTheStatusActuallyApplied() throws Exception {
        ReferralDtos.HistoryEntry downgrade = new ReferralDtos.HistoryEntry(
                "PENDING", "WAITLISTED",
                "No open slots at review time - added to waitlist",
                "Dana Whitfield", "2026-10-04T14:00:00");

        when(referralService.decide(eq(55L), any(), eq(200L)))
                .thenReturn(response("WAITLISTED", "Dana Whitfield",
                        List.of(creationRow(), downgrade)));

        asNavigator.perform(post("/api/referrals/55/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReferralDtos.DecisionRequest(
                                "ACCEPTED", "Trying to accept"))))
                .andExpect(status().isOk())
                // Asked for ACCEPTED; told WAITLISTED.
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andExpect(jsonPath("$.resolvedBy").value("Dana Whitfield"))
                .andExpect(jsonPath("$.history[1].note").value(
                        "No open slots at review time - added to waitlist"));

        // The requested status reached the service unchanged - the
        // downgrade is the service's decision, not the controller's.
        ArgumentCaptor<ReferralDtos.DecisionRequest> captor =
                ArgumentCaptor.forClass(ReferralDtos.DecisionRequest.class);
        verify(referralService).decide(eq(55L), captor.capture(), eq(200L));
        assertThat(captor.getValue().status()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("an accepted decision is 200 with two history rows")
    void decide_acceptReturnsFullHistory() throws Exception {
        ReferralDtos.HistoryEntry accepted = new ReferralDtos.HistoryEntry(
                "PENDING", "ACCEPTED", "Intake Thursday",
                "Dana Whitfield", "2026-10-04T14:00:00");

        when(referralService.decide(eq(55L), any(), eq(200L)))
                .thenReturn(response("ACCEPTED", "Dana Whitfield",
                        List.of(creationRow(), accepted)));

        asNavigator.perform(post("/api/referrals/55/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReferralDtos.DecisionRequest(
                                "ACCEPTED", "Intake Thursday"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.history",
                        org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    @DisplayName("deciding an already-resolved referral is 400")
    void decide_alreadyResolvedIs400() throws Exception {
        when(referralService.decide(anyLong(), any(), anyLong()))
                .thenThrow(new IllegalStateException(
                        "Referral 55 is already ACCEPTED"));

        asNavigator.perform(post("/api/referrals/55/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReferralDtos.DecisionRequest(
                                "DECLINED", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Referral 55 is already ACCEPTED"));
    }

    /**
     * A leftover {id} placeholder in the URL - /api/referrals/{16}/
     * decision - used to come back as a 500. The caller sent something
     * malformed, so it is a 400, and the message names the value and
     * the parameter.
     */
    @Test
    @DisplayName("a non-numeric referral id is 400, not 500")
    void decide_nonNumericIdIs400() throws Exception {
        asNavigator.perform(post("/api/referrals/abc/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReferralDtos.DecisionRequest(
                                "ACCEPTED", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("not a valid value")));

        verify(referralService, never()).decide(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("a blank status is rejected by validation")
    void decide_blankStatusIs400() throws Exception {
        asNavigator.perform(post("/api/referrals/55/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"\",\"note\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    /**
     * GET /api/referrals/{id} is readable by its owner or by staff, so
     * the controller has to tell the service which the caller is. A
     * client principal must arrive as isAdmin=false - getting that
     * backwards would open every referral to everyone.
     */
    @Test
    @DisplayName("a client's request reaches the service with isAdmin=false")
    void getById_clientIsNotAdmin() throws Exception {
        when(referralService.getById(55L, 100L, false))
                .thenReturn(response("PENDING", null, List.of(creationRow())));

        asClient.perform(get("/api/referrals/55"))
                .andExpect(status().isOk());

        verify(referralService).getById(55L, 100L, false);
    }

    @Test
    @DisplayName("a navigator's request reaches the service with isAdmin=true")
    void getById_navigatorIsAdmin() throws Exception {
        when(referralService.getById(55L, 200L, true))
                .thenReturn(response("PENDING", null, List.of(creationRow())));

        asNavigator.perform(get("/api/referrals/55"))
                .andExpect(status().isOk());

        verify(referralService).getById(55L, 200L, true);
    }

    @Test
    @DisplayName("reading someone else's referral is 403, with a message that says so")
    void getById_someoneElsesIs403() throws Exception {
        when(referralService.getById(anyLong(), anyLong(), anyBoolean()))
                .thenThrow(new ForbiddenOperationException(
                        "You do not have access to this referral"));

        asClient.perform(get("/api/referrals/99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You do not have access to this referral"));
    }

    @Test
    @DisplayName("an unknown referral is 404")
    void getById_missingIs404() throws Exception {
        when(referralService.getById(anyLong(), anyLong(), anyBoolean()))
                .thenThrow(new ResourceNotFoundException("Referral 999 not found"));

        asClient.perform(get("/api/referrals/999"))
                .andExpect(status().isNotFound());
    }

    // =================================================================
    // Lists
    // =================================================================

    @Test
    @DisplayName("GET /mine asks for the signed-in user's own referrals")
    void mine_usesPrincipal() throws Exception {
        when(referralService.listMine(eq(100L), any())).thenReturn(
                new PageImpl<>(
                        List.of(response("PENDING", null, List.of(creationRow()))),
                        PageRequest.of(0, 20), 1));

        asClient.perform(get("/api/referrals/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(55))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(referralService).listMine(eq(100L), any());
    }

    /**
     * The queue defaults to PENDING, so the navigator's main screen
     * needs no query parameter at all.
     */
    @Test
    @DisplayName("GET /queue defaults to PENDING")
    void queue_defaultsToPending() throws Exception {
        when(referralService.queue(eq(ReferralRequest.Status.PENDING), any()))
                .thenReturn(new PageImpl<>(
                        List.of(response("PENDING", null, List.of(creationRow()))),
                        PageRequest.of(0, 20), 1));

        asNavigator.perform(get("/api/referrals/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        verify(referralService).queue(eq(ReferralRequest.Status.PENDING), any());
    }

    @Test
    @DisplayName("GET /queue accepts another status")
    void queue_acceptsExplicitStatus() throws Exception {
        when(referralService.queue(eq(ReferralRequest.Status.WAITLISTED), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        asNavigator.perform(get("/api/referrals/queue")
                        .param("status", "WAITLISTED"))
                .andExpect(status().isOk());

        verify(referralService).queue(eq(ReferralRequest.Status.WAITLISTED), any());
    }

    @Test
    @DisplayName("POST withdraw is 200 and uses the signed-in user's id")
    void withdraw_usesPrincipal() throws Exception {
        ReferralDtos.HistoryEntry withdrawn = new ReferralDtos.HistoryEntry(
                "PENDING", "WITHDRAWN", "Withdrawn by client",
                "Alicia Moreno", "2026-10-04T14:00:00");

        // resolvedBy stays null - a withdrawal is not a staff action.
        when(referralService.withdraw(55L, 100L)).thenReturn(
                response("WITHDRAWN", null, List.of(creationRow(), withdrawn)));

        asClient.perform(post("/api/referrals/55/withdraw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.resolvedBy")
                        .value(org.hamcrest.Matchers.nullValue()));

        verify(referralService).withdraw(55L, 100L);
    }

    @Test
    @DisplayName("withdrawing someone else's referral is 403")
    void withdraw_someoneElsesIs403() throws Exception {
        when(referralService.withdraw(anyLong(), anyLong()))
                .thenThrow(new ForbiddenOperationException(
                        "You can only withdraw your own referrals"));

        asClient.perform(post("/api/referrals/99/withdraw"))
                .andExpect(status().isForbidden());
    }
}
