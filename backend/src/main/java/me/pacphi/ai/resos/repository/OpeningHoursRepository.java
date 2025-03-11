package me.pacphi.ai.resos.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import me.pacphi.ai.resos.jdbc.OpeningHoursEntity;

import java.util.UUID;

@Repository
public interface OpeningHoursRepository extends CrudRepository<OpeningHoursEntity, UUID> {

}
