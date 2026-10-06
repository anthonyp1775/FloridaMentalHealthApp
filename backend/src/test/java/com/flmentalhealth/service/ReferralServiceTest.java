package com.flmentalhealth.service;

import com.flmentalhealth.TestFixtures;
import com.flmentalhealth.dto.ReferralDtos;
import com.flmentalhealth.entity.Provider;
import com.flmentalhealth.entity.ReferralRequest;
import com.flmentalhealth.entity.ReferralRequest.Status;
import com.flmentalhealth.entity.ReferralStatusHistory;
import com.flmentalhealth.entity.User;
import com.flmentalhealth.exception.ApiExceptions.DuplicateReferralException;
import com.flmentalhealth.exception.ApiExceptions.ForbiddenOperationException;
import com.flmentalhealth.exception.ApiExceptions.ResourceNotFoundException;
import com.flmentalhealth.repository.ProviderRepository;
import com.flmentalhealth.repository.ReferralRequestRepository;
import com.flmentalhealth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The most important test class in the project.
 *
 * ReferralService is the only class that has to keep two things true at
 * the same time: a provider's capacity must never be oversubscribed,
 * and no referral may change status without an audit row recording who
 * moved it. Everything here is aimed at one of those two.
 *
 * The case worth demonstrating is
 * {@code decide_downgradesToWaitlistWhenCapacityIsGone}. A navigator
 * asks for ACCEPTED, the provider's last slot has gone, and the service
 * returns WAITLISTED - the status that was actually applied, not the
 * one requested.
 *
 * Repositories are mocked, so nothing here touches MySQL and the whole
 * class runs in milliseconds. What a mock cannot prove is that
 * {@code findByIdForUpdate} really takes a row lock; that is SQL
 * behavior, verified by reading the generated statement. What these
 * tests do prove is that the service ASKS for the locking read on
 * exactly the path that needs it, which is the part that could silently
 * regress in a refactor.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReferralService")
class ReferralServiceTest {

    @Mock private ReferralRequestRepository referralRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ReferralService service;

    private User client;
    private User navigator;
    private Provider provider;

    @BeforeEach
    void setUp() {
        client = TestFixtures.client(100L);
        navigator = TestFixtures.navigator(200L);
        provider = TestFixtures.provider(1L);          // 3 open slots, accepting
    }

    /**
     * Stands in for what Hibernate does on persist: assigns the
     * identity and fills @CreationTimestamp. Without it the service's
     * own mapper would NPE on a null submittedAt - which is exactly the
     * bug that bit the cascaded history row in development.
     */
    private void stubSaveAsIfPersisted(Long assignedId) {
        when(referralRepository.save(any(ReferralRequest.class)))
                .thenAnswer(invocation -> {
                    ReferralRequest saved = invocation.getArgument(0);
                    saved.setId(assignedId);
                    saved.setSubmittedAt(LocalDateTime.now());
                    return saved;
                });
    }

