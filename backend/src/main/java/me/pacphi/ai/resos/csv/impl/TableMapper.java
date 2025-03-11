package me.pacphi.ai.resos.csv.impl;

import me.pacphi.ai.resos.csv.CsvEntityMapper;
import me.pacphi.ai.resos.csv.CsvMappingException;
import me.pacphi.ai.resos.csv.EntityMapper;
import me.pacphi.ai.resos.jdbc.AreaEntity;
import me.pacphi.ai.resos.jdbc.TableEntity;
import me.pacphi.ai.resos.repository.AreaRepository;
import org.springframework.data.jdbc.core.mapping.AggregateReference;

@CsvEntityMapper("tables")
public class TableMapper implements EntityMapper<TableEntity> {

    private final AreaRepository areaRepository;

    public TableMapper(AreaRepository areaRepository) {
        this.areaRepository = areaRepository;
    }

    @Override
    public TableEntity mapFromCsv(String[] line) throws CsvMappingException {
        try {

            String areaName = line[4];

            // Try to find the area by its name
            var area = areaRepository.findByName(areaName)
                    .orElseGet(() -> {
                        // Area doesn't exist, create it
                        AreaEntity newArea = new AreaEntity();
                        newArea.setName(areaName);
                        return areaRepository.save(newArea);
                    });

            var entity = new TableEntity();
            entity.setName(line[0]);
            entity.setSeatsMin(Integer.parseInt(line[1]));
            entity.setSeatsMax(Integer.parseInt(line[2]));
            entity.setInternalNote(line[3]);

            // Create AggregateReference using the found Area's ID
            entity.setArea(AggregateReference.to(area.getId()));

            return entity;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CsvMappingException("Failed to map table from CSV", e);
        }
    }

    @Override
    public Class<TableEntity> getEntityClass() {
        return TableEntity.class;
    }
}