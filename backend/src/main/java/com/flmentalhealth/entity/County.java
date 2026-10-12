package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * One of Florida's 67 counties.
 *
 * `region` groups counties for filtering. `managingEntity` is the
 * regional behavioral-health contractor that administers state-funded
 * services there - a real feature of how Florida organizes this system,
 * and the kind of detail a generic directory would not carry.
 */
@Entity
@Table(name = "counties")
@Getter
@Setter
@NoArgsConstructor
public class County {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 60)
    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    @NotBlank
    @Size(max = 40)
    @Column(name = "region", nullable = false, length = 40)
    private String region;

    @Size(max = 80)
    @Column(name = "managing_entity", length = 80)
    private String managingEntity;

    public County(String name, String region, String managingEntity) {
        this.name = name;
        this.region = region;
        this.managingEntity = managingEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof County other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "County{id=" + id + ", name='" + name + "'}";
    }
}
