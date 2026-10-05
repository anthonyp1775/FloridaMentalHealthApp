package com.flmentalhealth.entity;

import com.flmentalhealth.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Provider's own behavior, with no service and no mocks.
 *
 * Capacity rules live on the entity rather than in a service, which
 * means they hold no matter who calls them. These tests check them
 * directly - if consumeSlot can be made to go negative here, no amount
 * of transactional care in ReferralService would save the directory.
 */
@DisplayName("Provider")
class ProviderTest {

    @Nested
    @DisplayName("capacity")
    class Capacity {

        @Test
        @DisplayName("consuming a slot decrements the count")
        void consumeSlot_decrements() {
            Provider p = TestFixtures.provider(1L, 3, true);

            p.consumeSlot();

            assertThat(p.getOpenSlots()).isEqualTo(2);
            assertThat(p.isAcceptingNewClients()).isTrue();
        }

        /**
         * The directory must never show "accepting new clients" beside
         * zero openings. Taking the last slot closes intake in the same
         * call, so there is no window in which the two disagree.
         */
        @Test
        @DisplayName("taking the last slot closes intake in the same call")
        void consumeSlot_lastSlotClosesIntake() {
            Provider p = TestFixtures.provider(1L, 1, true);

            p.consumeSlot();

            assertThat(p.getOpenSlots()).isZero();
            assertThat(p.isAcceptingNewClients()).isFalse();
            assertThat(p.isAtCapacity()).isTrue();
        }

        /**
         * Throws rather than clamping to zero. A caller that reaches
         * this without checking capacity has a bug, and silently
         * doing nothing would hide it until someone turned up to an
         * appointment that was never real.
         */
        @Test
        @DisplayName("consuming a slot that does not exist throws rather than clamping")
        void consumeSlot_throwsWhenEmpty() {
            Provider p = TestFixtures.provider(1L, 0, false);

            assertThatThrownBy(p::consumeSlot)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no open slots");

            assertThat(p.getOpenSlots()).isZero();   // never negative
        }

        @Test
        @DisplayName("a provider with slots and an open intake can accept")
        void canAcceptReferral_whenOpen() {
            assertThat(TestFixtures.provider(1L, 2, true).canAcceptReferral()).isTrue();
        }

        @Test
        @DisplayName("zero slots means it cannot accept, whatever the flag says")
        void canAcceptReferral_falseWithoutSlots() {
            Provider p = TestFixtures.provider(1L, 0, true);   // contradictory on purpose

            assertThat(p.canAcceptReferral()).isFalse();
        }

        @Test
        @DisplayName("a closed intake means it cannot accept, even with slots free")
        void canAcceptReferral_falseWhenNotAccepting() {
            assertThat(TestFixtures.provider(1L, 5, false).canAcceptReferral()).isFalse();
        }

        /**
         * A deactivated provider is out of the directory entirely. The
         * check is here as well as in the service so a code path that
         * skipped the service could not resurrect them.
         */
        @Test
        @DisplayName("a deactivated provider cannot accept, whatever its capacity")
        void canAcceptReferral_falseWhenInactive() {
            Provider p = TestFixtures.provider(1L, 5, true);
            p.setActive(false);

            assertThat(p.canAcceptReferral()).isFalse();
        }

