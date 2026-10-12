package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The clinic or agency a provider works at.
 *
 * Carries the address, and therefore the county. That is why location
 * search joins provider -> organization -> county rather than
 * duplicating a county FK onto every provider row: a provider works
 * somewhere, and the somewhere has the address.
 */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization {

    /** Referenced as Organization.OrgType. */
    public enum OrgType {
        PRIVATE_PRACTICE,
        GROUP_PRACTICE,
        COMMUNITY_MENTAL_HEALTH_CENTER,
        HOSPITAL,
        CRISIS_STABILIZATION_UNIT,
        RESIDENTIAL,
        FQHC,
        TELEHEALTH_ONLY,
        NONPROFIT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 160)
    @Column(name = "name", nullable = false, unique = true, length = 160)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "org_type", nullable = false, length = 40)
    private OrgType orgType = OrgType.PRIVATE_PRACTICE;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "county_id", nullable = false)
    private County county;

    @Size(max = 160)
    @Column(name = "address_line1", length = 160)
    private String addressLine1;

    @Size(max = 160)
    @Column(name = "address_line2", length = 160)
    private String addressLine2;

    @Size(max = 80)
    @Column(name = "city", length = 80)
    private String city;

    @Size(max = 10)
    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Size(max = 30)
    @Column(name = "phone", length = 30)
    private String phone;

    @Size(max = 255)
    @Column(name = "website", length = 255)
    private String website;

    /**
     * Florida's involuntary examination law is the Baker Act, and
     * designated receiving facilities are a real category in the state
     * system.
     *
     * This flag is INFORMATIONAL ONLY. Nothing in this application
     * initiates, recommends, or processes an involuntary examination -
     * it exists so a directory entry can be accurate about what kind of
     * facility it is.
     */
    @Column(name = "baker_act_receiving_facility", nullable = false)
    private boolean bakerActReceivingFacility = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Inverse side - Provider owns the relationship via its
     * organization_id column. No cascade: deleting an organization must
     * not cascade into providers, and the database enforces that with
     * ON DELETE RESTRICT.
     */
    @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
    private List<Provider> providers = new ArrayList<>();

    /** Single-line address for display. */
    public String getDisplayAddress() {
        StringBuilder sb = new StringBuilder();
        if (addressLine1 != null) sb.append(addressLine1);
        if (addressLine2 != null) sb.append(", ").append(addressLine2);
        if (city != null)         sb.append(", ").append(city);
        if (postalCode != null)   sb.append(" ").append(postalCode);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Organization other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** County and providers excluded - both would trigger a lazy load. */
    @Override
    public String toString() {
        return "Organization{id=" + id + ", name='" + name + "'}";
    }
}
