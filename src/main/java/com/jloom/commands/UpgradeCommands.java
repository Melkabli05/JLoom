package com.jloom.commands;

import com.jloom.io.JloomOutput;
import com.jloom.orchestrate.UpgradeEngine;
import com.jloom.orchestrate.UpgradeEngine.UpgradeResult;
import com.jloom.registry.ModuleRegistry;
import com.jloom.state.AppliedModule;
import com.jloom.state.ProjectState;
import com.jloom.state.ProjectStateStore;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.core.command.annotation.Option;

import java.nio.file.Path;

@CommandGroup(name = "jloom")
final class UpgradeCommands {

    private final ModuleRegistry modules;
    private final ProjectStateStore stateStore;
    private final UpgradeEngine upgradeEngine;
    private final JloomOutput output;

    UpgradeCommands(ModuleRegistry modules, ProjectStateStore stateStore, UpgradeEngine upgradeEngine, JloomOutput output) {
        this.modules = modules;
        this.stateStore = stateStore;
        this.upgradeEngine = upgradeEngine;
        this.output = output;
    }

    @Command(name = "status", description = "Show applied modules and whether newer versions exist.", exitStatusExceptionMapper = "jloomExitStatusMapper")
    public String status(@Option(defaultValue = ".") String project) {
        Path projectPath = Path.of(project);
        ProjectState state = stateStore.load(projectPath);
        if (state.modules().isEmpty()) {
            return "No applied modules in " + projectPath.toAbsolutePath() + ".";
        }
        StringBuilder out = new StringBuilder("Applied modules in ")
                .append(projectPath.toAbsolutePath()).append(":\n");
        for (AppliedModule applied : state.modules()) {
            var latest = modules.find(applied.id());
            String note = (latest.isPresent() && !latest.get().version().equals(applied.version()))
                    ? "catalog has " + latest.get().version() + " — run 'jloom upgrade' to pick it up"
                    : "up to date";
            out.append(String.format("  %-25s %-10s %s%n", applied.id(), applied.version(), note));
        }
        return out.toString();
    }

    @Command(name = "upgrade",
            description = "Upgrade applied modules to the catalog's current versions.",
            exitStatusExceptionMapper = "jloomExitStatusMapper",
            completionProvider = "jloomCompletionModule")
    public String upgrade(
            @Option(defaultValue = ".") String project,
            @Option(longName = "module", description = "Upgrade only this module") String module,
            @Option(longName = "dry-run", defaultValue = "false", description = "Preview without writing") boolean dryRun) {
        UpgradeResult result = upgradeEngine.upgrade(Path.of(project), module, dryRun);
        return switch (result) {
            case UpgradeResult.UpToDate ignored -> "Already up to date.";
            case UpgradeResult.Upgraded upgraded -> output.success("Upgraded:\n"
                    + upgraded.changes().stream().map(c -> "  " + c).reduce((a, b) -> a + "\n" + b).orElse(""));
            case UpgradeResult.DryRun dryRunResult -> "Dry run — would upgrade:\n"
                    + dryRunResult.changes().stream().map(c -> "  " + c).reduce((a, b) -> a + "\n" + b).orElse("");
            case UpgradeResult.Blocked blocked -> throw new IllegalStateException(
                    "Refusing to upgrade — no module was changed:\n  - "
                            + String.join("\n  - ", blocked.reasons()));
            case UpgradeResult.Failed failed -> throw new IllegalStateException(
                    "OpenRewrite upgrade run failed:\n" + failed.output());
        };
    }
}
