package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * A clinical focus area - what a provider treats.
 *
 * These are browsable categories a person can recognize and select for
 * themselves. This is NOT a screening instrument: there is no severity
 * scoring and no diagnostic logic anywhere in the application. See
 * ADR 0004.
 */
@Entity
@Table(name = "specialties")
@Getter
@Setter
@NoArgsConstructor
public class Specialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 80)
    @Column(name = "name", nullable = false, unique = true, length = 80)
    private String name;

    @Size(max = 255)
    @Column(name = "description", length = 255)
    private String description;

    public Specialty(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Specialty other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Specialty{id=" + id + ", name='" + name + "'}";
    }
}
