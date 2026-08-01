package com.jloom.cli;

import com.jloom.framework.FrameworkSupport;
import com.jloom.orchestrate.ModuleApplier;
import com.jloom.orchestrate.ModuleApplier.ApplyResult;
import com.jloom.registry.ArchetypeRegistry;
import com.jloom.registry.ServiceManifest;
import com.jloom.registry.ServiceRegistry;
import com.jloom.util.ProjectPaths;
import jakarta.validation.constraints.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Command(name = "new", mixinStandardHelpOptions = true, description = "Create a new project. Generates immediately; pass --dry-run to preview.")
public class NewCmd extends CliCommand implements Runnable {

    private static final String DEFAULT_PROJECT_NAME = "my-app";
    private static final String DEFAULT_BASE_PACKAGE = "com.example.app";

    @ParentCommand
    JloomCommand parent;

    @Option(names = "--name", description = "Project name / target directory.")
    String name;

    @Option(names = "--service", description = "Service-type id, e.g. 'notification-service' (omit for a bare base project).")
    String service;

    @Option(names = "--framework", description = "Framework (spring-boot | micronaut) — only used if the service supports more than one.")
    String framework;

    @Option(names = "--base-package",
            description = "Base Java package.",
            defaultValue = DEFAULT_BASE_PACKAGE)
    @Pattern(regexp = "[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*",
            message = "must be a valid dotted Java package name, e.g. com.acme.myapp")
    String basePackage;

    @Option(names = "--archetype", description = "Apply an archetype's modules on top.")
    String archetype;

    @Option(names = "--database",
            description = "Database for a bare project: postgres | mysql | mariadb | h2 | none. Only used if --service is not set.",
            completionCandidates = NewCmd.DatabaseCandidates.class)
    String database;

    @Option(names = "--capabilities",
            description = "Comma-separated capabilities for a bare project, e.g. validation,security,caching.")
    String capabilities;

    @Option(names = "--cache-provider", description = "caffeine | redis. Only relevant if 'caching' is in --capabilities.",
            completionCandidates = NewCmd.CacheProviderCandidates.class)
    String cacheProvider;

    @Option(names = "--dry-run", defaultValue = "false", description = "Preview without writing.")
    boolean dryRun;

    @Override
    public void run() {
        JloomContext ctx = parent.context();
        JloomPrompts prompts = ctx.prompts();

        if (!quiet && !StringUtils.hasText(name) && prompts.isInteractive()) {
            System.out.println("Let's set up your project — press Enter on any question to accept the default.\n");
        }
        Path target = resolveTarget(name, prompts, ctx.services(), ctx.archetypes());

        String serviceId = prompts.chooseOptional(service, "service", "What would you like to create?",
                ctx.services().all().stream().collect(Collectors.toMap(
                        s -> s.id() + " — " + s.displayName(), ServiceManifest::id)),
                "Just a base project");

        List<String> moduleIds = serviceId == null
                ? buildCapabilityWizard(database, capabilities, cacheProvider, prompts)
                : modulesForService(ctx.services(), serviceId, framework, prompts);

        Map<String, String> archetypeAnswers = Map.of();
        if (archetype != null) {
            var manifest = ctx.archetypes().find(archetype)
                    .orElseThrow(() -> new IllegalArgumentException("No such archetype: " + archetype));
            moduleIds.addAll(manifest.modules());
            archetypeAnswers = manifest.answers();
        }

        String resolvedBasePackage = prompts.promptWithDefault(basePackage, "base-package", "Base package", DEFAULT_BASE_PACKAGE);

        if (!quiet) {
            System.out.println((dryRun ? "Previewing " : "Setting up ") + target + "...");
        }
        ApplyResult result = ctx.applier().apply(target, moduleIds, archetypeAnswers, dryRun,
                resolvedBasePackage, target.getFileName().toString());
        switch (result) {
            case ApplyResult.Applied ignored -> System.out.println("""
                    %s

                    %s
                      cd %s
                      ./gradlew test
                    """.formatted(JloomOutput.success("Created " + target),
                            JloomOutput.heading("Next steps:"), target));
            case ApplyResult.DryRun ignored -> System.out.println("Dry run — would create " + target + " with modules " + moduleIds);
            case ApplyResult.Rejected rejected -> throw new IllegalArgumentException(formatProblems(rejected.problems()));
            case ApplyResult.Failed f -> throw new IllegalStateException(f.output());
        }
    }

    private Path resolveTarget(String name, JloomPrompts prompts, ServiceRegistry services, ArchetypeRegistry archetypes) {
        String candidate = name;
        while (true) {
            String resolved = prompts.requireText(candidate, "name", "Project name", suggestProjectName(services, archetypes));
            Path p = Path.of(resolved);
            if (ProjectPaths.isEmpty(p)) {
                return p;
            }
            if (!prompts.isInteractive()) {
                ProjectPaths.requireEmpty(p);
            }
            System.out.println(JloomOutput.error("'" + p.toAbsolutePath() + "' already exists and isn't empty."));
            candidate = null;
        }
    }

    private static String suggestProjectName(ServiceRegistry services, ArchetypeRegistry archetypes) {
        if (ProjectPaths.isEmpty(Path.of(DEFAULT_PROJECT_NAME))) {
            return DEFAULT_PROJECT_NAME;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = DEFAULT_PROJECT_NAME + "-" + i;
            if (ProjectPaths.isEmpty(Path.of(candidate))) {
                return candidate;
            }
        }
        return DEFAULT_PROJECT_NAME;
    }

