package me.pacphi.ai.resos.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import me.pacphi.ai.resos.jdbc.AreaEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AreaRepository extends CrudRepository<AreaEntity, UUID> {

    @Query("SELECT * FROM area WHERE name_01 = :name")
    Optional<AreaEntity> findByName(String name);
}
