package com.flmentalhealth.entity;

import com.flmentalhealth.TestFixtures;
import com.flmentalhealth.entity.ReferralRequest.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReferralRequest's own behavior, with no service and no mocks.
 *
 * The rule this class exists to enforce is that a status change and its
 * audit row are written together. transitionTo is the only way to move
 * a referral, and it does both - so a status without a history row
 * explaining it is not something a caller can produce by accident.
 */
@DisplayName("ReferralRequest")
class ReferralRequestTest {

    private User client;
    private User navigator;
    private Provider provider;

    @BeforeEach
    void setUp() {
        client = TestFixtures.client(100L);
        navigator = TestFixtures.navigator(200L);
        provider = TestFixtures.provider(1L);
    }

    @Nested
    @DisplayName("status")
    class StatusRules {

        @Test
        @DisplayName("a new request starts PENDING and counts as open")
        void newRequest_isPendingAndOpen() {
            ReferralRequest r = new ReferralRequest(
                    client, provider, "Evenings please",
                    ReferralRequest.ContactPreference.EMAIL);

            assertThat(r.getStatus()).isEqualTo(Status.PENDING);
            assertThat(r.isOpen()).isTrue();
            assertThat(r.getResolvedAt()).isNull();
            assertThat(r.getResolvedBy()).isNull();
        }

        @ParameterizedTest
        @EnumSource(value = Status.class,
                    names = {"ACCEPTED", "WAITLISTED", "DECLINED", "WITHDRAWN", "CLOSED"})
        @DisplayName("every status other than PENDING is closed")
        void onlyPendingIsOpen(Status status) {
            assertThat(status.isOpen()).isFalse();
        }

        @Test
        @DisplayName("PENDING is the only open status")
        void pendingIsOpen() {
            assertThat(Status.PENDING.isOpen()).isTrue();
        }
    }

    @Nested
    @DisplayName("transitionTo")
    class Transitions {

        @Test
        @DisplayName("moves the status and writes the audit row in one call")
        void transition_writesStatusAndHistoryTogether() {
            ReferralRequest r = TestFixtures.pendingReferral(10L, client, provider);

            r.transitionTo(Status.ACCEPTED, "Intake Thursday", navigator);

            assertThat(r.getStatus()).isEqualTo(Status.ACCEPTED);

            // Creation row, then the transition.
            List<ReferralStatusHistory> history = r.getHistory();
            assertThat(history).hasSize(2);

            ReferralStatusHistory row = history.get(1);
            assertThat(row.getFromStatus()).isEqualTo(Status.PENDING);
            assertThat(row.getToStatus()).isEqualTo(Status.ACCEPTED);
            assertThat(row.getNote()).isEqualTo("Intake Thursday");
            assertThat(row.getChangedBy()).isEqualTo(navigator);
            assertThat(row.getCreatedAt()).isNotNull();
            assertThat(row.isCreation()).isFalse();
        }

        @Test
        @DisplayName("resolving records who did it and when")
        void transition_setsResolvedFields() {
            ReferralRequest r = TestFixtures.pendingReferral(11L, client, provider);

            r.transitionTo(Status.DECLINED, "Outside my focus area", navigator);

            assertThat(r.getResolvedAt()).isNotNull();
            assertThat(r.getResolvedBy()).isEqualTo(navigator);
        }

        /**
         * A withdrawal is the client's own action. The history row
         * still attributes it to them, but resolvedBy stays null -
         * putting a staff name against something no staff member did
         * would make the audit trail say something untrue.
         */
        @Test
        @DisplayName("a withdrawal is attributed in history but leaves resolvedBy null")
        void transition_withdrawalIsNotAStaffResolution() {
            ReferralRequest r = TestFixtures.pendingReferral(12L, client, provider);

            r.transitionTo(Status.WITHDRAWN, "Withdrawn by client", client);

            assertThat(r.getStatus()).isEqualTo(Status.WITHDRAWN);
            assertThat(r.getResolvedAt()).isNotNull();
            assertThat(r.getResolvedBy()).isNull();
            assertThat(r.getHistory().get(1).getChangedBy()).isEqualTo(client);
        }

