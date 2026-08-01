package com.jloom.cli;

import com.jloom.exec.GradleRewriteRunner;
import com.jloom.orchestrate.ModuleApplier;
import com.jloom.orchestrate.UpgradeEngine;
import com.jloom.registry.ArchetypeRegistry;
import com.jloom.registry.ModuleRegistry;
import com.jloom.registry.ServiceRegistry;
import com.jloom.scaffold.FileTreeCopier;
import com.jloom.state.AppliedModule;
import com.jloom.state.ProjectStateStore;
import com.jloom.compose.RecipeComposer;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JloomCommandsTest {

    @TempDir
    Path tempDir;

    private CommandLine buildCommandLine() {
        JloomContext ctx = new JloomContext(new org.springframework.context.support.StaticApplicationContext() {
            @Override public <T> T getBean(Class<T> requiredType) {
                throw new UnsupportedOperationException("not used in this test");
            }
        });
        // Wire the real Spring beans that NewCommands/UpgradeCommands need
        ModuleApplier applier = new ModuleApplier(
                ModuleRegistry.loadBundled(),
                new RecipeComposer(),
                new GradleRewriteRunner(),
                new ProjectStateStore(),
                new FileTreeCopier());
        UpgradeEngine upgradeEngine = new UpgradeEngine(ModuleRegistry.loadBundled());
        ModuleRegistry modules = ModuleRegistry.loadBundled();
        ServiceRegistry services = ServiceRegistry.loadBundled();
        ArchetypeRegistry archetypes = ArchetypeRegistry.loadBundled();
        ProjectStateStore stateStore = new ProjectStateStore();

        JloomCommand root = new JloomCommand(new TestContext(applier, upgradeEngine, modules, services, archetypes, stateStore));
        return new CommandLine(root)
                .setOut(new PrintWriter(new ByteArrayOutputStream(), true))
                .setErr(new PrintWriter(new ByteArrayOutputStream(), true))
                .setExecutionExceptionHandler(new JloomExceptionHandler())
                .setParameterExceptionHandler(new JloomExceptionHandler());
    }

    @Test
    void registersEveryDeclaredCommand() {
        CommandLine cmd = buildCommandLine();
        Set<String> names = cmd.getSubcommands().keySet();
        assertThat(names).contains("new", "add", "list", "info", "status", "upgrade", "config", "help");
    }

    @Test
    void listModulesRunsEndToEnd() {
        Result r = run("list", "--what", "modules");
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(r.out).contains("Available modules:");
    }

    @Test
    void configRunsEndToEnd() {
        Result r = run("config");
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(r.out).contains("jloom config:");
    }

    @Test
    void infoRejectsUnknownModuleWithNonZeroExit() {
        Result r = run("info", "--module", "does-not-exist");
        assertThat(r.exitCode).isNotZero();
        assertThat(r.err).contains("No such module");
    }

    @Test
    void newAppliesTheGivenBasePackage() {
        Path target = tempDir.resolve("kebab-case-project");
        Result r = run("new", "--name", target.toString(), "--base-package", "com.acme.demo");
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(new ProjectStateStore().load(target).basePackage()).isEqualTo("com.acme.demo");
    }

    @Test
    void newRejectsAMalformedBasePackageAndWritesNothing() {
        Path target = tempDir.resolve("bad-package-project");
        Result r = run("new", "--name", target.toString(), "--base-package", "1invalid!");
        assertThat(r.exitCode).isNotZero();
        assertThat(target).doesNotExist();
    }

    @Test
    void newWithoutNameFailsFast() {
        Result r = run("new", "--base-package", "com.acme.demo");
        assertThat(r.exitCode).isNotZero();
        assertThat(r.err).containsIgnoringCase("name");
    }

    @Test
    void newWithoutServiceDefaultsToBareBaseProject() {
        Path target = tempDir.resolve("bare-base-project");
        Result r = run("new", "--name", target.toString(), "--base-package", "com.acme.demo");
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(new ProjectStateStore().load(target).appliedIds()).containsExactly("base");
    }

    @Test
    void newWithDatabaseAndCapabilitiesResolvesEachCapability() {
        Path target = tempDir.resolve("wizard-project");
        Result r = run("new",
                "--name", target.toString(),
                "--base-package", "com.acme.demo",
                "--database", "postgres",
                "--capabilities", "validation,migrations,security,caching,openapi,auditing,observability,testing",
                "--cache-provider", "redis");
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(new ProjectStateStore().load(target).appliedIds())
                .containsExactlyInAnyOrder(
                        "base", "postgres", "validation", "flyway", "jwt-auth", "caching-redis",
                        "openapi", "auditing", "otel-tracing", "testcontainers");
    }

    @Test
    void newWithMysqlDatabaseAndMigrationsPicksMysqlFlyway() {
        Path target = tempDir.resolve("mysql-migrations-project");
        Result r = run("new", "--name", target.toString(), "--base-package", "com.acme.demo",
                "--database", "mysql", "--capabilities", "migrations");
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(new ProjectStateStore().load(target).appliedIds())
                .contains("base", "mysql", "flyway-mysql");
    }

    @Test
    void newWithNoDatabaseAndNoCapabilitiesStaysBareBaseProject() {
        Path target = tempDir.resolve("bare-wizard-project");
        Result r = run("new", "--name", target.toString(), "--base-package", "com.acme.demo",
                "--database", "none", "--capabilities", "");
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(new ProjectStateStore().load(target).appliedIds()).containsExactly("base");
    }

    @Test
    void newWithUnknownCapabilityFailsFastWithClearMessage() {
        Path target = tempDir.resolve("bad-capability-project");
        Result r = run("new", "--name", target.toString(), "--base-package", "com.acme.demo",
                "--database", "none", "--capabilities", "bogus-capability");
        assertThat(r.exitCode).isNotZero();
        assertThat(r.err).contains("bogus-capability");
        assertThat(target).doesNotExist();
    }

    @Test
    void newWithServiceAppliesCuratedModuleList() {
        Path target = tempDir.resolve("service-project");
        Result r = run("new", "--name", target.toString(),
                "--service", "notification-service", "--base-package", "com.acme.demo");
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(new ProjectStateStore().load(target).appliedIds())
                .contains("base", "postgres", "notification-service");
    }

    @Test
    void newRejectsFrameworkServiceDoesntSupport() {
        Path target = tempDir.resolve("bad-framework-project");
        Result r = run("new", "--name", target.toString(),
                "--service", "identity-service", "--framework", "micronaut",
                "--base-package", "com.acme.demo");
        assertThat(r.exitCode).isNotZero();
        assertThat(target).doesNotExist();
    }

    @Test
    void newDryRunOnBrandNewProjectPreviewsWithoutWriting() {
        Path target = tempDir.resolve("dry-run-project");
        Result r = run("new", "--name", target.toString(),
                "--service", "user-service", "--dry-run", "--base-package", "com.acme.demo");
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(r.out).contains("Dry run");
        assertThat(target).doesNotExist();
    }

    @Test
    void addAppliesMultipleModulesAndSetOverridesPrompt() throws Exception {
        Path target = tempDir.resolve("set-override-project");
        Result r1 = run("new", "--name", target.toString());
        assertThat(r1.exitCode).isEqualTo(0);
        Result r2 = run("add", "--project", target.toString(),
                "postgres", "flyway", "--set", "postgres.db_name=demo");
        assertThat(r2.exitCode).isEqualTo(0);

        AppliedModule postgres = new ProjectStateStore().load(target).modules().stream()
                .filter(m -> m.id().equals("postgres"))
                .findFirst().orElseThrow();
        assertThat(postgres.answers()).containsEntry("db_name", "demo");
    }

    @Test
    void newRejectsACollidingNameImmediately() throws Exception {
        Path target = tempDir.resolve("already-taken");
        java.nio.file.Files.createDirectories(target);
        java.nio.file.Files.writeString(target.resolve("existing-file.txt"), "content");
        Result r = run("new", "--name", target.toString());
        assertThat(r.exitCode).isNotZero();
        assertThat(r.err).contains("already exists");
    }

    @Test
    void upgradeReportsAlreadyUpToDateWhenNothingIsBehind() {
        Path target = tempDir.resolve("up-to-date-project");
        assertThat(run("new", "--name", target.toString()).exitCode).isEqualTo(0);
        assertThat(run("add", "--project", target.toString(), "postgres").exitCode).isEqualTo(0);

        Result r = run("upgrade", "--project", target.toString());
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(r.out).contains("Already up to date");
    }

    @Test
    void upgradeBridgesAnOldRecordedVersion() throws Exception {
        Path target = tempDir.resolve("upgrade-project");
        assertThat(run("new", "--name", target.toString()).exitCode).isEqualTo(0);
        assertThat(run("add", "--project", target.toString(), "postgres").exitCode).isEqualTo(0);

        ProjectStateStore store = new ProjectStateStore();
        AppliedModule postgres = store.load(target).modules().stream()
                .filter(m -> m.id().equals("postgres")).findFirst().orElseThrow();
        store.save(target, store.load(target).withApplied(new AppliedModule(
                "postgres", "1.0.0", postgres.appliedAt(), postgres.answers())));

        Result r = run("upgrade", "--project", target.toString());
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(r.out).contains("postgres: 1.0.0 -> 1.2.0");
    }

    @Test
    void helpCommandPrintsUsage() {
        Result r = run("help");
        assertThat(r.exitCode).isEqualTo(0);
        assertThat(r.out).contains("jloom");
    }

    @Test
    void unknownArgumentProducesFriendlyError() {
        Result r = run("totally-bogus-command");
        assertThat(r.exitCode).isNotZero();
        assertThat(r.err).contains("Unknown argument");
    }

    private Result run(String... args) {
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(out, true));
        System.setErr(new java.io.PrintStream(err, true));
        try {
            CommandLine cmd = new CommandLine(new JloomCommand(new TestContext(
                    new ModuleApplier(ModuleRegistry.loadBundled(), new RecipeComposer(),
                            new GradleRewriteRunner(), new ProjectStateStore(), new FileTreeCopier()),
                    new UpgradeEngine(ModuleRegistry.loadBundled()),
                    ModuleRegistry.loadBundled(),
                    ServiceRegistry.loadBundled(),
                    ArchetypeRegistry.loadBundled(),
                    new ProjectStateStore())))
                    .setExecutionExceptionHandler(new JloomExceptionHandler())
                    .setParameterExceptionHandler(new JloomExceptionHandler());
            int exit = cmd.execute(args);
            return new Result(exit, out.toString(), err.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record Result(int exitCode, String out, String err) {
    }

    static class TestContext extends JloomContext {
        TestContext(ModuleApplier applier, UpgradeEngine engine, ModuleRegistry modules,
                   ServiceRegistry services, ArchetypeRegistry archetypes, ProjectStateStore store) {
            super(new org.springframework.context.support.StaticApplicationContext() {
                @SuppressWarnings("unchecked")
                @Override public <T> T getBean(Class<T> requiredType) {
                    if (requiredType == ModuleApplier.class) return (T) applier;
                    if (requiredType == UpgradeEngine.class) return (T) engine;
                    if (requiredType == ModuleRegistry.class) return (T) modules;
                    if (requiredType == ServiceRegistry.class) return (T) services;
                    if (requiredType == ArchetypeRegistry.class) return (T) archetypes;
                    if (requiredType == ProjectStateStore.class) return (T) store;
                    if (requiredType == Terminal.class) return (T) newDumbTerminal();
                    if (requiredType == JloomContext.class) return (T) this; // recursive; ignored
                    throw new UnsupportedOperationException("not stubbed: " + requiredType.getName());
                }
            });
        }
    }

    private static Terminal newDumbTerminal() {
        try {
            return new DumbTerminal(new java.io.ByteArrayInputStream(new byte[0]), new java.io.ByteArrayOutputStream());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}