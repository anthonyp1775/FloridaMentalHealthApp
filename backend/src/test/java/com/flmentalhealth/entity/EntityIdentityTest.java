package com.flmentalhealth.entity;

import com.flmentalhealth.TestFixtures;
import com.flmentalhealth.entity.ReferralRequest.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The equality contract, applied to every entity in the model.
 *
 * WHY THIS IS NOT BOILERPLATE. JPA entity equality is one of the few
 * places where a three-line method decides whether the application
 * works. Every entity here follows the same identifier-based rule:
 *
 *     this == o                 -> true
 *     not the same type         -> false
 *     id is null                -> false, ALWAYS
 *     otherwise                 -> compare ids
 *
 * The third clause is the one that matters. Two unsaved entities both
 * have a null id, and if null-equals-null were treated as a match, any
 * two new objects of the same type would be "equal" - and adding both
 * to a Set would silently keep only one. That is a data-loss bug that
 * looks like a UI bug.
 *
 * Running the contract over all thirteen entities in one parameterized
 * test also means a fourteenth cannot be added later with a subtly
 * different implementation and no one notice.
 */
@DisplayName("Entity identity contract")
class EntityIdentityTest {

    // =================================================================
    // One row per entity: an instance with an id, a second instance
    // with the SAME id but different data, and two unsaved instances.
    // =================================================================

    private static Stream<Arguments> everyEntity() {
        User user = TestFixtures.client(100L);
        Provider provider = TestFixtures.provider(1L);

        return Stream.of(
                arguments("Provider",
                        TestFixtures.provider(7L), differentProviderSameId(7L),
                        new Provider(), new Provider()),

                arguments("Organization",
                        TestFixtures.organization(5L, "Example North Clinic"),
                        TestFixtures.organization(5L, "Example South Clinic"),
                        new Organization(), new Organization()),

                arguments("County",
                        TestFixtures.county(3L, "Leon"),
                        TestFixtures.county(3L, "Duval"),
                        new County(), new County()),

                arguments("Specialty",
                        TestFixtures.specialty(4L, "PTSD & Trauma"),
                        TestFixtures.specialty(4L, "Depression"),
                        new Specialty(), new Specialty()),

                arguments("Population",
                        TestFixtures.population(6L, "Adults"),
                        TestFixtures.population(6L, "Adolescents"),
                        new Population(), new Population()),

                arguments("Language",
                        TestFixtures.language(8L, "Spanish"),
                        TestFixtures.language(8L, "Creole"),
                        new Language(), new Language()),

                arguments("InsurancePlan",
                        TestFixtures.plan(9L, "Example Medicaid MCO",
                                InsurancePlan.PlanType.MEDICAID),
                        TestFixtures.plan(9L, "Example Commercial PPO",
                                InsurancePlan.PlanType.COMMERCIAL),
                        new InsurancePlan(), new InsurancePlan()),

                arguments("Role",
                        TestFixtures.role(2L, "ROLE_USER"),
                        TestFixtures.role(2L, "ROLE_ADMIN"),
                        new Role(), new Role()),

                arguments("User",
                        TestFixtures.client(11L),
                        TestFixtures.user(11L, "Dana", "Whitfield",
                                "navigator@carepathfl.org", "ROLE_ADMIN"),
                        new User(), new User()),

                arguments("SavedProvider",
                        TestFixtures.savedProvider(12L, user, provider, "first note"),
                        TestFixtures.savedProvider(12L, user, provider, "second note"),
                        new SavedProvider(), new SavedProvider()),

                arguments("ReferralRequest",
                        TestFixtures.pendingReferral(13L, user, provider),
                        TestFixtures.pendingReferral(13L, user, provider),
                        new ReferralRequest(), new ReferralRequest()),

                arguments("ReferralStatusHistory",
                        historyWithId(14L, "first note"),
                        historyWithId(14L, "second note"),
                        new ReferralStatusHistory(), new ReferralStatusHistory()),

                arguments("ClientProfile",
                        profileFor(15L, "305-555-0101"),
                        profileFor(15L, "305-555-0199"),
                        new ClientProfile(), new ClientProfile()));
    }

    private static Provider differentProviderSameId(Long id) {
        Provider p = TestFixtures.provider(id);
        p.setFirstName("Someone");
        p.setLastName("Else");
        return p;
    }

