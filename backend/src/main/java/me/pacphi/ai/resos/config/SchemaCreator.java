package me.pacphi.ai.resos.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationTemp;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.FileCopyUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.util.*;

/**
 * Dynamically generates Liquibase changelogs from Spring Data JDBC entities.
 * <p>
 * This component scans for @Table-annotated entities and generates Liquibase
 * changesets. When running from a JAR (e.g., Docker container), it uses a
 * consistent temporary directory provided by Spring Boot's ApplicationTemp.
 * <p>
 * <b>Key behaviors:</b>
 * <ul>
 *   <li>In IDE/filesystem: writes to target/classes/db/changelog/</li>
 *   <li>In JAR: writes to ApplicationTemp directory (consistent across restarts)</li>
 *   <li>Copies patch files from classpath to temp directory for JAR execution</li>
 *   <li>Sets system property "liquibase.changelog.dir" for LiquibaseCustomizer</li>
 * </ul>
 */
@Profile({"dev", "test"})
@Component
public class SchemaCreator {
    private static final Logger logger = LoggerFactory.getLogger(SchemaCreator.class);

    /**
     * System property key for communicating changelog directory to LiquibaseCustomizer.
     * When set, indicates JAR execution mode requiring file-based changelog loading.
     */
    public static final String CHANGELOG_DIR_PROPERTY = "liquibase.changelog.dir";

    private final String entityBasePackage;
    private final String datasourceUrl;
    private final ApplicationTemp applicationTemp;

    // Track whether we're in JAR mode for changelog generation
    private boolean jarExecutionMode = false;

    public SchemaCreator(
            @Value("${app.entity.base-package}") String entityBasePackage,
            @Value("${spring.datasource.url}") String datasourceUrl
    ) {
        this.entityBasePackage = entityBasePackage;
        this.datasourceUrl = datasourceUrl;
        this.applicationTemp = new ApplicationTemp(SchemaCreator.class);
    }

    @PostConstruct
    public void generateSchema() throws IOException {
        logger.info("SchemaCreator starting - generating Liquibase changelogs from entities");

        // Create changeset generator
        ChangesetGenerator generator = new ChangesetGenerator(datasourceUrl);

        // Get target path (detects JAR vs filesystem execution)
        Path targetPath = getTargetPath();
        File changelogDir = targetPath.resolve("db/changelog/generated").toFile();
        changelogDir.mkdirs();

        // Generate changesets from entities
        generator.generateChangesets(entityBasePackage, changelogDir.getAbsolutePath());

        // Copy patches from classpath to temp directory if running from JAR
        if (jarExecutionMode) {
            copyPatchFilesToTempDirectory(targetPath);
        }

        // Update master changelog (must be after patches are copied)
        updateMasterChangelog(changelogDir, targetPath);

        logger.info("SchemaCreator completed - changelogs ready at: {}",
                targetPath.resolve("db/changelog/db.changelog-master.yml"));
    }

    /**
     * Determines the target path for changelog generation.
     * Uses ApplicationTemp for JAR execution to ensure consistent directory
     * across application restarts (important for Liquibase change tracking).
     */
    private Path getTargetPath() throws IOException {
        try {
            java.net.URL resource = getClass().getClassLoader().getResource("");
            if (resource != null) {
                String protocol = resource.getProtocol();
                String resourceStr = resource.toString();

                // Detect JAR execution: jar protocol or nested JAR path
                if ("jar".equals(protocol) || resourceStr.contains("!") || resourceStr.contains(".jar")) {
                    return getJarExecutionPath();
                }

                // Normal filesystem - use target/classes or target/test-classes
                Path classesPath = Paths.get(resource.toURI());
                Path changelogDir = classesPath.resolve("db/changelog");
                if (!changelogDir.toFile().exists()) {
                    changelogDir.toFile().mkdirs();
                }
                logger.debug("Using filesystem path for changelogs: {}", classesPath);
                return classesPath;
            }
        } catch (java.net.URISyntaxException | UnsupportedOperationException e) {
            logger.warn("Failed to resolve classpath root ({}), falling back to temp directory", e.getMessage());
            return getJarExecutionPath();
        }

        // Fallback to working directory (original behavior)
        Path currentPath = Paths.get("").toAbsolutePath();
        Path targetPath = currentPath.resolve("target/classes");
        if (!targetPath.toFile().exists()) {
            targetPath.toFile().mkdirs();
        }
        return targetPath;
    }

