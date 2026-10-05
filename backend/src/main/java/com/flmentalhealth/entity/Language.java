package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * A language a provider offers services in.
 *
 * In Florida, Spanish and Haitian Creole are first-order access
 * barriers rather than nice-to-haves, which is why this is a filterable
 * relationship and not a free-text note on the provider record.
 *
 * NOTE: this is com.flmentalhealth.entity.Language, not java.lang -
 * check the import if the IDE flags something unexpected.
 */
@Entity
@Table(name = "languages")
@Getter
@Setter
@NoArgsConstructor
public class Language {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Size(max = 8)
    @Column(name = "iso_code", length = 8)
    private String isoCode;

    public Language(String name, String isoCode) {
        this.name = name;
        this.isoCode = isoCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Language other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Language{id=" + id + ", name='" + name + "'}";
    }
}
