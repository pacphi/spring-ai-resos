package me.pacphi.ai.resos.csv;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Profile(value = { "dev", "seed" })
public class CsvFileProcessor {
    private static final String SEPARATOR = ";";

    public <T> List<T> processCsvFile(Path filePath, Function<String[], T> mapper) throws IOException {
        try (Reader reader = Files.newBufferedReader(filePath)) {
            CSVParser parser = new CSVParserBuilder()
                    .withSeparator(SEPARATOR.charAt(0))
                    .build();

            CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withSkipLines(1)
                    .withCSVParser(parser)
                    .build();

            return csvReader.readAll().stream()
                    .map(mapper)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (CsvException e) {
            throw new IOException("Failed to process CSV file: " + filePath, e);
        }
    }
}
