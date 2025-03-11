package me.pacphi.ai.resos.jdbc;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@WritingConverter
public class OffsetDateTimeToStringConverter implements Converter<OffsetDateTime, String> {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public String convert(OffsetDateTime source) {
        return source != null ? source.format(formatter) : null;
    }
}
