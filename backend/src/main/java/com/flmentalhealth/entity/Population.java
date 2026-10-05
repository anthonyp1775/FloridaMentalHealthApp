package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * Who a provider serves: Children, Adolescents, Adults, Older Adults,
 * Couples, Families, Groups.
 *
 * Deliberately separate from Specialty - "do they see teenagers?" is a
 * different question from "do they treat anxiety?", and users filter on
 * them independently.
 */
@Entity
@Table(name = "populations")
@Getter
@Setter
@NoArgsConstructor
public class Population {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 60)
    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    /** Display only, e.g. "13-17". Not used for any filtering logic. */
    @Size(max = 30)
    @Column(name = "age_range", length = 30)
    private String ageRange;

    public Population(String name, String ageRange) {
        this.name = name;
        this.ageRange = ageRange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Population other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Population{id=" + id + ", name='" + name + "'}";
    }
}
