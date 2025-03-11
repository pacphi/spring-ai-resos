package me.pacphi.ai.resos.jdbc;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@ReadingConverter
public class StringToOffsetDateTimeConverter implements Converter<String, OffsetDateTime> {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public OffsetDateTime convert(String source) {
        return source != null ? OffsetDateTime.parse(source, formatter) : null;
    }
}
