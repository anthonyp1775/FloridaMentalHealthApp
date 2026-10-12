package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An account holder - either a person seeking care (ROLE_USER) or a
 * navigator / clinic staff member (ROLE_ADMIN). Email is the login id.
 *
 * NOTE ON LOMBOK: only @Getter/@Setter/@NoArgsConstructor here - never
 * @Data or @EqualsAndHashCode on a JPA entity. @Data generates
 * equals/hashCode/toString across ALL fields including collections,
 * which forces lazy loads and can recurse on bidirectional links.
 * equals/hashCode are written by hand below, keyed on the id.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @NotBlank
    @Size(max = 50)
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 120)
    @Column(name = "email", nullable = false, unique = true, length = 120)
    private String email;

    /** BCrypt hash, never plaintext. Always 60 chars. */
    @NotBlank
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Many-to-many through user_roles. User is the OWNING side - it
     * declares @JoinTable, so Hibernate writes join rows when this
     * collection changes.
     *
     * EAGER is deliberate: every authenticated request needs the roles
     * to build authorities, and they are read outside an open session,
     * which is exactly where LazyInitializationException bites.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public void addRole(Role role)    { this.roles.add(role); }
    public void removeRole(Role role) { this.roles.remove(role); }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** Roles excluded - including them would trigger a load. */
    @Override
    public String toString() {
        return "User{id=" + id + ", email='" + email + "'}";
    }
}