    // =================================================================
    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("creates a PENDING referral with its creation history row")
        void submit_createsPendingReferralWithHistoryRow() {
            when(userRepository.findById(100L)).thenReturn(Optional.of(client));
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(referralRepository.findByUserIdAndProviderIdAndStatus(
                    100L, 1L, Status.PENDING)).thenReturn(List.of());
            stubSaveAsIfPersisted(55L);

            ReferralDtos.Response response = service.submit(100L,
                    new ReferralDtos.Request(1L, "Evenings please", "EMAIL"));

            assertThat(response.id()).isEqualTo(55L);
            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(response.clientName()).isEqualTo("Alicia Moreno");
            assertThat(response.providerName()).isEqualTo("Priya Raman");

            // Exactly one history row, and it records the creation.
            assertThat(response.history()).hasSize(1);
            ReferralDtos.HistoryEntry creation = response.history().get(0);
            assertThat(creation.fromStatus()).isNull();
            assertThat(creation.toStatus()).isEqualTo("PENDING");
            assertThat(creation.changedBy()).isEqualTo("Alicia Moreno");
            assertThat(creation.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("submitting does NOT consume a slot - capacity is checked at review")
        void submit_leavesCapacityUntouched() {
            when(userRepository.findById(100L)).thenReturn(Optional.of(client));
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(referralRepository.findByUserIdAndProviderIdAndStatus(
                    anyLong(), anyLong(), any())).thenReturn(List.of());
            stubSaveAsIfPersisted(56L);

            service.submit(100L, new ReferralDtos.Request(1L, null, "PHONE"));

            assertThat(provider.getOpenSlots()).isEqualTo(3);
            assertThat(provider.getWaitlistCount()).isZero();
        }

        /**
         * MySQL has no partial unique index, so "unique only when
         * status = PENDING" cannot be expressed in the schema. The
         * service enforces it, which is why it needs a test.
         */
        @Test
        @DisplayName("rejects a second open request to the same provider")
        void submit_rejectsDuplicateOpenRequest() {
            ReferralRequest existing =
                    TestFixtures.pendingReferral(9L, client, provider);

            when(userRepository.findById(100L)).thenReturn(Optional.of(client));
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(referralRepository.findByUserIdAndProviderIdAndStatus(
                    100L, 1L, Status.PENDING)).thenReturn(List.of(existing));

            var req = new ReferralDtos.Request(1L, "again", "EMAIL");
            assertThatThrownBy(() -> service.submit(100L, req))
                    .isInstanceOf(DuplicateReferralException.class)
                    .hasMessageContaining("already have a pending request");

            verify(referralRepository, never()).save(any());
        }

        @Test
        @DisplayName("a deactivated provider reads as not found, not as an error")
        void submit_treatsInactiveProviderAsNotFound() {
            Provider retired = TestFixtures.provider(2L);
            retired.setActive(false);

            when(userRepository.findById(100L)).thenReturn(Optional.of(client));
            when(providerRepository.findById(2L)).thenReturn(Optional.of(retired));

            var req = new ReferralDtos.Request(2L, null, "EMAIL");
            assertThatThrownBy(() -> service.submit(100L, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("unknown provider id is a 404, not a null pointer")
        void submit_throwsWhenProviderMissing() {
            when(userRepository.findById(100L)).thenReturn(Optional.of(client));
            when(providerRepository.findById(999L)).thenReturn(Optional.empty());

            var req = new ReferralDtos.Request(999L, null, "EMAIL");
            assertThatThrownBy(() -> service.submit(100L, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("an invalid contact preference names the valid values")
        void submit_rejectsUnknownContactPreference() {
            when(userRepository.findById(100L)).thenReturn(Optional.of(client));
            when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
            when(referralRepository.findByUserIdAndProviderIdAndStatus(
                    anyLong(), anyLong(), any())).thenReturn(List.of());

            var req = new ReferralDtos.Request(1L, null, "CARRIER_PIGEON");
            assertThatThrownBy(() -> service.submit(100L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("EMAIL")
                    .hasMessageContaining("PHONE")
                    .hasMessageContaining("TEXT");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("decide")
    class Decide {

        @Test
        @DisplayName("accepting consumes one open slot and records the transition")
        void decide_acceptConsumesSlotAndWritesHistory() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(10L, client, provider);

            when(referralRepository.findById(10L)).thenReturn(Optional.of(referral));
            when(userRepository.findById(200L)).thenReturn(Optional.of(navigator));
            when(providerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(provider));

            ReferralDtos.Response response = service.decide(10L,
                    new ReferralDtos.DecisionRequest("ACCEPTED", "Intake Thursday"),
                    200L);

            assertThat(response.status()).isEqualTo("ACCEPTED");
            assertThat(response.resolvedBy()).isEqualTo("Dana Whitfield");
            assertThat(response.resolvedAt()).isNotNull();
            assertThat(provider.getOpenSlots()).isEqualTo(2);

            // Creation row plus the transition row.
            assertThat(response.history()).hasSize(2);
            ReferralDtos.HistoryEntry transition = response.history().get(1);
            assertThat(transition.fromStatus()).isEqualTo("PENDING");
            assertThat(transition.toStatus()).isEqualTo("ACCEPTED");
            assertThat(transition.note()).isEqualTo("Intake Thursday");
            assertThat(transition.changedBy()).isEqualTo("Dana Whitfield");
        }

        /**
         * Accepting the LAST slot must also close the provider to new
         * clients. A directory showing "accepting" beside zero openings
         * is the exact failure this whole application exists to avoid.
         */
        @Test
        @DisplayName("consuming the last slot flips acceptingNewClients to false")
        void decide_acceptLastSlotClosesIntake() {
            Provider oneLeft = TestFixtures.provider(3L, 1, true);
            ReferralRequest referral =
                    TestFixtures.pendingReferral(11L, client, oneLeft);

            when(referralRepository.findById(11L)).thenReturn(Optional.of(referral));
            when(userRepository.findById(200L)).thenReturn(Optional.of(navigator));
            when(providerRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(oneLeft));

            service.decide(11L,
                    new ReferralDtos.DecisionRequest("ACCEPTED", null), 200L);

            assertThat(oneLeft.getOpenSlots()).isZero();
            assertThat(oneLeft.isAcceptingNewClients()).isFalse();
        }

        /**
         * THE CASE TO DEMONSTRATE.
         *
         * The navigator asks for ACCEPTED. Between the client
         * submitting and the navigator reviewing, the provider's last
         * slot went. Overbooking would promise an intake that does not
         * exist; failing outright would lose the request. So it
         * resolves to WAITLISTED - and the response says so.
         */
        @Test
        @DisplayName("ACCEPTED becomes WAITLISTED when the last slot is gone")
        void decide_downgradesToWaitlistWhenCapacityIsGone() {
            Provider full = TestFixtures.fullProvider(4L);     // 0 slots, closed
            ReferralRequest referral =
                    TestFixtures.pendingReferral(12L, client, full);

            when(referralRepository.findById(12L)).thenReturn(Optional.of(referral));
            when(userRepository.findById(200L)).thenReturn(Optional.of(navigator));
            when(providerRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(full));

            ReferralDtos.Response response = service.decide(12L,
                    new ReferralDtos.DecisionRequest("ACCEPTED", "Trying to accept"),
                    200L);

            // The status APPLIED, not the one requested.
            assertThat(response.status()).isEqualTo("WAITLISTED");
            assertThat(referral.getStatus()).isEqualTo(Status.WAITLISTED);

            // Capacity never went negative; the waitlist grew instead.
            assertThat(full.getOpenSlots()).isZero();
            assertThat(full.getWaitlistCount()).isEqualTo(1);

            // The reason is on the record, not just in a log line.
            assertThat(response.history()).hasSize(2);
            assertThat(response.history().get(1).note())
                    .contains("No open slots at review time")
                    .contains("Trying to accept");
        }

        /**
         * The lock is the whole defense against two navigators
         * accepting the same last slot. A mock cannot prove the SQL
         * takes a lock, but it can prove the service asks for the
         * locking read rather than reusing the stale copy hanging off
         * the referral - which is what a well-meaning refactor would
         * break.
         */
        @Test
        @DisplayName("re-reads the provider through the locking query, not the stale copy")
        void decide_usesPessimisticReadForCapacity() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(13L, client, provider);

            when(referralRepository.findById(13L)).thenReturn(Optional.of(referral));
            when(userRepository.findById(200L)).thenReturn(Optional.of(navigator));
            when(providerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(provider));

            service.decide(13L,
                    new ReferralDtos.DecisionRequest("ACCEPTED", null), 200L);

            verify(providerRepository).findByIdForUpdate(1L);
            verify(providerRepository, never()).findById(1L);
        }

        @Test
        @DisplayName("waitlisting outright increments the waitlist and keeps slots")
        void decide_waitlistKeepsSlotsAndGrowsWaitlist() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(14L, client, provider);

            when(referralRepository.findById(14L)).thenReturn(Optional.of(referral));
            when(userRepository.findById(200L)).thenReturn(Optional.of(navigator));
            when(providerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(provider));

            ReferralDtos.Response response = service.decide(14L,
                    new ReferralDtos.DecisionRequest("WAITLISTED", "No evening slots"),
                    200L);

            assertThat(response.status()).isEqualTo("WAITLISTED");
            assertThat(provider.getOpenSlots()).isEqualTo(3);
            assertThat(provider.getWaitlistCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("declining touches no capacity at all")
        void decide_declineLeavesCapacityAlone() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(15L, client, provider);

            when(referralRepository.findById(15L)).thenReturn(Optional.of(referral));
            when(userRepository.findById(200L)).thenReturn(Optional.of(navigator));

            ReferralDtos.Response response = service.decide(15L,
                    new ReferralDtos.DecisionRequest("DECLINED", "Outside my focus area"),
                    200L);

            assertThat(response.status()).isEqualTo("DECLINED");
            assertThat(provider.getOpenSlots()).isEqualTo(3);
            assertThat(provider.getWaitlistCount()).isZero();
            // No capacity path means no locking read either.
            verify(providerRepository, never()).findByIdForUpdate(anyLong());
        }

        @Test
        @DisplayName("a referral can only be resolved once")
        void decide_rejectsAlreadyResolvedReferral() {
            ReferralRequest done = TestFixtures.resolvedReferral(
                    16L, client, provider, navigator, Status.ACCEPTED);

            when(referralRepository.findById(16L)).thenReturn(Optional.of(done));

            var req = new ReferralDtos.DecisionRequest("DECLINED", null);
            assertThatThrownBy(() -> service.decide(16L, req, 200L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already ACCEPTED");
        }

        @Test
        @DisplayName("PENDING is not a resolution a navigator can choose")
        void decide_rejectsNonResolvableStatus() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(17L, client, provider);

            when(referralRepository.findById(17L)).thenReturn(Optional.of(referral));

            var req = new ReferralDtos.DecisionRequest("PENDING", null);
            assertThatThrownBy(() -> service.decide(17L, req, 200L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ACCEPTED, WAITLISTED or DECLINED");
        }

        @Test
        @DisplayName("an unknown status names the valid ones instead of failing blankly")
        void decide_rejectsGarbageStatus() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(18L, client, provider);

            when(referralRepository.findById(18L)).thenReturn(Optional.of(referral));

            var req = new ReferralDtos.DecisionRequest("MAYBE", null);
            assertThatThrownBy(() -> service.decide(18L, req, 200L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a valid status");
        }

        @Test
        @DisplayName("unknown referral id is a 404")
        void decide_throwsWhenReferralMissing() {
            when(referralRepository.findById(404L)).thenReturn(Optional.empty());

            var req = new ReferralDtos.DecisionRequest("ACCEPTED", null);
            assertThatThrownBy(() -> service.decide(404L, req, 200L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("404");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        /**
         * A withdrawal is the client's own action, not a staff
         * resolution, so resolvedBy stays null while resolvedAt is set.
         * Attributing it to a navigator would put a staff name against
         * something no staff member did.
         */
        @Test
        @DisplayName("records the client as the actor but leaves resolvedBy null")
        void withdraw_doesNotAttributeToStaff() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(20L, client, provider);

            when(referralRepository.findById(20L)).thenReturn(Optional.of(referral));

            ReferralDtos.Response response = service.withdraw(20L, 100L);

            assertThat(response.status()).isEqualTo("WITHDRAWN");
            assertThat(response.resolvedAt()).isNotNull();
            assertThat(response.resolvedBy()).isNull();

            ReferralDtos.HistoryEntry row = response.history().get(1);
            assertThat(row.changedBy()).isEqualTo("Alicia Moreno");
            assertThat(row.note()).isEqualTo("Withdrawn by client");
        }

        @Test
        @DisplayName("nobody can withdraw someone else's request")
        void withdraw_rejectsNonOwner() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(21L, client, provider);

            when(referralRepository.findById(21L)).thenReturn(Optional.of(referral));

            assertThatThrownBy(() -> service.withdraw(21L, 999L))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("your own referrals");

            assertThat(referral.getStatus()).isEqualTo(Status.PENDING);
        }

        @Test
        @DisplayName("a resolved referral cannot be withdrawn after the fact")
        void withdraw_rejectsClosedReferral() {
            ReferralRequest done = TestFixtures.resolvedReferral(
                    22L, client, provider, navigator, Status.DECLINED);

            when(referralRepository.findById(22L)).thenReturn(Optional.of(done));

            assertThatThrownBy(() -> service.withdraw(22L, 100L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be withdrawn");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("the owner can read their own referral")
        void getById_allowsOwner() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(30L, client, provider);
            when(referralRepository.findById(30L)).thenReturn(Optional.of(referral));

            assertThat(service.getById(30L, 100L, false).id()).isEqualTo(30L);
        }

        @Test
        @DisplayName("staff can read anyone's referral")
        void getById_allowsAdmin() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(31L, client, provider);
            when(referralRepository.findById(31L)).thenReturn(Optional.of(referral));

            assertThat(service.getById(31L, 200L, true).id()).isEqualTo(31L);
        }

        /**
         * The ownership check is the reason changing a number in the URL
         * does not expose someone else's request.
         */
        @Test
        @DisplayName("another client cannot read it by guessing the id")
        void getById_forbidsAnotherClient() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(32L, client, provider);
            when(referralRepository.findById(32L)).thenReturn(Optional.of(referral));

            assertThatThrownBy(() -> service.getById(32L, 777L, false))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("do not have access");
        }

        @Test
        @DisplayName("listMine asks for the caller's own referrals, newest first")
        void listMine_usesNewestFirstQuery() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(33L, client, provider);
            Pageable pageable = PageRequest.of(0, 20);
            Page<ReferralRequest> page = new PageImpl<>(List.of(referral), pageable, 1);

            when(referralRepository.findByUserIdOrderBySubmittedAtDesc(100L, pageable))
                    .thenReturn(page);

            Page<ReferralDtos.Response> result = service.listMine(100L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).id()).isEqualTo(33L);
        }

        /**
         * Oldest first, deliberately. A newest-first queue guarantees
         * that the longest-waiting request is the one nobody sees.
         */
        @Test
        @DisplayName("the queue asks for oldest first")
        void queue_usesOldestFirstQuery() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(34L, client, provider);
            Pageable pageable = PageRequest.of(0, 20);
            Page<ReferralRequest> page = new PageImpl<>(List.of(referral), pageable, 1);

            when(referralRepository.findByStatusOrderBySubmittedAtAsc(
                    Status.PENDING, pageable)).thenReturn(page);

            Page<ReferralDtos.Response> result = service.queue(Status.PENDING, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(referralRepository)
                    .findByStatusOrderBySubmittedAtAsc(Status.PENDING, pageable);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("the invariant that holds the audit trail together")
    class AuditTrail {

        /**
         * Every status change must leave the latest history row's
         * toStatus equal to the referral's current status. That is the
         * same check as query 1 in seed.sql, asserted here in memory so
         * it fails in the build rather than being noticed in SQL later.
         */
        @Test
        @DisplayName("the latest history row always matches the current status")
        void history_latestRowMatchesCurrentStatus() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(40L, client, provider);

            when(referralRepository.findById(40L)).thenReturn(Optional.of(referral));
            when(userRepository.findById(200L)).thenReturn(Optional.of(navigator));
            when(providerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(provider));

            service.decide(40L,
                    new ReferralDtos.DecisionRequest("ACCEPTED", null), 200L);

            List<ReferralStatusHistory> history = referral.getHistory();
            ReferralStatusHistory latest = history.get(history.size() - 1);

            assertThat(latest.getToStatus()).isEqualTo(referral.getStatus());
        }

        /**
         * The database also enforces
         * CHECK (from_status IS NULL OR from_status <> to_status) - a
         * "change" that changes nothing is a bug, not an event.
         */
        @Test
        @DisplayName("transitioning to the current status is rejected outright")
        void transition_toSameStatusIsRejected() {
            ReferralRequest referral =
                    TestFixtures.pendingReferral(41L, client, provider);

            assertThatThrownBy(() ->
                    referral.transitionTo(Status.PENDING, "no-op", navigator))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already PENDING");
        }
    }
}
