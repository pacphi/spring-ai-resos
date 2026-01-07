package me.pacphi.ai.resos.repository;

import me.pacphi.ai.resos.jdbc.AuthorityEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthorityRepository extends CrudRepository<AuthorityEntity, UUID> {

    @Query("SELECT * FROM authority WHERE name_01 = :name")
    Optional<AuthorityEntity> findByName(String name);
}
