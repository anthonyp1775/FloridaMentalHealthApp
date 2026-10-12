package com.flmentalhealth;

import com.flmentalhealth.entity.*;
import com.flmentalhealth.entity.ReferralRequest.ContactPreference;
import com.flmentalhealth.security.UserPrincipal;

import java.time.LocalDateTime;

/**
 * Entity builders shared by every test class.
 *
 * WHY THIS EXISTS: a Provider needs an Organization, which needs a
 * County, before any service method can map it to a DTO. Rebuilding
 * that chain in thirty test methods would bury the thing each test is
 * actually asserting.
 *
 * WHY IDS AND TIMESTAMPS ARE SET BY HAND: with a mocked repository
 * nothing is ever persisted, so Hibernate never runs. @GeneratedValue
 * leaves the id null and @CreationTimestamp leaves submittedAt and
 * createdAt null - and the service mappers call .toString() on those.
 * In production Hibernate fills them in during persist(); here the
 * fixture has to stand in for it.
 */
public final class TestFixtures {

    private TestFixtures() {}

    // =================================================================
    // Lookups
    // =================================================================

    public static County county(Long id, String name) {
        County c = new County(name, "Southern", "Example Managing Entity");
        c.setId(id);
        return c;
    }

    public static County miamiDade() {
        return county(13L, "Miami-Dade");
    }

    public static InsurancePlan plan(Long id, String name, InsurancePlan.PlanType type) {
        InsurancePlan p = new InsurancePlan(name, type);
        p.setId(id);
        return p;
    }

    public static Organization organization(Long id, String name) {
        Organization o = new Organization();
        o.setId(id);
        o.setName(name);
        o.setOrgType(Organization.OrgType.COMMUNITY_MENTAL_HEALTH_CENTER);
        o.setCounty(miamiDade());
        o.setCity("Miami");
        return o;
    }

    public static Organization organization() {
        return organization(1L, "Example Behavioral Health Center");
    }

    // =================================================================
    // Users
    // =================================================================

    public static Role role(Long id, String name) {
        Role r = new Role(name);
        r.setId(id);
        return r;
    }

    /** A client: ROLE_USER only. */
    public static User client(Long id) {
        return user(id, "Alicia", "Moreno", "alicia.moreno@example.com", "ROLE_USER");
    }

    /** A navigator: ROLE_USER and ROLE_ADMIN. */
    public static User navigator(Long id) {
        return user(id, "Dana", "Whitfield", "navigator@carepathfl.org",
                "ROLE_USER", "ROLE_ADMIN");
    }

    public static User user(Long id, String first, String last,
                            String email, String... roleNames) {
        User u = new User();
        u.setId(id);
        u.setFirstName(first);
        u.setLastName(last);
        u.setEmail(email);
        // A real stored value is a 60-character BCrypt hash, never a
        // password. Tests that care assert on the shape, not the text.
        u.setPassword("$2a$11$notARealHashJustLongEnoughToLookLikeOne00000000000000000");
        u.setActive(true);

        long roleId = 1L;
        for (String name : roleNames) {
            u.addRole(role(roleId++, name));
        }
        return u;
    }

    public static UserPrincipal principal(User user) {
        return new UserPrincipal(user);
    }

    // =================================================================
    // Providers
    // =================================================================

    /** A provider with capacity: 3 open slots, accepting. */
    public static Provider provider(Long id) {
        return provider(id, 3, true);
    }

    /** A provider at zero capacity and closed to new clients. */
    public static Provider fullProvider(Long id) {
        return provider(id, 0, false);
    }

    public static Provider provider(Long id, int openSlots, boolean accepting) {
        Provider p = new Provider();
        p.setId(id);
        p.setFirstName("Priya");
        p.setLastName("Raman");
        p.setCredential(Provider.Credential.PSYCHIATRIST);
        p.setLicenseNumber("ME" + id);
        p.setOrganization(organization());
        p.setBio("Example bio.");
        p.setYearsExperience(9);
        p.setOffersTelehealth(true);
        p.setOffersInPerson(true);
        p.setOpenSlots(openSlots);
        p.setAcceptingNewClients(accepting);
        p.setTypicalWaitDays(12);
        p.setActive(true);
        return p;
    }

    public static SavedProvider savedProvider(Long id, User user,
                                              Provider provider, String note) {
        SavedProvider s = new SavedProvider(user, provider, note);
        s.setId(id);
        // @CreationTimestamp - see the class comment.
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }

    // =================================================================
    // Referrals
    // =================================================================

    /** A PENDING referral with its creation history row already attached. */
    public static ReferralRequest pendingReferral(Long id, User user, Provider provider) {
        ReferralRequest r = new ReferralRequest(
                user, provider, "Weekday evenings if possible", ContactPreference.EMAIL);
        r.setId(id);
        r.setSubmittedAt(LocalDateTime.now().minusDays(2));
        r.addHistory(ReferralStatusHistory.created(user));
        return r;
    }

    /** A referral that has already been resolved, so it is closed. */
    public static ReferralRequest resolvedReferral(Long id, User user, Provider provider,
                                                   User staff,
                                                   ReferralRequest.Status status) {
        ReferralRequest r = pendingReferral(id, user, provider);
        r.transitionTo(status, "Already handled", staff);
        return r;
    }
}