        @Test
        @DisplayName("the waitlist grows and shrinks")
        void waitlist_growsAndShrinks() {
            Provider p = TestFixtures.provider(1L, 0, false);

            p.addToWaitlist();
            p.addToWaitlist();
            assertThat(p.getWaitlistCount()).isEqualTo(2);

            p.removeFromWaitlist();
            assertThat(p.getWaitlistCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("the waitlist never goes negative")
        void waitlist_stopsAtZero() {
            Provider p = TestFixtures.provider(1L, 0, false);

            p.removeFromWaitlist();

            assertThat(p.getWaitlistCount()).isZero();
        }
    }

    @Nested
    @DisplayName("credential")
    class CredentialRules {

        /**
         * Which credentials can prescribe medication is the single fact
         * people most often get wrong before booking. It is carried on
         * the enum constant itself, so it cannot drift from a lookup
         * table somewhere else.
         */
        @Test
        @DisplayName("only psychiatrists and psychiatric nurse practitioners prescribe")
        void prescribers_areExactlyTheTwoMedicalCredentials() {
            assertThat(Provider.Credential.PSYCHIATRIST.isPrescriber()).isTrue();
            assertThat(Provider.Credential.PSYCHIATRIC_ARNP.isPrescriber()).isTrue();

            assertThat(Provider.Credential.PSYCHOLOGIST.isPrescriber()).isFalse();
            assertThat(Provider.Credential.LMHC.isPrescriber()).isFalse();
            assertThat(Provider.Credential.LCSW.isPrescriber()).isFalse();
            assertThat(Provider.Credential.LMFT.isPrescriber()).isFalse();
            assertThat(Provider.Credential.REGISTERED_INTERN.isPrescriber()).isFalse();
            assertThat(Provider.Credential.CAP.isPrescriber()).isFalse();
            assertThat(Provider.Credential.PEER_SPECIALIST.isPrescriber()).isFalse();
        }

        /** Every constant answers the question - none throws or returns null. */
        @ParameterizedTest
        @EnumSource(Provider.Credential.class)
        @DisplayName("every credential has a defined prescribing answer")
        void everyCredentialAnswers(Provider.Credential credential) {
            Provider p = TestFixtures.provider(1L);
            p.setCredential(credential);

            assertThat(p.isPrescriber()).isEqualTo(credential.isPrescriber());
        }

        @Test
        @DisplayName("a provider with no credential set is not a prescriber")
        void nullCredentialIsNotAPrescriber() {
            Provider p = new Provider();

            assertThat(p.isPrescriber()).isFalse();
        }
    }

    @Nested
    @DisplayName("collections and identity")
    class CollectionsAndIdentity {

        @Test
        @DisplayName("the helpers add to the right collection")
        void helpers_addToTheRightSet() {
            Provider p = TestFixtures.provider(1L);

            p.addSpecialty(TestFixtures.specialty(1L, "PTSD & Trauma"));
            p.addPopulation(TestFixtures.population(1L, "Adults"));
            p.addLanguage(TestFixtures.language(1L, "Spanish"));
            p.addInsurancePlan(TestFixtures.plan(
                    1L, "Example Medicaid MCO", InsurancePlan.PlanType.MEDICAID));

            assertThat(p.getSpecialties()).hasSize(1);
            assertThat(p.getPopulations()).hasSize(1);
            assertThat(p.getLanguages()).hasSize(1);
            assertThat(p.getInsurancePlans()).hasSize(1);
        }

        @Test
        @DisplayName("full name joins first and last")
        void fullName_joinsNames() {
            assertThat(TestFixtures.provider(1L).getFullName()).isEqualTo("Priya Raman");
        }

        @Test
        @DisplayName("two providers are the same when their ids match")
        void equality_isByIdentity() {
            Provider a = TestFixtures.provider(7L);
            Provider b = TestFixtures.provider(7L);
            b.setFirstName("Someone");            // different data, same row

            assertThat(a).isEqualTo(b);
            assertThat(a).hasSameHashCodeAs(b);
        }

        /**
         * Two unsaved providers are NOT equal, even though both ids are
         * null. Treating them as equal would collapse them into one
         * entry the moment they were added to a Set.
         */
        @Test
        @DisplayName("two unsaved providers are not equal to each other")
        void equality_unsavedAreDistinct() {
            assertThat(new Provider()).isNotEqualTo(new Provider());
        }

        @Test
        @DisplayName("a provider is not equal to something that is not a provider")
        void equality_rejectsOtherTypes() {
            assertThat(TestFixtures.provider(1L)).isNotEqualTo("Priya Raman");
        }

        /**
         * toString leaves out the organization and the four collections
         * on purpose: including them would trigger a lazy load every
         * time anything logged this object, which is the most common
         * cause of mystery queries in a JPA application.
         */
        @Test
        @DisplayName("toString stays shallow so logging cannot trigger a lazy load")
        void toString_doesNotTouchLazyAssociations() {
            String text = TestFixtures.provider(1L).toString();

            assertThat(text).contains("Priya Raman", "PSYCHIATRIST");
            assertThat(text).doesNotContain("Example Behavioral Health Center");
        }
    }
}
