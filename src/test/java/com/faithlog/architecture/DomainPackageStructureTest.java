package com.faithlog.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DomainPackageStructureTest {

    private static final Set<String> DOMAINS = Set.of(
            "admin",
            "batch",
            "billing",
            "campus",
            "devotion",
            "notification",
            "poll",
            "prayer",
            "user");

    private final Path mainSourceRoot = Path.of("src/main/java/com/faithlog");
    private final Path testSourceRoot = Path.of("src/test/java/com/faithlog");

    @Test
    void productionPackagesFollowDomainMvcStructure() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources(mainSourceRoot)) {
            String relativePath = normalized(mainSourceRoot.relativize(source));
            String domain = firstSegment(relativePath);
            String fileName = source.getFileName().toString();

            if (DOMAINS.contains(domain)) {
                rejectLegacyPackages(relativePath, violations);
                verifyResponsibilityPackage(relativePath, fileName, violations);
            }

            if (domain.equals("global") && relativePath.startsWith("global/presentation/")) {
                violations.add(relativePath + " -> global controller 책임은 global/controller에 둔다");
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void testPackagesMirrorProductionStructure() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources(testSourceRoot)) {
            String relativePath = normalized(testSourceRoot.relativize(source));
            String domain = firstSegment(relativePath);
            if (DOMAINS.contains(domain)) {
                rejectLegacyPackages(relativePath, violations);
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    private void rejectLegacyPackages(String relativePath, List<String> violations) {
        if (relativePath.contains("/application/")) {
            violations.add(relativePath + " -> application 대신 service 책임 패키지를 사용한다");
        }
        if (relativePath.contains("/presentation/")) {
            violations.add(relativePath + " -> presentation 대신 controller 패키지를 사용한다");
        }
        if (relativePath.contains("/infrastructure/jpa/")) {
            violations.add(relativePath + " -> JPA repository는 infrastructure/repository에 둔다");
        }
    }

    private void verifyResponsibilityPackage(
            String relativePath, String fileName, List<String> violations) throws IOException {
        if (fileName.equals("package-info.java")) {
            return;
        }

        if (fileName.endsWith("Controller.java") && !relativePath.contains("/controller/")) {
            violations.add(relativePath + " -> Controller는 controller에 둔다");
        }
        if (relativePath.contains("/controller/") && fileName.endsWith("Request.java")
                && !relativePath.contains("/controller/dto/request/")) {
            violations.add(relativePath + " -> Request DTO는 controller/dto/request에 둔다");
        }
        if (relativePath.contains("/controller/") && fileName.endsWith("Response.java")
                && !relativePath.contains("/controller/dto/response/")) {
            violations.add(relativePath + " -> Response DTO는 controller/dto/response에 둔다");
        }
        if (fileName.endsWith("Command.java") && !relativePath.contains("/service/command/")) {
            violations.add(relativePath + " -> Command는 service/command에 둔다");
        }
        if ((fileName.endsWith("Query.java") || fileName.endsWith("Criteria.java"))
                && !relativePath.contains("/service/query/")) {
            violations.add(relativePath + " -> Query/Criteria는 service/query에 둔다");
        }
        if (fileName.endsWith("Result.java") && !relativePath.contains("/service/result/")
                && !relativePath.contains("/domain/type/")) {
            violations.add(relativePath + " -> application Result는 service/result에 둔다");
        }
        if (fileName.endsWith("Policy.java") && !relativePath.contains("/service/policy/")) {
            violations.add(relativePath + " -> Policy는 service/policy에 둔다");
        }
        if (fileName.endsWith("Port.java") && !relativePath.contains("/service/port/")) {
            violations.add(relativePath + " -> Port는 service/port에 둔다");
        }
        if (fileName.endsWith("Repository.java")
                && !relativePath.contains("/infrastructure/repository/")) {
            violations.add(relativePath + " -> Repository는 infrastructure/repository에 둔다");
        }

        Path source = mainSourceRoot.resolve(relativePath);
        String content = Files.readString(source);
        if (content.contains("@Entity") && !relativePath.contains("/domain/entity/")) {
            violations.add(relativePath + " -> Entity는 domain/entity에 둔다");
        }
        if (content.matches("(?s).*\\b(enum|public\\s+enum)\\s+\\w+.*")
                && relativePath.contains("/domain/")
                && !relativePath.contains("/domain/type/")) {
            violations.add(relativePath + " -> domain enum은 domain/type에 둔다");
        }
    }

    private List<Path> javaSources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String firstSegment(String path) {
        int separator = path.indexOf('/');
        return separator < 0 ? path : path.substring(0, separator);
    }
}
