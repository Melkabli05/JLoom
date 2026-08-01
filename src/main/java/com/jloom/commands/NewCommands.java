package com.jloom.commands;

import com.jloom.framework.FrameworkSupport;
import com.jloom.io.JloomOutput;
import com.jloom.orchestrate.ModuleApplier;
import com.jloom.orchestrate.ModuleApplier.ApplyResult;
import com.jloom.registry.ArchetypeRegistry;
import com.jloom.registry.ServiceManifest;
import com.jloom.registry.ServiceRegistry;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.shell.core.command.annotation.Arguments;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CommandGroup(name = "jloom")
final class NewCommands {

    private final ArchetypeRegistry archetypes;
    private final ServiceRegistry services;
    private final ModuleApplier applier;
    private final InteractivePrompts prompts;
    private final JloomOutput output;

    NewCommands(ArchetypeRegistry archetypes, ServiceRegistry services,
                ModuleApplier applier, InteractivePrompts prompts, JloomOutput output) {
        this.archetypes = archetypes;
        this.services = services;
        this.applier = applier;
        this.prompts = prompts;
        this.output = output;
    }

    private static final String DEFAULT_PROJECT_NAME = "my-app";
    private static final String DEFAULT_BASE_PACKAGE = "com.example.app";

    @Command(name = "new", description = "Create a new project — generates immediately; pass --dry-run to preview instead.",
            exitStatusExceptionMapper = "jloomExitStatusMapper", completionProvider = "jloomCompletionNew")
    public String newProject(
            @Option(longName = "name", description = "Project name / target directory") String name,
            @Option(description = "Service-type id, e.g. 'notification-service' (omit for a bare base project)") String service,
            @Option(description = "Framework (spring-boot | micronaut) — only asked if the service supports more than one") String framework,
            @Pattern(regexp = "[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*",
                    message = "must be a valid dotted Java package name, e.g. com.acme.myapp")
            @Option(longName = "base-package", description = "Base Java package") String basePackage,
            @Option(longName = "archetype", description = "Apply an archetype's modules on top") String archetype,
            @Option(longName = "database", description = "Database for a bare project: postgres | mysql | mariadb | h2 | none (only asked when not picking a --service)") String database,
            @Option(longName = "capabilities", description = "Comma-separated capabilities for a bare project, e.g. validation,security,caching (only asked when not picking a --service)") String capabilities,
            @Option(longName = "cache-provider", description = "caffeine | redis — only relevant if 'caching' is among the chosen capabilities") String cacheProvider,
            @Option(longName = "dry-run", defaultValue = "false", description = "Preview without writing") boolean dryRun) {
        if (!StringUtils.hasText(name) && prompts.isInteractive()) {
            System.out.println("Let's set up your project — press Enter on any question to accept the default.\n");
        }
        Path target = resolveTarget(name);

        String serviceId = prompts.chooseOptional(service, "service", "What would you like to create?",
                services.all().stream().collect(Collectors.toMap(s -> s.id() + " — " + s.displayName(), ServiceManifest::id)),
                "Just a base project");
        List<String> moduleIds = serviceId == null
                ? buildCapabilityWizard(database, capabilities, cacheProvider)
                : modulesForService(serviceId, framework);
        Map<String, String> archetypeAnswers = Map.of();
        if (archetype != null) {
            var manifest = archetypes.find(archetype)
                    .orElseThrow(() -> new IllegalArgumentException("No such archetype: " + archetype));
            moduleIds.addAll(manifest.modules());
            archetypeAnswers = manifest.answers();
        }
        String resolvedBasePackage = prompts.promptWithDefault(basePackage, "base-package", "Base package", DEFAULT_BASE_PACKAGE);

        System.out.println((dryRun ? "Previewing " : "Setting up ") + target + "...");
        var result = applier.apply(target, moduleIds, archetypeAnswers, dryRun,
                resolvedBasePackage, target.getFileName().toString());
        return switch (result) {
            case ApplyResult.Applied ignored -> """
                    %s

                    %s
                      cd %s
                      ./gradlew test
                    """.formatted(output.success("Created " + target), output.heading("Next steps:"), target);
            case ApplyResult.DryRun ignored -> "Dry run — would create " + target + " with modules " + moduleIds;
            case ApplyResult.Rejected rejected -> throw new IllegalArgumentException(formatProblems(rejected.problems()));
            case ApplyResult.Failed f -> throw new IllegalStateException(f.output());
        };
    }

