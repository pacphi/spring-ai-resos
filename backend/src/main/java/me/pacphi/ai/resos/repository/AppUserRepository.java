package me.pacphi.ai.resos.repository;

import me.pacphi.ai.resos.jdbc.AppUserEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends CrudRepository<AppUserEntity, UUID> {

    @Query("SELECT * FROM app_user WHERE username = :username")
    Optional<AppUserEntity> findByUsername(String username);

    @Query("SELECT * FROM app_user WHERE email = :email")
    Optional<AppUserEntity> findByEmail(String email);
}
