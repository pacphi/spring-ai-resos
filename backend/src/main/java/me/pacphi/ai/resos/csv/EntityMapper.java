package me.pacphi.ai.resos.csv;

public interface EntityMapper<T> {
    T mapFromCsv(String[] line) throws CsvMappingException;
    Class<T> getEntityClass();
}