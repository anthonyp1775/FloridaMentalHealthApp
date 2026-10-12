package com.flmentalhealth.config;

import com.flmentalhealth.entity.Role;
import com.flmentalhealth.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataSeeder - the startup guarantee that ROLE_USER and ROLE_ADMIN
 * exist.
 *
 * The property worth testing is idempotence. This runs on EVERY
 * startup, so a seeder that inserts unconditionally would accumulate
 * duplicate roles and eventually break registration - the exact
 * failure it exists to prevent.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataSeeder")
class DataSeederTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private DataSeeder seeder;

    @Test
    @DisplayName("creates both roles when the table is empty")
    void run_createsBothRolesWhenMissing() {
        when(roleRepository.findByName(anyString())).thenReturn(Optional.empty());

        seeder.run();

        ArgumentCaptor<Role> saved = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(Role::getName)
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("writes nothing on a second startup")
    void run_isIdempotent() {
        when(roleRepository.findByName(anyString()))
                .thenReturn(Optional.of(new Role("already here")));

        seeder.run();

        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("creates only the role that is actually missing")
    void run_createsOnlyWhatIsMissing() {
        when(roleRepository.findByName("ROLE_USER"))
                .thenReturn(Optional.of(new Role("ROLE_USER")));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.empty());

        seeder.run();

        ArgumentCaptor<Role> saved = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("ROLE_ADMIN");
    }
}
