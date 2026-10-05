package com.flmentalhealth.security;

import com.flmentalhealth.entity.User;
import com.flmentalhealth.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells Spring Security how to look a user up.
 *
 * Spring Boot auto-configures an authentication provider around this
 * bean plus the PasswordEncoder bean in SecurityConfig - that is the
 * "Global AuthenticationManager configured with UserDetailsService
 * bean" line in the startup log.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /** Constructor injection, not @Autowired on a field. */
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @Transactional(readOnly = true) matters here even though this is
     * a single read: User.roles is EAGER, so the roles load inside this
     * method's session. Without an open session the collection would be
     * fetched outside a transaction.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found for " + email));

        return new UserPrincipal(user);
    }
}
