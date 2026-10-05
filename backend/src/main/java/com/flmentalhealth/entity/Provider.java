package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The searchable core of the application.
 *
 * CAPACITY is the moving part. openSlots is decremented inside
 * ReferralService's @Transactional decision flow; if it has reached
 * zero between a client submitting and a navigator reviewing, the
 * referral resolves to WAITLISTED instead of overbooking. That is the
 * closest thing this system has to a hard invariant.
 */
@Entity
@Table(name = "providers")
@Getter
@Setter
@NoArgsConstructor
public class Provider {

    /**
     * Referenced as Provider.Credential.
     *
     * One per provider, so a column rather than a join table. This
     * matters to users more than it might look: it is what determines
     * who can prescribe medication. These are Florida's actual license
     * types (Ch. 490 psychologists, Ch. 491 LMHC / LCSW / LMFT).
     */
    public enum Credential {
        PSYCHIATRIST(true),         // MD/DO
        PSYCHIATRIC_ARNP(true),     // PMHNP
        PSYCHOLOGIST(false),        // PhD/PsyD - therapy and testing
        LMHC(false),                // Licensed Mental Health Counselor
        LCSW(false),                // Licensed Clinical Social Worker
        LMFT(false),                // Licensed Marriage & Family Therapist
        REGISTERED_INTERN(false),   // RMHCI / RCSWI / RMFTI - supervised
        CAP(false),                 // Certified Addiction Professional
        PEER_SPECIALIST(false);     // Certified Recovery Peer Specialist

        private final boolean prescriber;

        Credential(boolean prescriber) {
            this.prescriber = prescriber;
        }

        /** Whether this credential can prescribe medication. */
        public boolean isPrescriber() {
            return prescriber;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 60)
    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @NotBlank
    @Size(max = 60)
    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "credential", nullable = false, length = 30)
    private Credential credential;

    /**
     * Florida license number. Nullable because peer specialists and
     * registered interns are credentialed differently - but unique when
     * present, which MySQL allows since it does not treat NULLs as
     * duplicates of each other.
     */
    @Size(max = 30)
    @Column(name = "license_number", unique = true, length = 30)
    private String licenseNumber;

    /*
     * LAZY, like every @ManyToOne in this project. JPA defaults to-one
     * relationships to EAGER, which means loading one provider silently
     * fires an extra SELECT for its organization - and listing 20
     * providers fires 20 more. That is the N+1 problem. The service
     * layer decides when the organization is actually needed.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @PositiveOrZero
    @Column(name = "years_experience", nullable = false)
    private int yearsExperience = 0;

    @Column(name = "offers_telehealth", nullable = false)
    private boolean offersTelehealth = false;

    @Column(name = "offers_in_person", nullable = false)
    private boolean offersInPerson = true;

    // ---------- capacity ----------

    @Column(name = "accepting_new_clients", nullable = false)
    private boolean acceptingNewClients = true;

    @PositiveOrZero
    @Column(name = "open_slots", nullable = false)
    private int openSlots = 0;

    @PositiveOrZero
    @Column(name = "waitlist_count", nullable = false)
    private int waitlistCount = 0;

    /** NULL means unknown, which is honest - not the same as zero. */
    @Column(name = "typical_wait_days")
    private Integer typicalWaitDays;

    // ---------- lifecycle ----------

    /** Soft delete. Providers with referral history are never removed. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /*
     * ---------- the four many-to-many relationships ----------
     *
     * Provider is the OWNING side of all four - it declares the
     * @JoinTable, so Hibernate writes join rows when these collections
     * change. The lookup entities carry no reverse collection, which
     * keeps them simple: nothing needs to ask "which providers speak
     * Spanish?" as an object graph, because that question is a query.
     *
     * Set rather than List: a provider cannot have the same specialty
     * twice, and Set gives Hibernate much better delete behavior on
     * join tables than List does.
     */

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "provider_specialties",
            joinColumns = @JoinColumn(name = "provider_id"),
            inverseJoinColumns = @JoinColumn(name = "specialty_id")
    )
    private Set<Specialty> specialties = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "provider_populations",
            joinColumns = @JoinColumn(name = "provider_id"),
            inverseJoinColumns = @JoinColumn(name = "population_id")
    )
    private Set<Population> populations = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "provider_languages",
            joinColumns = @JoinColumn(name = "provider_id"),
            inverseJoinColumns = @JoinColumn(name = "language_id")
    )
    private Set<Language> languages = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "provider_insurance",
            joinColumns = @JoinColumn(name = "provider_id"),
            inverseJoinColumns = @JoinColumn(name = "insurance_plan_id")
    )
    private Set<InsurancePlan> insurancePlans = new HashSet<>();

    // ---------- behavior ----------

    public String getFullName() {
        return firstName + " " + lastName;
    }

    /** True when there is no remaining intake capacity. */
    public boolean isAtCapacity() {
        return openSlots <= 0;
    }

    /**
     * Whether a referral to this provider could be accepted right now.
     * ReferralService calls this INSIDE the transaction, after
     * re-reading the row - checking it anywhere else would be reading
     * a value that may already be stale.
     */
    public boolean canAcceptReferral() {
        return active && acceptingNewClients && openSlots > 0;
    }

    /**
     * Consumes one intake slot. Throws rather than going negative: a
     * caller that reaches this without checking capacity has a bug, and
     * silently clamping to zero would hide it.
     */
    public void consumeSlot() {
        if (openSlots <= 0) {
            throw new IllegalStateException(
                    "Provider " + id + " has no open slots");
        }
        openSlots--;
        if (openSlots == 0) {
            // Keep the flag honest - the directory must never show
            // "accepting new clients" next to zero availability.
            acceptingNewClients = false;
        }
    }

    public void addToWaitlist() {
        waitlistCount++;
    }

    public void removeFromWaitlist() {
        if (waitlistCount > 0) waitlistCount--;
    }

    /** Whether this provider can prescribe medication. */
    public boolean isPrescriber() {
        return credential != null && credential.isPrescriber();
    }

    // ---------- collection helpers ----------

    public void addSpecialty(Specialty s)      { specialties.add(s); }
    public void addPopulation(Population p)    { populations.add(p); }
    public void addLanguage(Language l)        { languages.add(l); }
    public void addInsurancePlan(InsurancePlan p) { insurancePlans.add(p); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Provider other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Collections and the organization are excluded on purpose.
     * Including them would force a lazy load every time anything logged
     * this object - the single most common cause of mystery queries.
     */
    @Override
    public String toString() {
        return "Provider{id=" + id + ", name='" + getFullName()
                + "', credential=" + credential
                + ", openSlots=" + openSlots + "}";
    }
}
