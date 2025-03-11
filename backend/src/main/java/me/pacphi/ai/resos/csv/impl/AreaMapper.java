package me.pacphi.ai.resos.csv.impl;

import me.pacphi.ai.resos.csv.CsvEntityMapper;
import me.pacphi.ai.resos.csv.CsvMappingException;
import me.pacphi.ai.resos.csv.EntityMapper;
import me.pacphi.ai.resos.jdbc.AreaEntity;

@CsvEntityMapper("areas")
public class AreaMapper implements EntityMapper<AreaEntity> {

    @Override
    public AreaEntity mapFromCsv(String[] line) throws CsvMappingException {
        try {
            var entity = new AreaEntity();
            entity.setName(line[0]);
            entity.setInternalNote(line[1]);
            entity.setBookingPriority(Integer.parseInt(line[2]));
            return entity;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CsvMappingException("Failed to map area from CSV", e);
        }
    }

    @Override
    public Class<AreaEntity> getEntityClass() {
        return AreaEntity.class;
    }
}
