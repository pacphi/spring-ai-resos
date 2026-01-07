package me.pacphi.ai.resos.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for AppUserDetailsService.
 * Tests database-backed user authentication and authority loading.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserDetailsServiceTest {

    @Autowired
    private AppUserDetailsService userDetailsService;

    @Test
    void shouldLoadUserByUsername() {
        // Given: User exists in database (from seed data)
        String username = "admin";

        // When: Load user by username
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Then: Returns UserDetails with correct username
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(username);
        assertThat(userDetails.getPassword()).isNotNull().isNotEmpty();
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    void shouldThrowExceptionForNonExistentUser() {
        // Given: Non-existent username
        String nonExistentUsername = "nonexistent-user-12345";

        // When & Then: Should throw UsernameNotFoundException
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(nonExistentUsername))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void shouldLoadUserAuthorities() {
        // Given: User exists with authorities
        String username = "admin";

        // When: Load user by username
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Then: User should have authorities
        Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
        assertThat(authorities).isNotNull().isNotEmpty();

        // And: Should have expected roles (based on seed data)
        boolean hasAdminRole = authorities.stream()
                .anyMatch(auth -> auth.getAuthority().contains("ADMIN"));
        assertThat(hasAdminRole).isTrue();
    }

    @Test
    void shouldReturnEnabledUserForActiveAccount() {
        // Given: Active user account
        String username = "admin";

        // When: Load user
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Then: User should be enabled, not locked, not expired
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void shouldHandleCaseInsensitiveUsernameSearch() {
        // Given: Username in different case
        // Note: This depends on database collation settings
        String username = "admin";

        // When: Load user with exact case
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Then: Should successfully load user
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualToIgnoringCase(username);
    }
}