        /**
         * The database enforces the same thing with
         * CHECK (from_status IS NULL OR from_status <> to_status). A
         * "change" that changes nothing is a bug, not an event.
         */
        @Test
        @DisplayName("moving to the status it already holds is rejected")
        void transition_toSameStatusIsRejected() {
            ReferralRequest r = TestFixtures.pendingReferral(13L, client, provider);

            assertThatThrownBy(() -> r.transitionTo(Status.PENDING, "no-op", navigator))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already PENDING");

            assertThat(r.getHistory()).hasSize(1);   // nothing was written
        }

        @Test
        @DisplayName("the latest history row always matches the current status")
        void transition_keepsHistoryAndStatusInStep() {
            ReferralRequest r = TestFixtures.pendingReferral(14L, client, provider);

            r.transitionTo(Status.WAITLISTED, "No evening slots", navigator);
            r.transitionTo(Status.ACCEPTED, "A slot opened", navigator);

            List<ReferralStatusHistory> history = r.getHistory();
            assertThat(history).hasSize(3);
            assertThat(history.get(history.size() - 1).getToStatus())
                    .isEqualTo(r.getStatus());
        }
    }

    @Nested
    @DisplayName("history")
    class History {

        /**
         * addHistory sets BOTH sides of the relationship. Setting only
         * one is the most common JPA bug there is: the object graph
         * looks right in memory and the foreign key is never written.
         */
        @Test
        @DisplayName("adding a row links it back to its referral")
        void addHistory_keepsBothSidesInSync() {
            ReferralRequest r = new ReferralRequest(
                    client, provider, null, ReferralRequest.ContactPreference.PHONE);
            ReferralStatusHistory row = ReferralStatusHistory.created(client);

            r.addHistory(row);

            assertThat(r.getHistory()).containsExactly(row);
            assertThat(row.getReferralRequest()).isSameAs(r);
        }

        @Test
        @DisplayName("the creation row has no previous status")
        void creationRow_hasNoFromStatus() {
            ReferralStatusHistory row = ReferralStatusHistory.created(client);

            assertThat(row.getFromStatus()).isNull();
            assertThat(row.getToStatus()).isEqualTo(Status.PENDING);
            assertThat(row.isCreation()).isTrue();
            assertThat(row.getChangedBy()).isEqualTo(client);
        }

        /**
         * createdAt is assigned in the constructor rather than by
         * @CreationTimestamp. The row is persisted by cascade, which
         * happens at flush - so with @CreationTimestamp this field
         * would still be null while the service was mapping its
         * response, and the mapping would NPE. It did, once.
         */
        @Test
        @DisplayName("createdAt exists from the moment the row does")
        void createdAt_isSetBeforeAnythingIsPersisted() {
            assertThat(ReferralStatusHistory.created(client).getCreatedAt()).isNotNull();
            assertThat(new ReferralStatusHistory(
                    Status.PENDING, Status.ACCEPTED, "note", navigator).getCreatedAt())
                    .isNotNull();
        }

        @Test
        @DisplayName("a history row prints its transition")
        void toString_showsTheTransition() {
            ReferralStatusHistory row = new ReferralStatusHistory(
                    Status.PENDING, Status.ACCEPTED, "note", navigator);

            assertThat(row.toString()).contains("PENDING", "ACCEPTED");
        }
    }

    @Nested
    @DisplayName("identity")
    class Identity {

        @Test
        @DisplayName("two referrals are the same when their ids match")
        void equality_isByIdentity() {
            ReferralRequest a = TestFixtures.pendingReferral(9L, client, provider);
            ReferralRequest b = TestFixtures.pendingReferral(9L, client, provider);

            assertThat(a).isEqualTo(b);
            assertThat(a).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("two unsaved referrals are not equal to each other")
        void equality_unsavedAreDistinct() {
            ReferralRequest a = new ReferralRequest(
                    client, provider, null, ReferralRequest.ContactPreference.EMAIL);
            ReferralRequest b = new ReferralRequest(
                    client, provider, null, ReferralRequest.ContactPreference.EMAIL);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("toString names the referral and its status")
        void toString_namesStatus() {
            assertThat(TestFixtures.pendingReferral(9L, client, provider).toString())
                    .contains("9", "PENDING");
        }
    }
}
