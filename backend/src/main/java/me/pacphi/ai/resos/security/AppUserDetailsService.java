package me.pacphi.ai.resos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Custom UserDetailsService that loads users from the app_user table.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(AppUserDetailsService.class);

    private final JdbcTemplate jdbcTemplate;

    public AppUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.debug("Loading user by username: {}", username);

        // Query the app_user table
        String userQuery = """
            SELECT id, username, password, email, enabled,
                   account_non_expired, account_non_locked, credentials_non_expired
            FROM app_user
            WHERE username = ?
            """;

        List<AppUser> users = jdbcTemplate.query(userQuery, (rs, rowNum) -> new AppUser(
            UUID.fromString(rs.getString("id")),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("email"),
            rs.getBoolean("enabled"),
            rs.getBoolean("account_non_expired"),
            rs.getBoolean("account_non_locked"),
            rs.getBoolean("credentials_non_expired")
        ), username);

        if (users.isEmpty()) {
            logger.warn("User not found: {}", username);
            throw new UsernameNotFoundException("User not found: " + username);
        }

        AppUser appUser = users.get(0);

        // Query the user's authorities
        String authoritiesQuery = """
            SELECT a.name_01
            FROM authority a
            JOIN user_authority ua ON ua.authority_id = a.id
            WHERE ua.user_id = ?
            """;

        List<SimpleGrantedAuthority> authorities = jdbcTemplate.query(
            authoritiesQuery,
            (rs, rowNum) -> new SimpleGrantedAuthority(rs.getString("name_01")),
            appUser.id()
        );

        logger.debug("User {} has authorities: {}", username, authorities);

        return User.builder()
            .username(appUser.username())
            .password(appUser.password())
            .authorities(authorities)
            .accountExpired(!appUser.accountNonExpired())
            .accountLocked(!appUser.accountNonLocked())
            .credentialsExpired(!appUser.credentialsNonExpired())
            .disabled(!appUser.enabled())
            .build();
    }

    /**
     * Internal record for holding user data from database.
     */
    private record AppUser(
        UUID id,
        String username,
        String password,
        String email,
        boolean enabled,
        boolean accountNonExpired,
        boolean accountNonLocked,
        boolean credentialsNonExpired
    ) {}
}
