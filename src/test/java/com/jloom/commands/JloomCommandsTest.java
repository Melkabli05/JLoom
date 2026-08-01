package com.jloom.commands;

import com.jloom.Main;
import com.jloom.state.AppliedModule;
import com.jloom.state.ProjectStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.shell.core.InputReader;
import org.springframework.shell.core.command.Command;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandExecutor;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.ExitStatus;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Main.class)
class JloomCommandsTest {

    @Autowired
    private CommandRegistry commandRegistry;

    @Autowired
    private CommandParser commandParser;

    @Autowired
    private ProjectStateStore stateStore;

    @TempDir
    private Path tempDir;

    @Test
    void registersEveryDeclaredCommand() {
        Set<String> names = commandRegistry.getCommands().stream().map(Command::getName).collect(java.util.stream.Collectors.toSet());
        assertThat(names).contains("new", "add", "list", "info", "status", "upgrade", "config");
    }

    @Test
    void listModulesRunsEndToEnd() throws Exception {
        Result result = run("list --what modules");
        assertThat(result.status()).isEqualTo(ExitStatus.OK);
        assertThat(result.output()).contains("Available modules:");
    }

    @Test
    void configRunsEndToEndAndReportsResolvedTheme() throws Exception {
        Result result = run("config");
        assertThat(result.status()).isEqualTo(ExitStatus.OK);
        assertThat(result.output()).contains("jloom config:");
    }

    @Test
    void infoRejectsUnknownModuleWithNonZeroExit() throws Exception {
        Result result = run("info --module does-not-exist");
        assertThat(result.status().code()).isNotZero();
        assertThat(result.status().description()).contains("No such module");
    }

    @Test
    void newAppliesTheGivenBasePackageOptionByItsDocumentedKebabCaseName() throws Exception {
        Path target = tempDir.resolve("kebab-case-project");
        Result result = run("new --name " + target + " --base-package com.acme.demo");
        assertThat(result.status()).isEqualTo(ExitStatus.OK);
        assertThat(stateStore.load(target).basePackage()).isEqualTo("com.acme.demo");
    }

    @Test
    void newRejectsAMalformedBasePackageAndWritesNothing() throws Exception {
        Path target = tempDir.resolve("bad-package-project");
        Result result = run("new --name " + target + " --base-package 1invalid!");
        assertThat(result.status()).isNotEqualTo(ExitStatus.OK);
        assertThat(target).doesNotExist();
    }

    @Test
    void newWithoutNameFailsFastInsteadOfHangingWhenNoInteractiveTerminalIsAttached() throws Exception {
        Result result = run("new --base-package com.acme.demo");
        assertThat(result.status()).isNotEqualTo(ExitStatus.OK);
        assertThat(result.status().description()).containsIgnoringCase("name");
    }

    @Test
    void newWithoutServiceDefaultsToABareBaseProjectNonInteractively() throws Exception {
        Path target = tempDir.resolve("bare-base-project");
        Result result = run("new --name " + target + " --base-package com.acme.demo");
        assertThat(result.status()).isEqualTo(ExitStatus.OK);
        assertThat(stateStore.load(target).appliedIds()).containsExactly("base");
    }

    @Test
    void newWithDatabaseAndCapabilitiesResolvesEachCapabilityToItsModuleNonInteractively() throws Exception {
        Path target = tempDir.resolve("wizard-project");
        Result result = run("new --name " + target + " --base-package com.acme.demo"
                + " --database postgres --capabilities validation,migrations,security,caching,openapi,auditing,observability,testing"
                + " --cache-provider redis");
        assertThat(result.status()).isEqualTo(ExitStatus.OK);
        assertThat(stateStore.load(target).appliedIds()).containsExactlyInAnyOrder(
                "base", "postgres", "validation", "flyway", "jwt-auth", "caching-redis",
                "openapi", "auditing", "otel-tracing", "testcontainers");
    }

    @Test
    void newWithMysqlDatabaseAndMigrationsPicksTheMysqlFlywayVariantNonInteractively() throws Exception {
        Path target = tempDir.resolve("mysql-migrations-project");
        Result result = run("new --name " + target + " --base-package com.acme.demo"
                + " --database mysql --capabilities migrations");
        assertThat(result.status()).isEqualTo(ExitStatus.OK);
        assertThat(stateStore.load(target).appliedIds()).contains("base", "mysql", "flyway-mysql");
    }

    @Test
    void newWithNoDatabaseAndNoCapabilitiesStaysABareBaseProjectNonInteractively() throws Exception {
        Path target = tempDir.resolve("bare-wizard-project");
        Result result = run("new --name " + target + " --base-package com.acme.demo --database none --capabilities \"\"");
        assertThat(result.status()).isEqualTo(ExitStatus.OK);
        assertThat(stateStore.load(target).appliedIds()).containsExactly("base");
    }

    @Test
    void newWithUnknownCapabilityFailsFastWithAClearMessage() throws Exception {
        Path target = tempDir.resolve("bad-capability-project");
        Result result = run("new --name " + target + " --base-package com.acme.demo --database none --capabilities bogus-capability");
        assertThat(result.status()).isNotEqualTo(ExitStatus.OK);
        assertThat(result.status().description()).contains("bogus-capability");
        assertThat(target).doesNotExist();
    }

