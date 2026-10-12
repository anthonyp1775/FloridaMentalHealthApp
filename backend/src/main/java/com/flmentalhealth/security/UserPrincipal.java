package com.flmentalhealth.security;

import com.flmentalhealth.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts our User entity to Spring Security's UserDetails interface.
 *
 * This is the bridge between "a row in the users table" and "the thing
 * Spring Security knows how to authenticate". Keeping it separate means
 * the User entity carries no Spring Security imports, so the domain
 * model does not depend on the security framework.
 *
 * NOTE ON IMPORTS: this imports com.flmentalhealth.entity.User. Spring
 * Security ships its own org.springframework.security.core.userdetails.User,
 * and the IDE often suggests that one first. Importing the wrong one
 * produces errors that point nowhere near the real problem.
 */
/*
 * UserDetails extends Serializable, and the User entity it holds is not.
 * That is deliberate and safe here: the session policy is STATELESS and
 * there is no session store, so a UserPrincipal is never serialized.
 * Marking the field transient would be worse - it would silently null
 * out if anything ever did deserialize one. The cleaner long-term shape
 * is to hold the id, email and authorities rather than the entity.
 */
@SuppressWarnings("java:S1948")
@Getter
public class UserPrincipal implements UserDetails {

    /** The underlying entity, exposed so services can reach the id. */
    private final User user;

    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.user = user;
        /*
         * Role names are stored WITH the ROLE_ prefix in the database
         * (ROLE_USER, ROLE_ADMIN), so they map straight across.
         *
         * Do NOT add the prefix here. Spring Security's hasRole("ADMIN")
         * prepends ROLE_ internally, so a second prefix would produce
         * ROLE_ROLE_ADMIN and every authorization check would silently
         * fail - users could log in but get 403 everywhere.
         */
        this.authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .toList();
    }

    /** Convenience for services that need the database id. */
    public Long getId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /**
     * Spring Security calls this "username"; in this system the login
     * identifier is the email address.
     */
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** Deactivating a user in the database locks them out immediately. */
    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
