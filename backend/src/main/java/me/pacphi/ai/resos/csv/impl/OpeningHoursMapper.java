package me.pacphi.ai.resos.csv.impl;

import me.pacphi.ai.resos.csv.CsvEntityMapper;
import me.pacphi.ai.resos.csv.CsvMappingException;
import me.pacphi.ai.resos.csv.EntityMapper;
import me.pacphi.ai.resos.jdbc.OpeningHoursEntity;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@CsvEntityMapper("openinghours")
public class OpeningHoursMapper implements EntityMapper<OpeningHoursEntity> {

    @Override
    public OpeningHoursEntity mapFromCsv(String[] line) throws CsvMappingException {
        try {
            var entity = new OpeningHoursEntity();
            entity.setName(line[0]);
            entity.setDate(LocalDate.parse(line[1]));
            entity.setOpens(line[2]);
            entity.setCloses(line[3]);
            entity.setIsOpen(Boolean.parseBoolean(line[4]));
            entity.setMaxPeople(Integer.parseInt(line[5]));
            entity.setMetadata(line[6]);
            return entity;
        } catch (DateTimeParseException | IllegalArgumentException | NullPointerException e) {
            throw new CsvMappingException("Failed to map opening hours from CSV", e);
        }
    }

    @Override
    public Class<OpeningHoursEntity> getEntityClass() {
        return OpeningHoursEntity.class;
    }
}
