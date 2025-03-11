package me.pacphi.ai.resos.csv.impl;

import me.pacphi.ai.resos.csv.CsvEntityMapper;
import me.pacphi.ai.resos.csv.CsvMappingException;
import me.pacphi.ai.resos.csv.EntityMapper;
import me.pacphi.ai.resos.jdbc.CustomerEntity;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@CsvEntityMapper("customers")
public class CustomerMapper implements EntityMapper<CustomerEntity> {

    @Override
    public CustomerEntity mapFromCsv(String[] line) throws CsvMappingException {
        try {
            var entity = new CustomerEntity();
            entity.setName(line[0]);
            entity.setEmail(line[1]);
            entity.setPhone(line[2]);
            entity.setCreatedAt(OffsetDateTime.parse(line[3]));
            entity.setLastBookingAt(OffsetDateTime.parse(line[4]));
            entity.setBookingCount(Integer.parseInt(line[5]));
            entity.setTotalSpent(Float.parseFloat(line[6]));
            entity.setMetadata(line[7]);
            return entity;
        } catch (DateTimeParseException | IllegalArgumentException | NullPointerException e) {
            throw new CsvMappingException("Failed to map customer from CSV", e);
        }
    }

    @Override
    public Class<CustomerEntity> getEntityClass() {
        return CustomerEntity.class;
    }
}
