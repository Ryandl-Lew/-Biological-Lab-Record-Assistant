package com.bionote.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LayerDependencyTest {
    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final List<String> FORBIDDEN_PERSISTENCE_DEPENDENCIES =
            List.of(
                    "org.springframework.jdbc", "jakarta.persistence.EntityManager",
                    "org.springframework.data.jpa.repository",
                            "org.springframework.data.repository");
    private static final Pattern SQL =
            Pattern.compile("(?i)\\\"\\s*(SELECT|INSERT|UPDATE|DELETE)\\s");
    private static final Pattern PACKAGE = Pattern.compile("package\\s+([\\w.]+)\\s*;");
    private static final Pattern CONCRETE_SERVICE =
            Pattern.compile("\\b[A-Z][A-Za-z0-9]*Service\\b");

    @Test
    void persistenceTechnologyAndSqlStayInsideInfrastructure() throws Exception {
        for (Path source : javaSources()) {
            if (isInfrastructure(source)) continue;
            String code = Files.readString(source);
            for (String dependency : FORBIDDEN_PERSISTENCE_DEPENDENCIES) {
                assertFalse(code.contains(dependency), () -> source + " depends on " + dependency);
            }
            assertFalse(
                    SQL.matcher(code).find(),
                    () -> source + " contains SQL outside infrastructure");
        }
    }

    @Test
    void applicationAndDomainDoNotDependOnInfrastructure() throws Exception {
        for (Path source : javaSources()) {
            if (isInfrastructure(source)) continue;
            String code = Files.readString(source);
            assertFalse(
                    code.contains(".infrastructure."),
                    () -> source + " imports infrastructure code");
        }
    }

    @Test
    void controllersDependOnlyOnInterfaceCollaborators() throws Exception {
        for (Path source :
                javaSources().stream()
                        .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                        .toList()) {
            String code = Files.readString(source);
            assertFalse(
                    CONCRETE_SERVICE.matcher(code).find(),
                    () -> source + " references a concrete Service type");
            Matcher packageMatcher = PACKAGE.matcher(code);
            assertTrue(packageMatcher.find(), () -> "Missing package declaration in " + source);
            String simpleName = source.getFileName().toString().replace(".java", "");
            Class<?> controller = Class.forName(packageMatcher.group(1) + "." + simpleName);
            Stream.of(controller.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .forEach(
                            field -> {
                                assertTrue(
                                        Modifier.isFinal(field.getModifiers()),
                                        () ->
                                                controller.getName()
                                                        + "."
                                                        + field.getName()
                                                        + " is not final");
                                assertTrue(
                                        field.getType().isInterface(),
                                        () ->
                                                controller.getName()
                                                        + "."
                                                        + field.getName()
                                                        + " uses concrete "
                                                        + field.getType().getName());
                            });
        }
    }

    private List<Path> javaSources() throws Exception {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private boolean isInfrastructure(Path source) {
        return source.toString().replace('\\', '/').contains("/infrastructure/");
    }
}
