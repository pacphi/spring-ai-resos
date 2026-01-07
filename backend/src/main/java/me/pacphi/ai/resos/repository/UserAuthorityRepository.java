package me.pacphi.ai.resos.repository;

import me.pacphi.ai.resos.jdbc.UserAuthorityEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for user-authority join table.
 * Note: This entity doesn't have @Id, so we use a composite key approach.
 */
@Repository
public interface UserAuthorityRepository extends CrudRepository<UserAuthorityEntity, UUID> {

    @Query("SELECT * FROM user_authority WHERE user_id = :userId")
    List<UserAuthorityEntity> findByUserId(UUID userId);

    @Query("SELECT * FROM user_authority WHERE authority_id = :authorityId")
    List<UserAuthorityEntity> findByAuthorityId(UUID authorityId);

    @Modifying
    @Query("INSERT INTO user_authority (user_id, authority_id) VALUES (:userId, :authorityId)")
    void insert(UUID userId, UUID authorityId);
}
