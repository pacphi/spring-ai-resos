package me.pacphi.ai.resos.jdbc;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity for application users (authentication).
 */
@Table("app_user")
public class AppUserEntity {

    @Id
    private UUID id;

    @Column("username")
    private String username;

    @Column("password")
    private String password;

    @Column("email")
    private String email;

    @Column("enabled")
    private Boolean enabled;

    @Column("account_non_expired")
    private Boolean accountNonExpired;

    @Column("account_non_locked")
    private Boolean accountNonLocked;

    @Column("credentials_non_expired")
    private Boolean credentialsNonExpired;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getAccountNonExpired() { return accountNonExpired; }
    public void setAccountNonExpired(Boolean accountNonExpired) { this.accountNonExpired = accountNonExpired; }

    public Boolean getAccountNonLocked() { return accountNonLocked; }
    public void setAccountNonLocked(Boolean accountNonLocked) { this.accountNonLocked = accountNonLocked; }

    public Boolean getCredentialsNonExpired() { return credentialsNonExpired; }
    public void setCredentialsNonExpired(Boolean credentialsNonExpired) { this.credentialsNonExpired = credentialsNonExpired; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
