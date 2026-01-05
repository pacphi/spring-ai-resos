package me.pacphi.ai.resos.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.util.*;

@Profile("dev")
@Component
public class SchemaCreator {
    private static final Logger logger = LoggerFactory.getLogger(SchemaCreator.class);
    private final String entityBasePackage;
    private final String datasourceUrl;

    public SchemaCreator(
            @Value("${app.entity.base-package}") String entityBasePackage,
            @Value("${spring.datasource.url}") String datasourceUrl
    ) {
        this.entityBasePackage = entityBasePackage;
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    public void generateSchema() throws IOException {
        // Create changeset generator
        ChangesetGenerator generator = new ChangesetGenerator(datasourceUrl);

        // Get target path
        Path targetPath = getTargetPath();
        File changelogDir = targetPath.resolve("db/changelog/generated").toFile();
        changelogDir.mkdirs();

        // Generate changesets
        generator.generateChangesets(entityBasePackage, changelogDir.getAbsolutePath());

        // Update master changelog
        updateMasterChangelog(changelogDir, targetPath);
    }

    private Path getTargetPath() throws IOException {
        // Use classpath resource to find the correct module's target/classes
        // This works regardless of where Maven/tests are run from
        try {
            java.net.URL resource = getClass().getClassLoader().getResource("");
            if (resource != null) {
                Path classesPath = Paths.get(resource.toURI());
                // Ensure db/changelog directory exists
                Path changelogDir = classesPath.resolve("db/changelog");
                if (!changelogDir.toFile().exists()) {
                    changelogDir.toFile().mkdirs();
                }
                return classesPath;
            }
        } catch (java.net.URISyntaxException e) {
            logger.warn("Failed to resolve classpath root, falling back to working directory", e);
        }

        // Fallback to working directory (original behavior)
        Path currentPath = Paths.get("").toAbsolutePath();
        Path targetPath = currentPath.resolve("target/classes");
        if (!targetPath.toFile().exists()) {
            targetPath.toFile().mkdirs();
        }
        return targetPath;
    }

    private void updateMasterChangelog(File generatedDir, Path targetPath) throws IOException {
        File masterFile = targetPath.resolve("db/changelog/db.changelog-master.yml").toFile();
        masterFile.getParentFile().mkdirs();

        List<Map<String, Object>> includes = new ArrayList<>();
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
        Set<Class<?>> entities = this.findEntities(basePackage);
        List<Class<?>> orderedEntities = getOrderedEntities(entities);

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

        // Generate table creation changeset
        Map<String, Object> changeset = generateChangeset(entity);

        // Write to file
        String fileName = String.format("%d_%s_init.yml",
                System.currentTimeMillis(),
                getTableName(entity));

        writeChangeset(changeset, new File(outputPath, fileName));
    }

    private Map<String, Object> generateChangeset(Class<?> entity) {
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
                System.currentTimeMillis(),
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