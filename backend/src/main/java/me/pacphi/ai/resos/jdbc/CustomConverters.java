package me.pacphi.ai.resos.jdbc;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CustomConverters extends AbstractJdbcConfiguration {

    @Override
    protected List<?> userConverters() {
        return Arrays.asList(
                new StringToOffsetDateTimeConverter(),
                new OffsetDateTimeToStringConverter()
        );
    }
}