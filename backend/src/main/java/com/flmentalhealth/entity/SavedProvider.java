package com.flmentalhealth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * An entry on a user's shortlist.
 *
 * WHY A SURROGATE ID, unlike the four pure join tables: this is a real
 * entity. It has its own createdAt and note, and it is read directly
 * rather than only ever traversed. A composite primary key on an entity
 * requires @IdClass or @EmbeddedId, which is more machinery than the
 * case justifies - so the table carries an id plus
 * UNIQUE (user_id, provider_id), which prevents the duplicate just as
 * effectively.
 *
 * The pure join tables have no entity class at all, so a composite PK
 * costs nothing there and they use one.
 */
@Entity
@Table(
        name = "saved_providers",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_saved_providers",
                columnNames = {"user_id", "provider_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class SavedProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Size(max = 255)
    @Column(name = "note", length = 255)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SavedProvider(User user, Provider provider, String note) {
        this.user = user;
        this.provider = provider;
        this.note = note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SavedProvider other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SavedProvider{id=" + id + "}";
    }
}
