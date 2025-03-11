package me.pacphi.ai.resos.util;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EntityGenerator {

    private static final String USAGE = """
            Usage: EntityGenerator <sourceDir> <targetDir> <sourcePackage> <targetPackage> [excludeClasses]

            Arguments:
              sourceDir     : Directory containing the unpacked source files
              targetDir     : Directory where generated entities will be written
              sourcePackage : Base package name of the source POJOs (e.g., me.pacphi.ai.resos.model)
              targetPackage : Base package name for generated entities (e.g., me.pacphi.ai.resos.jdbc)
              excludeClasses: Optional comma-separated list of fully-qualified class names to exclude

            Example:
              EntityGenerator /tmp/unpacked-sources /tmp/generated-sources me.pacphi.ai.resos.model me.pacphi.ai.resos.jdbc
            """;

    private static final String SUFFIX = "_01";
    private static final Map<String, String> qualifiedNames = new HashMap<>();
    private static Path sourcePath;

    // Configuration holder class
    private static class Config {
        final Path sourcePath;
        final Path targetPath;
        final String sourcePackage;
        final String targetPackage;
        final Set<String> excludedClasses;

        Config(Path sourcePath, Path targetPath, String sourcePackage, String targetPackage, Set<String> excludedClasses) {
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.sourcePackage = sourcePackage;
            this.targetPackage = targetPackage;
            this.excludedClasses = excludedClasses;
        }
    }

    // Stats tracking class
    private static class ProcessingStats {
        final AtomicInteger totalProcessed = new AtomicInteger(0);
        final AtomicInteger totalEntitiesCreated = new AtomicInteger(0);
        final AtomicInteger totalSkipped = new AtomicInteger(0);
        final AtomicInteger totalFailed = new AtomicInteger(0);

        void printSummary(Config config) {
            System.out.println("\nGeneration Summary:");
            System.out.println("------------------");
            System.out.println("Source directory: " + config.sourcePath);
            System.out.println("Target directory: " + config.targetPath);
            System.out.println("Total model files processed: " + totalProcessed.get());
            System.out.println("Successful entity generations: " + totalEntitiesCreated.get());
            System.out.println("Skipped (no String id): " + totalSkipped.get());
            System.out.println("Failed generations: " + totalFailed.get());
            if (!config.excludedClasses.isEmpty()) {
                System.out.println("Excluded classes: " + String.join(", ", config.excludedClasses));
            }
        }
    }

    public static void main(String[] args) {
        try {
            Config config = processArgs(args);
            setupParser(config);
            processSourceFiles(config);
        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage());
            System.err.println("\n" + USAGE);
            System.exit(1);
        }
    }

    private static Config processArgs(String[] args) throws IOException {
        validateArgs(args);

        String sourceDir = args[0];
        String targetDir = args[1];
        String sourcePackage = args[2];
        String targetPackage = args[3];
        Set<String> excludedClasses = new HashSet<>();

        if (args.length > 4 && args[4] != null && !args[4].trim().isEmpty()) {
            excludedClasses.addAll(Arrays.asList(args[4].split(",")));
        }

        Path sourcePath = Paths.get(sourceDir, sourcePackage.replace('.', '/'));
        Path targetPath = Paths.get(targetDir, targetPackage.replace('.', '/'));
        validateDirectories(sourcePath);
        Files.createDirectories(targetPath);

        return new Config(sourcePath, targetPath, sourcePackage, targetPackage, excludedClasses);
    }

    private static void validateArgs(String[] args) {
        if (args == null || args.length < 4) {
            throw new IllegalArgumentException("At least 4 arguments are required");
        }

        for (String arg : args) {
            if (arg == null || arg.trim().isEmpty()) {
                throw new IllegalArgumentException("All arguments must be non-null and non-empty");
            }
        }

        validatePackageName(args[2], "source");
        validatePackageName(args[3], "target");
    }

    private static void validatePackageName(String packageName, String type) {
        if (!isValidPackageName(packageName)) {
            throw new IllegalArgumentException("Invalid " + type + " package name: " + packageName);
        }
    }

    private static boolean isValidPackageName(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }

        return Arrays.stream(packageName.split("\\."))
                .allMatch(part -> part.matches("[a-zA-Z_$][a-zA-Z\\d_$]*"));
    }

    private static void validateDirectories(Path sourcePath) throws IOException {
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourcePath);
        }

        if (!Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("Source path is not a directory: " + sourcePath);
        }

        if (!Files.walk(sourcePath)
                .anyMatch(path -> path.toString().endsWith(".java"))) {
            throw new IllegalArgumentException("No Java files found in source directory: " + sourcePath);
        }
    }

    private static void setupParser(Config config) {
        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
        combinedSolver.add(new ReflectionTypeSolver());

        // Add source root solver
        Path sourceRoot = config.sourcePath.getParent().getParent();
        System.out.println("Adding source root to solver: " + sourceRoot);
        combinedSolver.add(new JavaParserTypeSolver(sourceRoot.toFile()));

        // Add model package solver
        System.out.println("Adding model package solver: " + config.sourcePath);
        combinedSolver.add(new JavaParserTypeSolver(config.sourcePath.toFile()));

        ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(combinedSolver))
                .setAttributeComments(false)
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

        StaticJavaParser.setConfiguration(parserConfiguration);
    }

    private static void processSourceFiles(Config config) {
        try {
            buildQualifiedNamesMap(config.sourcePath);
            Map<String, CompilationUnit> compilationUnits = parseSourceFiles(config.sourcePath);
            generateEntities(compilationUnits, config);
        } catch (IOException e) {
            throw new RuntimeException("Error processing files: " + e.getMessage(), e);
        }
    }

    private static void buildQualifiedNamesMap(Path sourcePath) throws IOException {
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        CompilationUnit cu = StaticJavaParser.parse(path);
                        cu.getPrimaryType().ifPresent(type -> {
                            String className = type.getName().asString();
                            cu.getPackageDeclaration().ifPresent(pkg -> {
                                String qualifiedName = pkg.getNameAsString() + "." + className;
                                qualifiedNames.put(className, qualifiedName);
                            });
                        });
                    } catch (IOException e) {
                        System.err.println("Error pre-parsing file " + path + ": " + e.getMessage());
                    }
                });
    }

    private static Map<String, CompilationUnit> parseSourceFiles(Path sourcePath) throws IOException {
        Map<String, CompilationUnit> compilationUnits = new HashMap<>();
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        System.out.println("Parsing file: " + path);
                        CompilationUnit cu = StaticJavaParser.parse(path);
                        String className = cu.getPrimaryType()
                                .map(type -> type.getName().asString())
                                .orElse("");
                        if (!className.isEmpty()) {
                            compilationUnits.put(className, cu);
                        }
                    } catch (IOException e) {
                        System.err.println("Error parsing file " + path + ": " + e.getMessage());
                    }
                });
        return compilationUnits;
    }

    private static void generateEntity(ClassOrInterfaceDeclaration classDecl, Config config) throws IOException {
        String className = classDecl.getNameAsString();
        EntityCodeBuilder builder = new EntityCodeBuilder(className, config);

        // Collect imports
        Set<String> imports = new TreeSet<>();

        // Add basic Spring Data JDBC imports
        imports.add("org.springframework.data.relational.core.mapping.Column");
        imports.add("org.springframework.data.relational.core.mapping.Embedded");
        imports.add("org.springframework.data.relational.core.mapping.MappedCollection");
        imports.add("org.springframework.data.jdbc.core.mapping.AggregateReference");

        // Add special imports based on class properties
        boolean hasId = hasStringIdField(classDecl);
        if (hasId) {
            imports.add("org.springframework.data.annotation.Id");
            imports.add("java.util.UUID");
        }

        // Add imports for fields
        imports.addAll(collectRequiredImports(classDecl));

        // Add the main class import
        imports.add(config.sourcePackage + "." + className);

        // Generate the entity class
        String entityCode = builder
                .addPackage()
                .addImports(imports)
                .addEnumImports(classDecl)
                .addClassAnnotation()
                .beginClass()
                .addFields(classDecl)
                .addFromPojoMethod(classDecl)
                .addToPojoMethod(classDecl)
                .addAccessors(classDecl)
                .endClass()
                .build();

        // Write the generated entity to file
        Path targetFile = config.targetPath.resolve(className + "Entity.java");
        Files.write(targetFile, entityCode.getBytes());
    }

    private static void generateEntities(Map<String, CompilationUnit> compilationUnits, Config config) {
        ProcessingStats stats = new ProcessingStats();

        compilationUnits.forEach((className, sourceCU) -> {
            stats.totalProcessed.incrementAndGet();
            String fullyQualifiedName = config.sourcePackage + "." + className;

            if (!config.excludedClasses.contains(fullyQualifiedName)) {
                sourceCU.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                    if (!classDecl.isInterface()) {
                        try {
                            EntityGenerator.sourcePath = config.sourcePath;
                            generateEntity(classDecl, config);
                            System.out.println("Generated entity for class: " + className);
                            stats.totalEntitiesCreated.incrementAndGet();
                        } catch (Exception e) {
                            System.err.println("Error generating entity for " + className + ": " + e.getMessage());
                            stats.totalFailed.incrementAndGet();
                        }
                    }
                });
            } else {
                stats.totalSkipped.incrementAndGet();
            }
        });

        stats.printSummary(config);
    }

    private static boolean hasIdField(String typeName) {
        String qualifiedName = qualifiedNames.get(typeName);
        if (qualifiedName == null) {
            return false;
        }

        try {
            Path classPath = Paths.get(sourcePath.toString(), typeName + ".java");
            if (Files.exists(classPath)) {
                CompilationUnit cu = StaticJavaParser.parse(classPath.toFile());
                return cu.findFirst(ClassOrInterfaceDeclaration.class)
                        .map(classDecl -> classDecl.getFields().stream()
                                .anyMatch(field -> {
                                    if (field.getVariables().size() != 1) return false;
                                    VariableDeclarator var = field.getVariable(0);
                                    return "id".equals(var.getNameAsString());
                                }))
                        .orElse(false);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not check id field in class " + typeName + ": " + e.getMessage());
        }
        return false;
    }

    private static boolean hasStringIdField(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getFields().stream()
                .anyMatch(field -> {
                    if (field.getVariables().size() != 1) return false;
                    VariableDeclarator var = field.getVariable(0);
                    return "id".equals(var.getNameAsString()) &&
                            "String".equals(var.getTypeAsString());
                });
    }

    private static String toSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }

        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(camelCase.charAt(0)));

        for (int i = 1; i < camelCase.length(); i++) {
            char currentChar = camelCase.charAt(i);
            if (Character.isUpperCase(currentChar)) {
                result.append('_');
                result.append(Character.toLowerCase(currentChar));
            } else {
                result.append(currentChar);
            }
        }

        return result.toString();
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static class EntityCodeBuilder {

        private final StringBuilder code = new StringBuilder();
        private final String className;
        private final Config config;

        EntityCodeBuilder(String className, Config config) {
            this.className = className;
            this.config = config;
        }

        EntityCodeBuilder addPackage() {
            code.append("package ").append(config.targetPackage).append(";\n\n");
            return this;
        }

        EntityCodeBuilder addImports(Set<String> imports) {
            imports.forEach(imp -> code.append("import ").append(imp).append(";\n"));
            return this;
        }

        EntityCodeBuilder addEnumImports(ClassOrInterfaceDeclaration sourceClass) {
            sourceClass.findAll(EnumDeclaration.class).forEach(enumDecl -> {
                code.append("import static ").append(config.sourcePackage).append(".")
                        .append(className).append(".").append(enumDecl.getNameAsString())
                        .append(";\n");
            });
            code.append("\n");
            return this;
        }

        EntityCodeBuilder addClassAnnotation() {
            code.append("@org.springframework.data.relational.core.mapping.Table(\"")
                    .append(createTableName(toSnakeCase(className))).append("\")\n");
            return this;
        }

        EntityCodeBuilder beginClass() {
            code.append("public class ").append(className).append("Entity {\n\n");
            return this;
        }

        EntityCodeBuilder addFields(ClassOrInterfaceDeclaration classDecl) {
            boolean hasId = hasStringIdField(classDecl);
            if (hasId) {
                code.append("    @Id\n");
                code.append("    private UUID id;\n\n");
            }

            classDecl.getFields().forEach(field -> {
                String fieldName = field.getVariable(0).getNameAsString();
                if (!fieldName.equalsIgnoreCase("serialVersionUID") && !fieldName.equals("id")) {
                    generateField(field);
                }
            });
            return this;
        }

        private void generateField(FieldDeclaration field) {
            String fieldName = field.getVariable(0).getNameAsString();
            Type fieldType = field.getCommonType();

            if (shouldBeAggregateReference(field)) {
                // Add Column annotation with foreign key naming convention
                code.append("    @Column(\"").append(fieldName).append("_id\")\n");
                // Generate aggregate reference field
                String referencedType = fieldType.asString();
                code.append("    private AggregateReference<").append(referencedType)
                        .append("Entity, UUID> ").append(fieldName).append(";\n");
            } else if (isEmbeddedEntity(field)) {
                // Generate embedded entity field
                code.append("    @Embedded.Nullable\n");
                code.append("    private ").append(fieldType.asString())
                        .append("Entity ").append(fieldName).append(";\n");
            } else if (fieldType.isClassOrInterfaceType()) {
                var classType = fieldType.asClassOrInterfaceType();
                if (isCollectionType(classType.getNameAsString())) {
                    generateCollectionField(field, classType);
                } else {
                    generateSimpleField(field);
                }
            } else {
                generateSimpleField(field);
            }
        }

        private void generateCollectionField(FieldDeclaration field, ClassOrInterfaceType classType) {
            String fieldName = field.getVariable(0).getNameAsString();
            String elementType = classType.getTypeArguments()
                    .map(args -> args.get(0).asString())
                    .orElse("");

            if (hasIdField(elementType)) {
                // Collection of entities
                code.append("    @MappedCollection(idColumn = \"").append(toSnakeCase(fieldName))
                        .append("_id\"");
                if (classType.getNameAsString().equals("List")) {
                    code.append(", keyColumn = \"").append(toSnakeCase(fieldName))
                            .append("_position\"");
                }
                code.append(")\n");

                code.append("    private ").append(classType.getNameAsString())
                        .append("<").append(elementType).append("Entity> ")
                        .append(fieldName).append(";\n");
            } else {
                // Collection of simple types
                if (SqlReservedWords.RESERVED_WORDS.contains(fieldName.toUpperCase())) {
                    code.append("    @Column(\"").append(fieldName).append(SUFFIX + "\")\n");
                }
                code.append("    private ").append(classType.getNameAsString())
                        .append("<").append(elementType).append("> ")
                        .append(fieldName).append(";\n");
            }
        }

        private void generateSimpleField(FieldDeclaration field) {
            String fieldName = field.getVariable(0).getNameAsString();
            if (SqlReservedWords.RESERVED_WORDS.contains(fieldName.toUpperCase())) {
                code.append("    @Column(\"").append(fieldName).append(SUFFIX + "\")\n");
            }
            code.append("    private ").append(field.getCommonType().asString())
                    .append(" ").append(fieldName).append(";\n");
        }

        EntityCodeBuilder addFromPojoMethod(ClassOrInterfaceDeclaration classDecl) {
            code.append("\n    public static ").append(className).append("Entity fromPojo(")
                    .append(className).append(" pojo) {\n");
            code.append("        if (pojo == null) return null;\n");
            code.append("        ").append(className).append("Entity entity = new ")
                    .append(className).append("Entity();\n");

            if (hasStringIdField(classDecl)) {
                code.append("        entity.id = pojo.getId() != null ? UUID.fromString(pojo.getId()) : null;\n");
            }

            classDecl.getFields().forEach(field -> {
                String fieldName = field.getVariable(0).getNameAsString();
                if (!fieldName.equalsIgnoreCase("serialVersionUID") && !fieldName.equals("id")) {
                    generateFromPojoFieldAssignment(field);
                }
            });

            code.append("        return entity;\n");
            code.append("    }\n\n");
            return this;
        }

        private void generateFromPojoFieldAssignment(FieldDeclaration field) {
            String fieldName = field.getVariable(0).getNameAsString();
            Type fieldType = field.getCommonType();

            if (shouldBeAggregateReference(field)) {
                // Handle aggregate reference conversion
                code.append("        if (pojo.get").append(capitalize(fieldName))
                        .append("() != null && pojo.get").append(capitalize(fieldName))
                        .append("().getId() != null) {\n")
                        .append("            entity.").append(fieldName)
                        .append(" = AggregateReference.to(UUID.fromString(pojo.get")
                        .append(capitalize(fieldName)).append("().getId()));\n")
                        .append("        }\n");
            } else if (isEmbeddedEntity(field)) {
                // Handle embedded entity conversion
                code.append("        entity.").append(fieldName)
                        .append(" = ").append(fieldType.asString()).append("Entity.fromPojo(pojo.get")
                        .append(capitalize(fieldName)).append("());\n");
            } else if (fieldType.isClassOrInterfaceType()) {
                var classType = fieldType.asClassOrInterfaceType();
                if (isCollectionType(classType.getNameAsString())) {
                    generateFromPojoCollectionAssignment(fieldName, classType);
                } else {
                    generateSimpleFromPojoAssignment(fieldName);
                }
            } else {
                generateSimpleFromPojoAssignment(fieldName);
            }
        }

        private void generateFromPojoCollectionAssignment(String fieldName, ClassOrInterfaceType classType) {
            String elementType = classType.getTypeArguments()
                    .map(args -> args.get(0).asString())
                    .orElse("");

            if (hasIdField(elementType)) {
                // Collection of entities
                code.append("        entity.").append(fieldName)
                        .append(" = pojo.get").append(capitalize(fieldName))
                        .append("() != null ? pojo.get").append(capitalize(fieldName))
                        .append("().stream().map(").append(elementType)
                        .append("Entity::fromPojo).collect(java.util.stream.Collectors.to")
                        .append(classType.getNameAsString()).append("()) : null;\n");
            } else {
                // Collection of simple types
                generateSimpleFromPojoAssignment(fieldName);
            }
        }

        private void generateSimpleFromPojoAssignment(String fieldName) {
            code.append("        entity.").append(fieldName)
                    .append(" = pojo.get").append(capitalize(fieldName))
                    .append("();\n");
        }

        EntityCodeBuilder addToPojoMethod(ClassOrInterfaceDeclaration classDecl) {
            code.append("    public ").append(className).append(" toPojo() {\n");
            code.append("        ").append(className).append(" pojo = new ")
                    .append(className).append("();\n");

            if (hasStringIdField(classDecl)) {
                code.append("        pojo.setId(this.id != null ? this.id.toString() : null);\n");
            }

            classDecl.getFields().forEach(field -> {
                String fieldName = field.getVariable(0).getNameAsString();
                if (!fieldName.equalsIgnoreCase("serialVersionUID") && !fieldName.equals("id")) {
                    generateToPojoFieldAssignment(field);
                }
            });

            code.append("        return pojo;\n");
            code.append("    }\n\n");
            return this;
        }

        private void generateToPojoFieldAssignment(FieldDeclaration field) {
            String fieldName = field.getVariable(0).getNameAsString();
            Type fieldType = field.getCommonType();

            if (shouldBeAggregateReference(field)) {
                // For aggregate references, we only set the ID
                code.append("        if (this.").append(fieldName).append(" != null) {\n")
                        .append("            ").append(fieldType.asString()).append(" ref = new ")
                        .append(fieldType.asString()).append("();\n")
                        .append("            ref.setId(this.").append(fieldName)
                        .append(".getId().toString());\n")
                        .append("            pojo.set").append(capitalize(fieldName))
                        .append("(ref);\n")
                        .append("        }\n");
            } else if (isEmbeddedEntity(field)) {
                code.append("        pojo.set").append(capitalize(fieldName))
                        .append("(this.").append(fieldName)
                        .append(" != null ? this.").append(fieldName)
                        .append(".toPojo() : null);\n");
            } else if (fieldType.isClassOrInterfaceType()) {
                var classType = fieldType.asClassOrInterfaceType();
                if (isCollectionType(classType.getNameAsString())) {
                    generateToPojoCollectionAssignment(fieldName, classType);
                } else {
                    generateSimpleToPojoAssignment(fieldName);
                }
            } else {
                generateSimpleToPojoAssignment(fieldName);
            }
        }

        private void generateToPojoCollectionAssignment(String fieldName, ClassOrInterfaceType classType) {
            String elementType = classType.getTypeArguments()
                    .map(args -> args.get(0).asString())
                    .orElse("");

            if (hasIdField(elementType)) {
                code.append("        pojo.set").append(capitalize(fieldName))
                        .append("(this.").append(fieldName)
                        .append(" != null ? this.").append(fieldName)
                        .append(".stream().map(e -> e.toPojo()).collect(java.util.stream.Collectors.to")
                        .append(classType.getNameAsString()).append("()) : null);\n");
            } else {
                generateSimpleToPojoAssignment(fieldName);
            }
        }

        private void generateSimpleToPojoAssignment(String fieldName) {
            code.append("        pojo.set").append(capitalize(fieldName))
                    .append("(this.").append(fieldName).append(");\n");
        }

        EntityCodeBuilder addAccessors(ClassOrInterfaceDeclaration classDecl) {
            // Generate ID accessors if needed
            if (hasStringIdField(classDecl)) {
                generateAccessor("UUID", "id");
            }

            // Generate accessors for all other fields
            classDecl.getFields().forEach(field -> {
                String fieldName = field.getVariable(0).getNameAsString();
                if (!fieldName.equalsIgnoreCase("serialVersionUID") && !fieldName.equals("id")) {
                    generateFieldAccessor(field);
                }
            });
            return this;
        }

        private void generateFieldAccessor(FieldDeclaration field) {
            String fieldName = field.getVariable(0).getNameAsString();
            Type fieldType = field.getCommonType();

            if (shouldBeAggregateReference(field)) {
                generateAggregateReferenceAccessors(fieldName, fieldType.asString());
            } else if (isEmbeddedEntity(field)) {
                generateEmbeddedEntityAccessors(fieldName, fieldType.asString());
            } else if (fieldType.isClassOrInterfaceType()) {
                var classType = fieldType.asClassOrInterfaceType();
                if (isCollectionType(classType.getNameAsString())) {
                    generateCollectionAccessors(fieldName, classType);
                } else {
                    generateAccessor(fieldType.asString(), fieldName);
                }
            } else {
                generateAccessor(fieldType.asString(), fieldName);
            }
        }

        private void generateAggregateReferenceAccessors(String fieldName, String typeName) {
            // Simple getter
            code.append("    public AggregateReference<").append(typeName)
                    .append("Entity, UUID> get").append(capitalize(fieldName))
                    .append("() {\n")
                    .append("        return this.").append(fieldName).append(";\n")
                    .append("    }\n\n");

            // Simple setter
            code.append("    public void set").append(capitalize(fieldName))
                    .append("(AggregateReference<").append(typeName)
                    .append("Entity, UUID> ").append(fieldName).append(") {\n")
                    .append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n")
                    .append("    }\n\n");
        }

        private void generateEmbeddedEntityAccessors(String fieldName, String typeName) {
            // Getter
            code.append("    public ").append(typeName).append(" get")
                    .append(capitalize(fieldName)).append("() {\n")
                    .append("        return this.").append(fieldName)
                    .append(" != null ? this.").append(fieldName)
                    .append(".toPojo() : null;\n")
                    .append("    }\n\n");

            // Setter
            code.append("    public void set").append(capitalize(fieldName))
                    .append("(").append(typeName).append(" ").append(fieldName).append(") {\n")
                    .append("        this.").append(fieldName).append(" = ")
                    .append(typeName).append("Entity.fromPojo(").append(fieldName).append(");\n")
                    .append("    }\n\n");
        }

        private void generateCollectionAccessors(String fieldName, ClassOrInterfaceType classType) {
            String collectionType = classType.getNameAsString();
            String elementType = classType.getTypeArguments()
                    .map(args -> args.get(0).asString())
                    .orElse("");

            if (hasIdField(elementType)) {
                // Collection of entities
                generateEntityCollectionAccessors(fieldName, collectionType, elementType);
            } else {
                // Collection of simple types
                generateAccessor(classType.asString(), fieldName);
            }
        }

        private void generateEntityCollectionAccessors(String fieldName, String collectionType, String elementType) {
            String publicType = collectionType + "<" + elementType + ">";
            String privateType = collectionType + "<" + elementType + "Entity>";

            // Getter
            code.append("    public ").append(publicType).append(" get")
                    .append(capitalize(fieldName)).append("() {\n")
                    .append("        return this.").append(fieldName)
                    .append(" != null ? this.").append(fieldName)
                    .append(".stream().map(e -> e.toPojo()).collect(java.util.stream.Collectors.to")
                    .append(collectionType).append("()) : null;\n")
                    .append("    }\n\n");

            // Setter
            code.append("    public void set").append(capitalize(fieldName))
                    .append("(").append(publicType).append(" ").append(fieldName).append(") {\n")
                    .append("        this.").append(fieldName).append(" = ")
                    .append(fieldName).append(" != null ? ")
                    .append(fieldName).append(".stream().map(").append(elementType)
                    .append("Entity::fromPojo).collect(java.util.stream.Collectors.to")
                    .append(collectionType).append("()) : null;\n")
                    .append("    }\n\n");
        }

        private void generateAccessor(String type, String name) {
            // Getter
            code.append("    public ").append(type).append(" get")
                    .append(capitalize(name)).append("() {\n")
                    .append("        return this.").append(name).append(";\n")
                    .append("    }\n\n");

            // Setter
            code.append("    public void set").append(capitalize(name))
                    .append("(").append(type).append(" ").append(name).append(") {\n")
                    .append("        this.").append(name).append(" = ").append(name).append(";\n")
                    .append("    }\n\n");
        }

        EntityCodeBuilder endClass() {
            code.append("}\n");
            return this;
        }

        String build() {
            return code.toString();
        }
    }

    // Type checking utilities
    private static boolean shouldBeAggregateReference(FieldDeclaration field) {
        if (!field.getCommonType().isClassOrInterfaceType()) {
            return false;
        }

        String typeName = field.getCommonType().asString();
        // Check if the referenced type has an ID field and is not a collection
        return hasIdField(typeName) && !isCollectionType(typeName);
    }

    private static boolean isEmbeddedEntity(FieldDeclaration field) {
        if (!field.getCommonType().isClassOrInterfaceType()) {
            return false;
        }

        String typeName = field.getCommonType().asString();
        // An entity should be embedded if it's a model class (in our qualified names)
        // but doesn't have an ID field
        return qualifiedNames.containsKey(typeName) && !hasIdField(typeName);
    }

    private static boolean isCollectionType(String typeName) {
        return typeName.equals("List") ||
                typeName.equals("Set") ||
                typeName.equals("Collection") ||
                typeName.equals("ArrayList") ||
                typeName.equals("HashSet");
    }

    private static boolean isMapType(String typeName) {
        return typeName.equals("Map") ||
                typeName.equals("HashMap") ||
                typeName.equals("LinkedHashMap") ||
                typeName.equals("TreeMap");
    }

    private static boolean isMathType(String typeName) {
        return typeName.equals("BigDecimal") ||
                typeName.equals("BigInteger");
    }

    private static boolean isNetType(String typeName) {
        return typeName.equals("URI") ||
                typeName.equals("URL") ||
                typeName.equals("Socket") ||
                typeName.equals("Inet4Address") ||
                typeName.equals("Inet6Address");
    }

    private static boolean isTimeType(String typeName) {
        return typeName.matches("^(LocalDate|LocalDateTime|ZonedDateTime|Instant|OffsetDateTime).*");
    }

    // Table name generation
    private static String createTableName(String input) {
        if (SqlReservedWords.RESERVED_WORDS.contains(input.toUpperCase())) {
            return input + SUFFIX;
        }
        return input;
    }

    // Import collection
    private static Set<String> collectRequiredImports(ClassOrInterfaceDeclaration sourceClass) {
        Set<String> imports = new HashSet<>();
        String className = sourceClass.getNameAsString();

        sourceClass.getFields().forEach(field -> {
            Type type = field.getCommonType();
            processTypeForImports(type, imports, className);
        });
        return imports;
    }

    private static void processTypeForImports(Type type, Set<String> imports, String contextClassName) {
        if (type.isClassOrInterfaceType()) {
            var classType = type.asClassOrInterfaceType();
            String typeName = classType.getNameAsString();

            if (typeName.endsWith("Enum")) {
                // Skip enum import as it will be handled by static import
                return;
            }

            // Handle collection types
            if (isCollectionType(typeName)) {
                imports.add("java.util." + typeName);
                classType.getTypeArguments().ifPresent(
                        typeArgs -> typeArgs.forEach(arg -> processTypeForImports(arg, imports, contextClassName)));
            }
            // Handle map types
            else if (isMapType(typeName)) {
                imports.add("java.util." + typeName);
                classType.getTypeArguments().ifPresent(
                        typeArgs -> typeArgs.forEach(arg -> processTypeForImports(arg, imports, contextClassName)));
            }
            // Handle math types
            else if (isMathType(typeName)) {
                imports.add("java.math." + typeName);
            }
            // Handle net types
            else if (isNetType(typeName)) {
                imports.add("java.net." + typeName);
            }
            // Handle time types
            else if (isTimeType(typeName)) {
                imports.add("java.time." + typeName);
            }
            // Handle model types
            else if (Character.isUpperCase(typeName.charAt(0))) {
                String qualifiedName = qualifiedNames.get(typeName);
                if (qualifiedName != null) {
                    imports.add(qualifiedName);
                }
            }

            // Process generic type arguments
            classType.getTypeArguments().ifPresent(
                    typeArgs -> typeArgs.forEach(arg -> processTypeForImports(arg, imports, contextClassName)));
        }
    }

    static class SqlReservedWords {

        static final Set<String> RESERVED_WORDS = Set.of(
                "A", "ABORT", "ABS", "ABSOLUTE", "ACCESS", "ACTION", "ADA", "ADD", "ADMIN", "AFTER", "AGGREGATE",
                "ALIAS", "ALL", "ALLOCATE", "ALSO", "ALTER", "ALWAYS", "ANALYSE", "ANALYZE", "AND", "ANY", "ARE",
                "ARRAY", "AS", "ASC", "ASENSITIVE", "ASSERTION", "ASSIGNMENT", "ASYMMETRIC", "AT", "ATOMIC",
                "ATTRIBUTE", "ATTRIBUTES", "AUTHORIZATION", "AVG", "BEFORE", "BEGIN", "BETWEEN", "BIGINT",
                "BINARY", "BIT", "BIT_LENGTH", "BLOB", "BOOLEAN", "BOTH", "BREADTH", "BY", "C", "CALL",
                "CALLED", "CARDINALITY", "CASCADE", "CASCADED", "CASE", "CAST", "CATALOG", "CATALOG_NAME",
                "CEIL", "CEILING", "CHAIN", "CHAR", "CHARACTER", "CHARACTERISTICS", "CHARACTER_LENGTH",
                "CHAR_LENGTH", "CHECK", "CHECKPOINT", "CLASS", "CLASS_ORIGIN", "CLOB", "CLOSE", "CLUSTER",
                "COALESCE", "COBOL", "COLLATE", "COLLATION", "COLLATION_CATALOG", "COLLATION_NAME",
                "COLLATION_SCHEMA", "COLLECT", "COLUMN", "COLUMN_NAME", "COMMAND_FUNCTION",
                "COMMAND_FUNCTION_CODE", "COMMENT", "COMMIT", "COMMITTED", "COMPLETION", "CONDITION",
                "CONDITION_NUMBER", "CONNECT", "CONNECTION", "CONNECTION_NAME", "CONSTRAINT",
                "CONSTRAINT_CATALOG", "CONSTRAINT_NAME", "CONSTRAINT_SCHEMA", "CONSTRAINTS",
                "CONSTRUCTOR", "CONTAINS", "CONTINUE", "CONVERSION", "CONVERT", "COPY", "CORR",
                "CORRESPONDING", "COUNT", "COVAR_POP", "COVAR_SAMP", "CREATE", "CROSS", "CUBE", "CUME_DIST",
                "CURRENT", "CURRENT_DATE", "CURRENT_DEFAULT_TRANSFORM_GROUP", "CURRENT_PATH",
                "CURRENT_ROLE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_TRANSFORM_GROUP_FOR_TYPE",
                "CURRENT_USER", "CURSOR", "CYCLE", "DATA", "DATE", "DATETIME_INTERVAL_CODE",
                "DATETIME_INTERVAL_PRECISION", "DAY", "DEALLOCATE", "DEC", "DECIMAL", "DECLARE", "DEFAULT",
                "DEFERRABLE", "DEFERRED", "DEFINED", "DEFINER", "DEGREE", "DELETE", "DENSE_RANK", "DEPTH",
                "DEREF", "DERIVED", "DESC", "DESCRIBE", "DESCRIPTOR", "DETERMINISTIC", "DIAGNOSTICS",
                "DISCONNECT", "DISTINCT", "DO", "DOMAIN", "DOUBLE", "DROP", "DYNAMIC", "DYNAMIC_FUNCTION",
                "DYNAMIC_FUNCTION_CODE", "EACH", "ELEMENT", "ELSE", "ELSEIF", "END", "END-EXEC", "EQUALS",
                "ESCAPE", "EVERY", "EXCEPT", "EXCEPTION", "EXEC", "EXECUTE", "EXISTS", "EXIT", "EXTERNAL",
                "EXTRACT", "FALSE", "FETCH", "FILTER", "FIRST", "FIRST_VALUE", "FLOAT", "FLOOR", "FOR",
                "FOREIGN", "FORTRAN", "FOUND", "FREE", "FREEZE", "FROM", "FULL", "FUNCTION", "FUSION", "G",
                "GENERAL", "GENERATED", "GET", "GLOBAL", "GO", "GOTO", "GRANT", "GRANTED", "GROUP", "GROUPING",
                "HANDLER", "HAVING", "HIERARCHY", "HOLD", "HOUR", "IDENTITY", "IF", "IGNORE", "ILIKE",
                "IMMEDIATE", "IMMUTABLE", "IMPLEMENTATION", "IMPLICIT", "IN", "INCLUDING", "INCREMENT",
                "INDEX", "INDICATOR", "INFIX", "INHERIT", "INHERITS", "INITIAL", "INITIALIZE", "INITIALLY",
                "INNER", "INPUT", "INSENSITIVE", "INSERT", "INSTANCE", "INSTANTIABLE", "INSTEAD", "INT",
                "INTEGER", "INTERSECT", "INTERSECTION", "INTERVAL", "INTO", "INVOKER", "IS", "ISNULL",
                "ISOLATION", "ITERATE", "JOIN", "K", "KEY", "KEY_MEMBER", "KEY_TYPE", "LANGUAGE", "LARGE",
                "LAST", "LAST_VALUE", "LATERAL", "LEADING", "LEAST", "LEFT", "LENGTH", "LESS", "LEVEL", "LIKE",
                "LIMIT", "LN", "LOCAL", "LOCALTIME", "LOCALTIMESTAMP", "LOCATOR", "LOWER", "M", "MAP", "MATCH",
                "MATCHED", "MAX", "MAX_CARDINALITY", "MEMBER", "MERGE", "MESSAGE_LENGTH", "MESSAGE_OCTET_LENGTH",
                "MESSAGE_TEXT", "METHOD", "MIN", "MINUTE", "MINVALUE", "MOD", "MODE", "MODIFIES", "MODIFY",
                "MODULE", "MONTH", "MORE", "MOVE", "MULTISET", "MUMPS", "NAME", "NAMES", "NATIONAL", "NATURAL",
                "NCHAR", "NCLOB", "NESTING", "NEW", "NEXT", "NO", "NONE", "NORMALIZE", "NORMALIZED", "NOT", "NULL",
                "NULLABLE", "NULLIF", "NUMERIC", "OBJECT", "OCTET_LENGTH", "OF", "OFF", "OFFSET", "OLD", "ON",
                "ONLY", "OPEN", "OPERATION", "OPTION", "OPTIONS", "OR", "ORDER", "ORDERING", "ORDINALITY",
                "OTHERS", "OUT", "OUTER", "OUTPUT", "OVER", "OVERLAPS", "OVERLAY", "OVERRIDING", "PAD",
                "PARAMETER", "PARAMETER_MODE", "PARAMETER_NAME", "PARAMETER_ORDINAL_POSITION",
                "PARAMETER_SPECIFIC_CATALOG", "PARAMETER_SPECIFIC_NAME", "PARAMETER_SPECIFIC_SCHEMA",
                "PARTIAL", "PARTITION", "PASCAL", "PATH", "PERCENT", "PERCENTILE_CONT", "PERCENTILE_DISC",
                "PERCENT_RANK", "PLACING", "PLI", "POSITION", "POSTFIX", "POWER", "PRECEDING", "PRECISION",
                "PREFIX", "PREORDER", "PREPARE", "PRESERVE", "PRIMARY", "PRIOR", "PRIVILEGES", "PROCEDURE",
                "PUBLIC", "RANGE", "RANK", "READ", "READS", "REAL", "RECURSIVE", "REF", "REFERENCES",
                "REFERENCING", "REGR_AVGX", "REGR_AVGY", "REGR_COUNT", "REGR_INTERCEPT", "REGR_R2",
                "REGR_SLOPE", "REGR_SXX", "REGR_SXY", "REGR_SYY", "RELATIVE", "RELEASE", "REPEAT", "RESTRICT",
                "RESULT", "RETURN", "RETURNED_CARDINALITY", "RETURNED_LENGTH", "RETURNED_OCTET_LENGTH",
                "RETURNED_SQLSTATE", "RETURNS", "REVOKE", "RIGHT", "ROLE", "ROLLBACK", "ROLLUP", "ROUTINE",
                "ROW", "ROWS", "ROW_COUNT", "ROW_NUMBER", "SAVEPOINT", "SCALE", "SCHEMA", "SCHEMA_NAME",
                "SCOPE", "SCROLL", "SEARCH", "SECOND", "SECTION", "SECURITY", "SELECT", "SELF", "SENSITIVE",
                "SEQUENCE", "SERIALIZABLE", "SERVER_NAME", "SESSION", "SESSION_USER", "SET", "SETOF", "SETS",
                "SIMILAR", "SIZE", "SMALLINT", "SOME", "SOURCE", "SPACE", "SPECIFIC", "SPECIFICTYPE",
                "SPECIFIC_NAME", "SQL", "SQLCODE", "SQLERROR", "SQLEXCEPTION", "SQLSTATE", "SQLWARNING",
                "SQRT", "STABLE", "START", "STATE", "STATEMENT", "STATIC", "STATISTICS", "STDDEV_POP",
                "STDDEV_SAMP", "STDIN", "STDOUT", "STORAGE", "STRING", "STRUCTURE", "STYLE", "SUBCLASS_ORIGIN",
                "SUBMULTISET", "SUBSTRING", "SUM", "SYMMETRIC", "SYSTEM", "SYSTEM_USER", "TABLE", "TABLE_NAME",
                "TABLESAMPLE", "TEMPORARY", "TERMINATE", "THAN", "THEN", "TIME", "TIMESTAMP", "TIMEZONE_HOUR",
                "TIMEZONE_MINUTE", "TO", "TRAILING", "TRANSACTION", "TRANSACTION_ACTIVE",
                "TRANSACTIONS_COMMITTED", "TRANSACTIONS_ROLLED_BACK", "TRANSFORM", "TRANSFORMS",
                "TRANSLATE", "TRANSLATION", "TREAT", "TRIGGER", "TRIGGER_CATALOG", "TRIGGER_NAME",
                "TRIGGER_SCHEMA", "TRIM", "TRUE", "TRUNCATE", "TYPE", "UESCAPE", "UNDER", "UNION", "UNIQUE",
                "UNKNOWN", "UNNEST", "UPDATE", "UPPER", "USAGE", "USER", "USER_DEFINED_TYPE_CATALOG",
                "USER_DEFINED_TYPE_CODE", "USER_DEFINED_TYPE_NAME", "USER_DEFINED_TYPE_SCHEMA", "USING",
                "VALUE", "VALUES", "VAR_POP", "VAR_SAMP", "VARCHAR", "VARIABLE", "VARYING", "VERBOSE",
                "VERSION", "VIEW", "WHEN", "WHENEVER", "WHERE", "WIDTH", "WITH", "WITHIN", "WITHOUT", "WORK",
                "WRITE", "YEAR", "ZONE");
    }
}