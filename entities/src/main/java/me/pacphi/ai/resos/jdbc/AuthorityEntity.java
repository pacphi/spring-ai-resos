package me.pacphi.ai.resos.jdbc;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Entity for user authorities/roles.
 */
@Table("authority")
public class AuthorityEntity {

    @Id
    private UUID id;

    @Column("name_01")
    private String name;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
