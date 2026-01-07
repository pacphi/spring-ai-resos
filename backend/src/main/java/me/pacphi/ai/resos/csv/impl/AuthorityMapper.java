package me.pacphi.ai.resos.csv.impl;

import me.pacphi.ai.resos.csv.CsvEntityMapper;
import me.pacphi.ai.resos.csv.CsvMappingException;
import me.pacphi.ai.resos.csv.EntityMapper;
import me.pacphi.ai.resos.jdbc.AuthorityEntity;

@CsvEntityMapper("authorities")
public class AuthorityMapper implements EntityMapper<AuthorityEntity> {

    @Override
    public AuthorityEntity mapFromCsv(String[] line) throws CsvMappingException {
        try {
            var entity = new AuthorityEntity();
            entity.setName(line[0]);
            return entity;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CsvMappingException("Failed to map authority from CSV", e);
        }
    }

    @Override
    public Class<AuthorityEntity> getEntityClass() {
        return AuthorityEntity.class;
    }
}
