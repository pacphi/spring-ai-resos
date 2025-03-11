package me.pacphi.ai.resos.repository;

import me.pacphi.ai.resos.jdbc.CustomerEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends CrudRepository<CustomerEntity, UUID> {

    Optional<CustomerEntity> findByName(String name);
}
