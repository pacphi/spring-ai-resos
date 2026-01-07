package me.pacphi.ai.resos.jdbc;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.lang.NonNull;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Custom converters for Spring Data JDBC.
 * <p>
 * Includes converters for types that require special handling:
 * <ul>
 *   <li>OffsetDateTime - for timestamp with timezone support</li>
 *   <li>URI - to avoid Java module accessibility issues (java.net.URI private constructor)</li>
 * </ul>
 */
@Configuration
public class CustomConverters extends AbstractJdbcConfiguration {

    @Override
    protected List<?> userConverters() {
        return Arrays.asList(
                new StringToOffsetDateTimeConverter(),
                new OffsetDateTimeToStringConverter(),
                new StringToUriConverter(),
                new UriToStringConverter()
        );
    }

    /**
     * Converts a String to URI when reading from database.
     * This avoids the InaccessibleObjectException that occurs when Spring Data JDBC
     * tries to access java.net.URI's private constructor without --add-opens.
     */
    @ReadingConverter
    public static class StringToUriConverter implements Converter<String, URI> {
        @Override
        public URI convert(@NonNull String source) {
            if (source.isEmpty()) {
                return null;
            }
            return URI.create(source);
        }
    }

    /**
     * Converts URI to String when writing to database.
     */
    @WritingConverter
    public static class UriToStringConverter implements Converter<URI, String> {
        @Override
        public String convert(@NonNull URI source) {
            return source.toString();
        }
    }
}