    /**
     * Gets a consistent temporary directory for JAR execution.
     * Uses Spring Boot's ApplicationTemp which provides:
     * - Consistent location across application restarts
     * - Application-specific isolation
     */
    private Path getJarExecutionPath() {
        jarExecutionMode = true;

        // Use ApplicationTemp for consistent temp directory across restarts
        File tempDir = applicationTemp.getDir("liquibase-changelogs");
        Path tempPath = tempDir.toPath();

        // Ensure changelog directory structure exists
        Path changelogDir = tempPath.resolve("db/changelog");
        changelogDir.toFile().mkdirs();

        // Set system property for LiquibaseCustomizer to find
        System.setProperty(CHANGELOG_DIR_PROPERTY, tempPath.toString());

        logger.info("JAR execution detected - using ApplicationTemp directory: {}", tempPath);
        logger.info("System property {} set to: {}", CHANGELOG_DIR_PROPERTY, tempPath);

        return tempPath;
    }

    /**
     * Copies patch files from classpath (inside JAR) to temp directory.
     * This is necessary because the master changelog uses relative paths
     * (patches/xxx.yml) which must exist on the filesystem.
     */
    private void copyPatchFilesToTempDirectory(Path targetPath) {
        Path patchesDir = targetPath.resolve("db/changelog/patches");
        patchesDir.toFile().mkdirs();

        try {
            Resource[] patchResources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:db/changelog/patches/*.yml");

            int copiedCount = 0;
            for (Resource patchResource : patchResources) {
                String filename = patchResource.getFilename();
                if (filename != null && filename.endsWith(".yml")) {
                    Path targetFile = patchesDir.resolve(filename);

                    // Copy using InputStream (works with classpath resources in JAR)
                    try (InputStream is = patchResource.getInputStream();
                         FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {
                        FileCopyUtils.copy(is, fos);
                        copiedCount++;
                        logger.debug("Copied patch file: {} -> {}", filename, targetFile);
                    }
                }
            }

            if (copiedCount > 0) {
                logger.info("Copied {} patch files from classpath to temp directory", copiedCount);
            }
        } catch (IOException e) {
            logger.warn("Failed to copy patch files from classpath: {}", e.getMessage());
            // Continue - patches might not exist, which is acceptable
        }
    }

    private void updateMasterChangelog(File generatedDir, Path targetPath) throws IOException {
        File masterFile = targetPath.resolve("db/changelog/db.changelog-master.yml").toFile();
        masterFile.getParentFile().mkdirs();

        List<Map<String, Object>> includes = new ArrayList<>();

        // Include generated entity changelogs (security entities are now part of entity scanning)
        File[] files = generatedDir.listFiles((dir, name) -> name.endsWith(".yml"));

        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                Map<String, Object> includeConfig = new HashMap<>();
                includeConfig.put("file", "generated/" + file.getName());
                includeConfig.put("relativeToChangelogFile", true);

                Map<String, Object> include = new HashMap<>();
                include.put("include", includeConfig);
                includes.add(include);
            }
        }

        // Include patch files after generated changelogs
        // Use classpath resource loading which works both from filesystem and JAR
        try {
            org.springframework.core.io.Resource[] patchResources =
                    new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                            .getResources("classpath:db/changelog/patches/*.yml");

            java.util.List<String> patchNames = new java.util.ArrayList<>();
            for (org.springframework.core.io.Resource patchResource : patchResources) {
                String filename = patchResource.getFilename();
                if (filename != null && filename.endsWith(".yml")) {
                    patchNames.add(filename);
                }
            }

            // Sort patch files alphabetically
            java.util.Collections.sort(patchNames);

            for (String patchName : patchNames) {
                Map<String, Object> includeConfig = new HashMap<>();
                includeConfig.put("file", "patches/" + patchName);
                includeConfig.put("relativeToChangelogFile", true);

                Map<String, Object> include = new HashMap<>();
                include.put("include", includeConfig);
                includes.add(include);
            }

            if (!patchNames.isEmpty()) {
                logger.info("Found {} patch files to include in changelog", patchNames.size());
            }
        } catch (IOException e) {
            logger.warn("Failed to load patch files from classpath, skipping patches", e);
        }

        Map<String, Object> changelog = new HashMap<>();
        changelog.put("databaseChangeLog", includes);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        try (FileWriter writer = new FileWriter(masterFile)) {
            new Yaml(options).dump(changelog, writer);
        }
    }

}

class ChangesetGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ChangesetGenerator.class);
    private final String databaseType;
    private final Set<Class<?>> processedEntities = new HashSet<>();
    // Track dependencies
    private final Map<Class<?>, Set<Class<?>>> dependencies = new HashMap<>();
    // Counter to ensure unique timestamps that preserve dependency order
    private long timestampCounter;

    public ChangesetGenerator(String datasourceUrl) {
        this.databaseType = datasourceUrl.contains(":postgresql:") ? "POSTGRESQL" : "H2";
    }

    // Add method to build dependency graph
    private void buildDependencyGraph(Set<Class<?>> entities) {
        for (Class<?> entity : entities) {
            dependencies.putIfAbsent(entity, new HashSet<>());

            for (Field field : entity.getDeclaredFields()) {
                if (isAggregateReference(field)) {
                    Class<?> targetEntity = getAggregateReferenceTargetType(field);
                    if (targetEntity != null) {
                        dependencies.get(entity).add(targetEntity);
                    }
                }
            }
        }
    }

    // Add method to sort entities based on dependencies
    private List<Class<?>> getOrderedEntities(Set<Class<?>> entities) {
        buildDependencyGraph(entities);
        List<Class<?>> ordered = new ArrayList<>();
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> visiting = new HashSet<>();

        for (Class<?> entity : entities) {
            if (!visited.contains(entity)) {
                visitEntity(entity, visited, visiting, ordered);
            }
        }

        return ordered;
    }

    private void visitEntity(Class<?> entity, Set<Class<?>> visited, Set<Class<?>> visiting, List<Class<?>> ordered) {
        if (visiting.contains(entity)) {
            throw new IllegalStateException("Circular dependency detected: " + entity.getName());
        }

        if (visited.contains(entity)) {
            return;
        }

        visiting.add(entity);

        // Visit dependencies first
        Set<Class<?>> deps = dependencies.getOrDefault(entity, Collections.emptySet());
        for (Class<?> dep : deps) {
            visitEntity(dep, visited, visiting, ordered);
        }

        visiting.remove(entity);
        visited.add(entity);
        ordered.add(entity);
    }

    public void generateChangesets(String basePackage, String outputPath) throws IOException {
        // Clear the output directory to ensure clean slate
        File outputDir = new File(outputPath);
        if (outputDir.exists()) {
            File[] existingFiles = outputDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (existingFiles != null) {
                for (File file : existingFiles) {
                    file.delete();
                }
            }
        }

        Set<Class<?>> entities = this.findEntities(basePackage);
        List<Class<?>> orderedEntities = getOrderedEntities(entities);

        // Initialize timestamp counter to ensure unique, ordered timestamps
        this.timestampCounter = System.currentTimeMillis();

        for(Class<?> entity : orderedEntities) {
            if (!this.processedEntities.contains(entity) && this.hasIdField(entity)) {
                this.processEntity(entity, outputPath);
            } else {
                logger.debug("Skipping entity {} because it either lacks an @Id field or has already been processed.", entity.getName());
            }
        }
    }

    private boolean hasIdField(Class<?> entity) {
        for(Field field : entity.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return true;
            }
        }

        return false;
    }

    private boolean isAggregateReference(Field field) {
        return AggregateReference.class.isAssignableFrom(field.getType());
    }

    private Class<?> getAggregateReferenceTargetType(Field field) {
        // Gets the first generic type parameter of AggregateReference
        if (field.getGenericType() instanceof java.lang.reflect.ParameterizedType pt) {
            return (Class<?>) pt.getActualTypeArguments()[0];
        }
        return null;
    }

    private void processEntity(Class<?> entity, String outputPath) throws IOException {
        processedEntities.add(entity);

        // Get current timestamp and increment for next entity
        long currentTimestamp = timestampCounter++;

        // Generate table creation changeset with the same timestamp
        Map<String, Object> changeset = generateChangeset(entity, currentTimestamp);

        // Write to file with the timestamp to preserve dependency order
        String fileName = String.format("%d_%s_init.yml",
                currentTimestamp,
                getTableName(entity));

        writeChangeset(changeset, new File(outputPath, fileName));
    }

    private Map<String, Object> generateChangeset(Class<?> entity, long timestamp) {
        String tableName = getTableName(entity);
        List<Map<String, Object>> changes = new ArrayList<>();

        // Table creation
        List<Map<String, Object>> columns = new ArrayList<>();

        // Add ID column first
        columns.add(createIdColumn());

        // Add regular columns (excluding AggregateReference fields)
        for (Field field : entity.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Id.class) && !isAggregateReference(field)) {
                processField(field, columns, tableName);
            }
        }

        // Create the initial table change
        Map<String, Object> createTable = new HashMap<>();
        createTable.put("tableName", tableName);
        createTable.put("columns", columns);
        changes.add(Collections.singletonMap("createTable", createTable));

        // Add AggregateReference columns as separate changes
        for (Field field : entity.getDeclaredFields()) {
            if (isAggregateReference(field)) {
                processAggregateReference(field, changes, tableName);
            }
        }

        // Process mapped collections if any
        processMappedCollections(entity, changes);

        // Create the changeset
        String changesetId = String.format("%d_%s_init",
                timestamp,
                tableName);

        Map<String, Object> changeSet = new HashMap<>();
        changeSet.put("id", changesetId);
        changeSet.put("author", "entity-generator");
        changeSet.put("changes", changes);

        return Collections.singletonMap("changeSet", changeSet);
    }

    private Map<String, Object> createIdColumn() {
        Map<String, Object> column = new HashMap<>();
        column.put("name", "id");
        column.put("type", "uuid");

        Map<String, Object> constraints = new HashMap<>();
        constraints.put("primaryKey", true);
        constraints.put("nullable", false);
        column.put("constraints", constraints);

        String defaultValue = databaseType.equals("POSTGRESQL") ?
                "gen_random_uuid()" : "random_uuid()";
        column.put("defaultValueComputed", defaultValue);

        return Collections.singletonMap("column", column);
    }

    private void processField(Field field, List<Map<String, Object>> columns, String tableName) {
        if (field.isAnnotationPresent(MappedCollection.class)) {
            return; // Handled separately
        }

        if (field.isAnnotationPresent(Embedded.class) || field.isAnnotationPresent(Embedded.Nullable.class)) {
            processEmbeddedField(field, columns);
            return;
        }

        // Remove AggregateReference check since it's handled separately
        String columnName = getColumnName(field);
        String columnType = getColumnType(field.getType());

        if (columnType != null) {
            Map<String, Object> column = new HashMap<>();
            column.put("name", columnName);
            column.put("type", columnType);

            if (field.isAnnotationPresent(org.springframework.lang.NonNull.class)) {
                Map<String, Object> constraints = new HashMap<>();
                constraints.put("nullable", false);
                column.put("constraints", constraints);
            }

            // Just add the column definition directly to the columns list
            columns.add(Collections.singletonMap("column", column));
        }
    }

    private void processAggregateReference(Field field, List<Map<String, Object>> changes, String tableName) {
        String columnName = getColumnName(field);
        Class<?> targetEntity = getAggregateReferenceTargetType(field);

        if (targetEntity != null) {
            String targetTable = getTableName(targetEntity);

            // Create the column definition
            Map<String, Object> column = new HashMap<>();
            column.put("name", columnName);
            column.put("type", "uuid");

            Map<String, Object> constraints = new HashMap<>();
            constraints.put("nullable", !field.isAnnotationPresent(org.springframework.lang.NonNull.class));
            constraints.put("references", targetTable + "(id)");
            constraints.put("foreignKeyName", "fk_" + tableName + "_" + targetTable);
            column.put("constraints", constraints);

            // Create a list of columns containing this column
            List<Map<String, Object>> columns = new ArrayList<>();
            columns.add(Collections.singletonMap("column", column));

            // Create the addColumn structure with tableName and columns array
            Map<String, Object> addColumn = new HashMap<>();
            addColumn.put("tableName", tableName);
            addColumn.put("columns", columns);

            // Add as a change
            changes.add(Collections.singletonMap("addColumn", addColumn));
        }
    }

    private void processEmbeddedField(Field field, List<Map<String, Object>> columns) {
        Class<?> embeddedType = field.getType();
        String prefix = toSnakeCase(field.getName());
        boolean isNullable = field.isAnnotationPresent(Embedded.Nullable.class);

        for (Field embeddedField : embeddedType.getDeclaredFields()) {
            // Skip static, transient, and synthetic fields
            if (java.lang.reflect.Modifier.isStatic(embeddedField.getModifiers()) ||
                    java.lang.reflect.Modifier.isTransient(embeddedField.getModifiers()) ||
                    embeddedField.isSynthetic()) {
                continue;
            }

            // Handle nested embedded objects
            if (embeddedField.isAnnotationPresent(Embedded.class) ||
                    embeddedField.isAnnotationPresent(Embedded.Nullable.class)) {
                processEmbeddedField(embeddedField, columns);
                continue;
            }

            String columnName = prefix + "_" + getColumnName(embeddedField);
            String columnType = getColumnType(embeddedField.getType());

            if (columnType != null) {
                Map<String, Object> column = new HashMap<>();
                column.put("name", columnName);
                column.put("type", columnType);

                // Handle nullable constraints
                if (!isNullable || embeddedField.isAnnotationPresent(org.springframework.lang.NonNull.class)) {
                    Map<String, Object> constraints = new HashMap<>();
                    constraints.put("nullable", false);
                    column.put("constraints", constraints);
                }

                columns.add(Collections.singletonMap("column", column));
            }
        }
    }

    private void processMappedCollections(Class<?> entity, List<Map<String, Object>> changes) {
        for (Field field : entity.getDeclaredFields()) {
            if (field.isAnnotationPresent(MappedCollection.class)) {
                MappedCollection ann = field.getAnnotation(MappedCollection.class);
                String joinTable = getTableName(entity) + "_" + toSnakeCase(field.getName());

                // Create join table
                List<Map<String, Object>> joinColumns = new ArrayList<>();

                // Parent ID column
                Map<String, Object> parentColumn = new HashMap<>();
                parentColumn.put("name", getTableName(entity) + "_id");
                parentColumn.put("type", "uuid");
                Map<String, Object> constraints = new HashMap<>();
                constraints.put("nullable", false);
                parentColumn.put("constraints", constraints);
                joinColumns.add(Collections.singletonMap("column", parentColumn));

                // Add collection specific columns
                if (!ann.keyColumn().isEmpty()) {
                    Map<String, Object> keyColumn = new HashMap<>();
                    keyColumn.put("name", ann.keyColumn());
                    keyColumn.put("type", "integer");
                    keyColumn.put("constraints",
                            Collections.singletonMap("nullable", false));
                    joinColumns.add(Collections.singletonMap("column", keyColumn));
                }

                Map<String, Object> createJoinTable = new HashMap<>();
                createJoinTable.put("tableName", joinTable);
                createJoinTable.put("columns", joinColumns);

                changes.add(Collections.singletonMap("createTable", createJoinTable));
            }
        }
    }

    private Set<Class<?>> findEntities(String basePackage) throws IOException {
        Set<Class<?>> entities = new HashSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory factory = new CachingMetadataReaderFactory(resolver);

        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                ClassUtils.convertClassNameToResourcePath(basePackage) + "/**/*.class";

        for (Resource resource : resolver.getResources(pattern)) {
            if (resource.isReadable()) {
                MetadataReader reader = factory.getMetadataReader(resource);
                String className = reader.getClassMetadata().getClassName();

                try {
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(Table.class)) {
                        entities.add(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    logger.warn("Could not load class: " + className, e);
                }
            }
        }

        return entities;
    }

    private String getTableName(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        return table != null ? table.value() : toSnakeCase(entityClass.getSimpleName());
    }

    private String getColumnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null) {
            return column.value();
        }

        // Special handling for AggregateReference fields
        if (isAggregateReference(field)) {
            // If no @Column annotation, append "_id" to the field name
            return toSnakeCase(field.getName()) + "_id";
        }

        return toSnakeCase(field.getName());
    }