    @Test
    void newWithServiceAppliesTheCuratedModuleListAndDefaultsFrameworkNonInteractively() throws Exception {
        Path target = tempDir.resolve("service-project");
        Result result = run("new --name " + target + " --service notification-service --base-package com.acme.demo");
        assertThat(result.status()).isEqualTo(ExitStatus.OK);
        assertThat(stateStore.load(target).appliedIds()).contains("base", "postgres", "notification-service");
    }

    @Test
    void newRejectsAFrameworkTheChosenServiceDoesNotSupport() throws Exception {
        Path target = tempDir.resolve("bad-framework-project");
        Result result = run("new --name " + target + " --service identity-service --framework micronaut");
        assertThat(result.status()).isNotEqualTo(ExitStatus.OK);
        assertThat(target).doesNotExist();
    }

    @Test
    void newDryRunOnABrandNewProjectPreviewsWithoutWritingAnything() throws Exception {
        Path target = tempDir.resolve("dry-run-project");
        Result result = run("new --name " + target + " --service user-service --dry-run");
        assertThat(result.status()).isEqualTo(ExitStatus.OK);
        assertThat(result.output()).contains("Dry run");
        assertThat(target).doesNotExist();
    }

    @Test
    void addAppliesMultipleModulesAndSetOverridesAPromptEndToEnd() throws Exception {
        Path target = tempDir.resolve("set-override-project");
        Result created = run("new --name " + target);
        assertThat(created.status()).isEqualTo(ExitStatus.OK);

        Result added = run("add --project " + target + " postgres flyway --set postgres.db_name=demo");
        assertThat(added.status()).isEqualTo(ExitStatus.OK);

        var appliedIds = stateStore.load(target).appliedIds();
        assertThat(appliedIds).contains("postgres", "flyway");

        var postgres = stateStore.load(target).modules().stream()
                .filter(m -> m.id().equals("postgres"))
                .findFirst()
                .orElseThrow();
        assertThat(postgres.answers()).containsEntry("db_name", "demo");
    }

    @Test
    void newRejectsACollidingNameImmediatelyWithoutHangingNonInteractively() throws Exception {
        Path target = tempDir.resolve("already-taken");
        java.nio.file.Files.createDirectories(target);
        java.nio.file.Files.writeString(target.resolve("existing-file.txt"), "content");

        Result result = run("new --name " + target);
        assertThat(result.status()).isNotEqualTo(ExitStatus.OK);
        assertThat(result.status().description()).contains("already exists");
    }

    @Test
    void upgradeBridgesAModuleFromAnOldRecordedVersionAndRewritesTheRealFile() throws Exception {
        Path target = tempDir.resolve("upgrade-project");
        assertThat(run("new --name " + target).status()).isEqualTo(ExitStatus.OK);
        assertThat(run("add --project " + target + " postgres").status()).isEqualTo(ExitStatus.OK);

        var state = stateStore.load(target);
        var postgres = state.modules().stream().filter(m -> m.id().equals("postgres")).findFirst().orElseThrow();
        stateStore.save(target, state.withApplied(new AppliedModule(
                "postgres", "1.0.0", postgres.appliedAt(), postgres.answers())));
        assertThat(stateStore.load(target).modules().stream()
                .filter(m -> m.id().equals("postgres")).findFirst().orElseThrow().version())
                .isEqualTo("1.0.0");

        Result upgraded = run("upgrade --project " + target);
        assertThat(upgraded.status()).isEqualTo(ExitStatus.OK);
        assertThat(upgraded.output()).contains("postgres: 1.0.0 -> 1.2.0");

        var afterUpgrade = stateStore.load(target).modules().stream()
                .filter(m -> m.id().equals("postgres")).findFirst().orElseThrow();
        assertThat(afterUpgrade.version()).isEqualTo("1.2.0");

        String applicationYml = java.nio.file.Files.readString(
                target.resolve("src/main/resources/application.yml"));
        assertThat(applicationYml).contains("maximum-pool-size");
        assertThat(applicationYml).contains("open-in-view");

        String buildGradle = java.nio.file.Files.readString(target.resolve("build.gradle.kts"));
        assertThat(buildGradle).contains("spring-boot-starter-data-jpa");
    }

    @Test
    void upgradeReportsAlreadyUpToDateWhenNothingIsBehind() throws Exception {
        Path target = tempDir.resolve("up-to-date-project");
        assertThat(run("new --name " + target).status()).isEqualTo(ExitStatus.OK);
        assertThat(run("add --project " + target + " postgres").status()).isEqualTo(ExitStatus.OK);

        Result result = run("upgrade --project " + target);
        assertThat(result.status()).isEqualTo(ExitStatus.OK);
        assertThat(result.output()).contains("Already up to date");
    }

    private Result run(String input) throws Exception {
        var parsed = commandParser.parse(input);
        StringWriter buffer = new StringWriter();
        var context = new CommandContext(parsed, commandRegistry, new PrintWriter(buffer), new InputReader() { });
        ExitStatus status = new CommandExecutor(commandRegistry).execute(context);
        return new Result(status, buffer.toString());
    }

    private record Result(ExitStatus status, String output) {
    }
}