    private static ReferralStatusHistory historyWithId(Long id, String note) {
        ReferralStatusHistory h = new ReferralStatusHistory(
                Status.PENDING, Status.ACCEPTED, note, TestFixtures.navigator(200L));
        h.setId(id);
        return h;
    }

    /** ClientProfile's identifier is userId - it shares the user's key. */
    private static ClientProfile profileFor(Long userId, String phone) {
        ClientProfile p = new ClientProfile(TestFixtures.client(userId));
        p.setUserId(userId);
        p.setPhone(phone);
        return p;
    }

    // =================================================================
    // The contract
    // =================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEntity")
    @DisplayName("is equal to itself")
    void isEqualToItself(String name, Object saved,
                         Object sameIdDifferentData, Object unsavedA, Object unsavedB) {
        assertThat(saved).isEqualTo(saved);
    }

    /**
     * Same row, different field values, still the same entity. A
     * freshly loaded copy and a modified one must not look like two
     * different records.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEntity")
    @DisplayName("two instances with the same id are the same entity")
    void sameIdMeansSameEntity(String name, Object saved,
                               Object sameIdDifferentData,
                               Object unsavedA, Object unsavedB) {
        assertThat(saved)
                .isEqualTo(sameIdDifferentData)
                .hasSameHashCodeAs(sameIdDifferentData);
    }

    /**
     * THE CLAUSE THAT MATTERS. Both ids are null, and they must still
     * be different objects - otherwise two new records collapse into
     * one the moment either lands in a Set.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEntity")
    @DisplayName("two unsaved instances are NOT equal, even though both ids are null")
    void unsavedInstancesAreDistinct(String name, Object saved,
                                     Object sameIdDifferentData,
                                     Object unsavedA, Object unsavedB) {
        assertThat(unsavedA).isNotEqualTo(unsavedB);

        // And a Set keeps both, which is the practical consequence.
        Set<Object> set = new HashSet<>();
        set.add(unsavedA);
        set.add(unsavedB);
        assertThat(set).hasSize(2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEntity")
    @DisplayName("is never equal to null or to an unrelated type")
    void rejectsNullAndOtherTypes(String name, Object saved,
                                  Object sameIdDifferentData,
                                  Object unsavedA, Object unsavedB) {
        assertThat(saved)
                .isNotNull()
                .isNotEqualTo("a string")
                .isNotEqualTo(42);
    }

    /**
     * An unsaved entity is not even equal to itself by id - but it IS
     * equal to itself by reference, which is what keeps it usable in a
     * collection before it is persisted.
     */
    /*
     * Same actual and expected is the point: this asserts that equals()
     * is reflexive for an entity whose id is still null, the one case
     * where id-based equality falls back on reference identity. Writing
     * it any other way tests something else - asserting on the boolean
     * instead just trades this rule for S5838.
     */
    @SuppressWarnings("java:S5863")
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEntity")
    @DisplayName("an unsaved instance is still equal to itself by reference")
    void unsavedIsStillItself(String name, Object saved,
                              Object sameIdDifferentData,
                              Object unsavedA, Object unsavedB) {
        assertThat(unsavedA).isEqualTo(unsavedA);
    }

    // =================================================================
    // The known limit of this pattern, written down rather than left
    // for someone to discover.
    // =================================================================

    /**
     * hashCode is derived from the id, so it CHANGES when an entity is
     * saved. An entity added to a HashSet while still unsaved is lost
     * once its id is assigned - it lands in the wrong bucket and
     * contains() stops finding it.
     *
     * That is a real hazard of identifier-based hashCode, and this test
     * pins the behavior rather than pretending it does not exist. It is
     * safe in this application because every Set of entities - a
     * provider's specialties, languages, populations and plans, and a
     * user's roles - is filled with rows already loaded from the
     * database, so their ids are set before they are ever added.
     *
     * If that ever stops being true, this test is the place that
     * explains why the symptom looks so strange.
     */
    @Test
    @DisplayName("hashCode changes when an id is assigned - safe here, but documented")
    void hashCodeChangesOnSave() {
        Provider unsaved = new Provider();
        int before = unsaved.hashCode();

        unsaved.setId(42L);                    // what persisting does
        int after = unsaved.hashCode();

        assertThat(after).isNotEqualTo(before);
    }
}
