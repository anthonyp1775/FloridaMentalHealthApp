package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Preferences a person states about themselves, used to pre-fill search
 * filters.
 *
 * CONTACT AND PREFERENCE DATA ONLY. No diagnoses, no assessments, no
 * clinical notes, no treatment history - not anywhere in this class and
 * not anywhere in the schema. That boundary is the subject of ADR 0004
 * and is what keeps this application outside the handling of protected
 * health information entirely.
 */
@Entity
@Table(name = "client_profiles")
@Getter
@Setter
@NoArgsConstructor
public class ClientProfile {

    /** Referenced as ClientProfile.ContactPreference. */
    public enum ContactPreference { EMAIL, PHONE, TEXT }

    /**
     * SHARED PRIMARY KEY. This table's PK is also its FK to users.
     *
     * @MapsId tells Hibernate to take this id from the associated User
     * rather than generating one, which makes a second profile row for
     * the same user structurally impossible - stronger than a unique
     * constraint, because there is no second column to be unique on.
     */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Size(max = 30)
    @Column(name = "phone", length = 30)
    private String phone;

    /*
     * All three preferences are optional and nullable - a person may
     * know their county but not their plan, or neither. ON DELETE SET
     * NULL in the schema means removing a lookup row never destroys a
     * profile.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_county_id")
    private County preferredCounty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_language_id")
    private Language preferredLanguage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_plan_id")
    private InsurancePlan insurancePlan;

    @Column(name = "prefers_telehealth", nullable = false)
    private boolean prefersTelehealth = false;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_preference", nullable = false, length = 20)
    private ContactPreference contactPreference = ContactPreference.EMAIL;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Builds an empty profile bound to a user. */
    public ClientProfile(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClientProfile other)) return false;
        return userId != null && userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "ClientProfile{userId=" + userId + "}";
    }
}
