package me.pacphi.ai.resos.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import me.pacphi.ai.resos.jdbc.BookingEntity;

import java.util.UUID;

@Repository
public interface BookingRepository extends CrudRepository<BookingEntity, UUID> {

}