    @Command(name = "add", description = "Apply one or more modules to a project.", exitStatusExceptionMapper = "jloomExitStatusMapper",
            completionProvider = "jloomCompletionModule")
    public String addModule(
            @Option(description = "Target project directory", defaultValue = ".") String project,
            @NotEmpty
            @Arguments List<String> moduleIds,
            @Option(description = "Override a module's prompts, e.g. --set postgres.db_name=demo,postgres.port=5433") Map<String, String> set,
            @Option(longName = "dry-run", defaultValue = "false", description = "Preview without writing") boolean dryRun) {
        var result = applier.apply(Path.of(project), moduleIds, set == null ? Map.of() : set, dryRun, null, null);
        return switch (result) {
            case ApplyResult.Applied ignored -> output.success("Applied: " + moduleIds);
            case ApplyResult.DryRun ignored -> "Dry run — no changes written.";
            case ApplyResult.Rejected rejected -> throw new IllegalArgumentException(formatProblems(rejected.problems()));
            case ApplyResult.Failed f -> throw new IllegalStateException("OpenRewrite run failed:\n" + f.output());
        };
    }

    private Path resolveTarget(String name) {
        String candidateName = name;
        while (true) {
            String resolvedName = prompts.requireText(candidateName, "name", "Project name", suggestProjectName());
            Path candidate = Path.of(resolvedName);
            if (ProjectPaths.isEmpty(candidate)) {
                return candidate;
            }
            if (!prompts.isInteractive()) {
                ProjectPaths.requireEmpty(candidate);
            }
            System.out.println(output.error("'" + candidate.toAbsolutePath() + "' already exists and isn't empty."));
            candidateName = null;
        }
    }

    private static String suggestProjectName() {
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

    private List<String> modulesForService(String serviceId, String framework) {
        ServiceManifest svc = services.require(serviceId);
        String chosenFramework = resolveFramework(svc, framework);
        if (!svc.framework().contains(chosenFramework)) {
            throw new IllegalArgumentException(
                    "Service '" + serviceId + "' does not support framework '" + chosenFramework
                            + "'. Supported: " + svc.framework());
        }
        FrameworkSupport fw = FrameworkSupport.byId(chosenFramework);
        return expandForFramework(svc.modulesFor(chosenFramework), fw);
    }

    private String resolveFramework(ServiceManifest svc, String framework) {
        String preferredDefault = svc.framework().contains("spring-boot") ? "spring-boot" : svc.framework().get(0);
        if (StringUtils.hasText(framework)) {
            return framework;
        }
        if (svc.framework().size() == 1) {
            return preferredDefault;
        }
        if (prompts.isInteractive()) {
            return prompts.requireChoice(null, "framework", "Framework",
                    svc.framework().stream().collect(Collectors.toMap(f -> f, f -> f)), preferredDefault);
        }
        return preferredDefault;
    }

    private static List<String> expandForFramework(List<String> base, FrameworkSupport fw) {
        List<String> out = new ArrayList<>(base);
        if (!"base".equals(fw.smokeModule())) {
            out.replaceAll(m -> m.equals("base") ? fw.smokeModule() : m);
        }
        return out;
    }

    private List<String> buildCapabilityWizard(String database, String capabilities, String cacheProvider) {
        List<String> moduleIds = new ArrayList<>(List.of("base"));

        String databaseModule = resolveDatabase(database);
        if (databaseModule != null) {
            moduleIds.add(databaseModule);
        }

        List<String> capabilityIds = resolveCapabilityIds(capabilities, databaseModule);
        String cacheModule = capabilityIds.contains("caching") ? resolveCacheProvider(cacheProvider) : null;

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

    private String resolveDatabase(String database) {
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

    private List<String> resolveCapabilityIds(String capabilities, String databaseModule) {
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
        return prompts.chooseMultiple(null, "capabilities", "Capabilities (space to toggle, enter to confirm)", choices);
    }

    private String resolveCacheProvider(String cacheProvider) {
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
}
