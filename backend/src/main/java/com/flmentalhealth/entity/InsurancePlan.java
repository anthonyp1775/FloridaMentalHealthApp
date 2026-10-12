package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * A payer: a Medicaid managed-care plan, Medicare, a commercial
 * carrier, a marketplace plan, sliding scale, or self-pay.
 *
 * planType is what the access-gap report groups by - the Medicaid
 * versus commercial split is the most useful single number this system
 * produces.
 */
@Entity
@Table(name = "insurance_plans")
@Getter
@Setter
@NoArgsConstructor
public class InsurancePlan {

    /** Referenced as InsurancePlan.PlanType. */
    public enum PlanType {
        MEDICAID, MEDICARE, COMMERCIAL, MARKETPLACE, SLIDING_SCALE, SELF_PAY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /*
     * EnumType.STRING stores "MEDICAID". ORDINAL would store 0, which
     * silently breaks every existing row the day someone reorders the
     * enum constants. Always STRING for a persisted enum.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 30)
    private PlanType planType;

    public InsurancePlan(String name, PlanType planType) {
        this.name = name;
        this.planType = planType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InsurancePlan other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "InsurancePlan{id=" + id + ", name='" + name + "'}";
    }
}
