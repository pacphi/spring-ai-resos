package me.pacphi.ai.resos.jdbc;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Entity for user-authority join table (many-to-many relationship).
 * Uses synthetic ID for Spring Data JDBC compatibility.
 */
@Table("user_authority")
public class UserAuthorityEntity {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("authority_id")
    private UUID authorityId;

    // ID getter/setter
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    // Getters and Setters
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getAuthorityId() { return authorityId; }
    public void setAuthorityId(UUID authorityId) { this.authorityId = authorityId; }
}