    private List<String> modulesForService(ServiceRegistry services, String serviceId, String framework, JloomPrompts prompts) {
        ServiceManifest svc = services.require(serviceId);
        String chosenFramework = resolveFramework(svc, framework, prompts);
        if (!svc.framework().contains(chosenFramework)) {
            throw new IllegalArgumentException(
                    "Service '" + serviceId + "' does not support framework '" + chosenFramework
                            + "'. Supported: " + svc.framework());
        }
        FrameworkSupport fw = FrameworkSupport.byId(chosenFramework);
        return expandForFramework(svc.modulesFor(chosenFramework), fw);
    }

    private String resolveFramework(ServiceManifest svc, String framework, JloomPrompts prompts) {
        String preferred = svc.framework().contains("spring-boot") ? "spring-boot" : svc.framework().get(0);
        if (StringUtils.hasText(framework)) {
            return framework;
        }
        if (svc.framework().size() == 1) {
            return preferred;
        }
        if (prompts.isInteractive()) {
            return prompts.requireChoice(null, "framework", "Framework",
                    svc.framework().stream().collect(Collectors.toMap(f -> f, f -> f)), preferred);
        }
        return preferred;
    }

    private static List<String> expandForFramework(List<String> base, FrameworkSupport fw) {
        List<String> out = new ArrayList<>(base);
        if (!"base".equals(fw.smokeModule())) {
            out.replaceAll(m -> m.equals("base") ? fw.smokeModule() : m);
        }
        return out;
    }

    private List<String> buildCapabilityWizard(String database, String capabilities, String cacheProvider, JloomPrompts prompts) {
        List<String> moduleIds = new ArrayList<>(List.of("base"));
        String databaseModule = resolveDatabase(database, prompts);
        if (databaseModule != null) {
            moduleIds.add(databaseModule);
        }

        List<String> capabilityIds = resolveCapabilityIds(capabilities, databaseModule, prompts);
        String cacheModule = capabilityIds.contains("caching") ? resolveCacheProvider(cacheProvider, prompts) : null;

        for (String capability : capabilityIds) {
            moduleIds.add(switch (capability) {
                case "validation" -> "validation";
                case "migrations" -> isMysqlFamily(databaseModule) ? "flyway-mysql" : "flyway";
                case "security" -> "jwt-auth";
                case "caching" -> cacheModule;
                case "aop" -> "aop";
                case "scheduling" -> "scheduling";
                case "async" -> "async";
                case "auditing" -> "auditing";
                case "observability" -> "otel-tracing";
                case "openapi" -> "openapi";
                case "testing" -> "testcontainers";
                default -> throw new IllegalArgumentException("Unknown capability '" + capability
                        + "' — expected one of: validation, migrations, security, caching, aop, "
                        + "scheduling, async, auditing, observability, openapi, testing");
            });
        }
        return moduleIds;
    }

    private String resolveDatabase(String database, JloomPrompts prompts) {
        if ("none".equalsIgnoreCase(database)) {
            return null;
        }
        if (StringUtils.hasText(database)) {
            return database;
        }
        Map<String, String> choices = new LinkedHashMap<>();
        choices.put("PostgreSQL", "postgres");
        choices.put("MySQL", "mysql");
        choices.put("MariaDB", "mariadb");
        choices.put("H2 (in-memory — dev/test only)", "h2");
        return prompts.chooseOptional(null, "database", "Database", choices, "None");
    }

    private List<String> resolveCapabilityIds(String capabilities, String databaseModule, JloomPrompts prompts) {
        if (StringUtils.hasText(capabilities)) {
            return java.util.Arrays.stream(capabilities.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        Map<String, String> choices = new LinkedHashMap<>();
        choices.put("Validation", "validation");
        if (databaseModule != null) {
            choices.put("Database migrations", "migrations");
        }
        choices.put("Security (JWT)", "security");
        choices.put("Caching", "caching");
        choices.put("AOP", "aop");
        choices.put("Scheduling", "scheduling");
        choices.put("Async processing", "async");
        if (databaseModule != null) {
            choices.put("Auditing", "auditing");
        }
        choices.put("Observability", "observability");
        choices.put("OpenAPI", "openapi");
        if (databaseModule != null) {
            choices.put("Testing infrastructure", "testing");
        }
        return prompts.chooseMultiple("capabilities", "Capabilities (space to toggle, enter to confirm)", choices);
    }

    private String resolveCacheProvider(String cacheProvider, JloomPrompts prompts) {
        if (StringUtils.hasText(cacheProvider)) {
            return "redis".equalsIgnoreCase(cacheProvider) ? "caching-redis" : "caching-caffeine";
        }
        if (!prompts.isInteractive()) {
            return "caching-caffeine";
        }
        Map<String, String> choices = new LinkedHashMap<>();
        choices.put("Caffeine (in-process, no external service)", "caching-caffeine");
        choices.put("Redis", "caching-redis");
        return prompts.requireChoice(null, "cache-provider", "Cache provider",
                choices, "Caffeine (in-process, no external service)");
    }

    private static boolean isMysqlFamily(String databaseModule) {
        return "mysql".equals(databaseModule) || "mariadb".equals(databaseModule);
    }

    private static String formatProblems(List<String> problems) {
        return String.join("\n  - ", problems);
    }

    static class DatabaseCandidates implements Iterable<String> {
        @Override
        public java.util.Iterator<String> iterator() {
            return java.util.List.of("postgres", "mysql", "mariadb", "h2", "none").iterator();
        }
    }

    static class CacheProviderCandidates implements Iterable<String> {
        @Override
        public java.util.Iterator<String> iterator() {
            return java.util.List.of("caffeine", "redis").iterator();
        }
    }
}