    private String getColumnType(Class<?> type) {
        if (String.class.equals(type)) return "varchar(255)";
        if (Integer.class.equals(type) || int.class.equals(type)) return "integer";
        if (Long.class.equals(type) || long.class.equals(type)) return "bigint";
        if (Double.class.equals(type) || double.class.equals(type)) return "double precision";
        if (Float.class.equals(type) || float.class.equals(type)) return "float";
        if (BigDecimal.class.equals(type)) return "decimal(19,2)";
        if (Boolean.class.equals(type) || boolean.class.equals(type)) return "boolean";
        if (LocalDateTime.class.equals(type)) return "timestamp";
        if (LocalDate.class.equals(type)) return "date";
        if (LocalTime.class.equals(type)) return "time";
        if (OffsetDateTime.class.equals(type)) return "timestamp with time zone";
        if (Instant.class.equals(type)) return "timestamp with time zone";
        if (UUID.class.equals(type)) return "uuid";
        if (byte[].class.equals(type)) return "bytea";
        if (type.isEnum()) return "varchar(50)";
        if (type.isAssignableFrom(Object.class)) return "text";
        return null;
    }

    private void writeChangeset(Map<String, Object> changeset, File file) throws IOException {
        Map<String, Object> changelog = new HashMap<>();
        changelog.put("databaseChangeLog", Collections.singletonList(changeset));

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        try (FileWriter writer = new FileWriter(file)) {
            new Yaml(options).dump(changelog, writer);
        }
    }

    private String toSnakeCase(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}