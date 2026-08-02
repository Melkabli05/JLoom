package com.jloom.cli;

import com.jloom.orchestrate.UpgradeEngine;
import com.jloom.orchestrate.UpgradeEngine.UpgradeResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;

@Command(name = "upgrade", mixinStandardHelpOptions = true, description = "Upgrade applied modules to the catalog's current versions.")
public class UpgradeCmd extends CliCommand implements Runnable {

    @ParentCommand
    JloomCommand parent;

    @Option(names = "--project", defaultValue = ".", description = "Project directory.")
    String project;

    @Option(names = "--module", description = "Upgrade only this module.")
    String module;

    @Option(names = "--dry-run", defaultValue = "false", description = "Preview without writing.")
    boolean dryRun;

    @Override
    public void run() {
        UpgradeEngine upgradeEngine = parent.context().upgradeEngine();
        UpgradeResult result = upgradeEngine.upgrade(Path.of(project), module, dryRun);
        switch (result) {
            case UpgradeResult.UpToDate ignored -> System.out.println(JloomOutput.hint("Already up to date."));
            case UpgradeResult.Upgraded upgraded -> {
                System.out.println(JloomOutput.success("Upgraded:"));
                for (String change : upgraded.changes()) {
                    System.out.println("  " + change);
                }
            }
            case UpgradeResult.DryRun dryRunResult -> {
                System.out.println(JloomOutput.heading("Dry run — would upgrade:"));
                for (String change : dryRunResult.changes()) {
                    System.out.println("  " + change);
                }
            }
            case UpgradeResult.Blocked blocked -> throw new IllegalStateException(
                    "Refusing to upgrade — no module was changed:\n  - " + String.join("\n  - ", blocked.reasons()));
            case UpgradeResult.Failed failed -> throw new IllegalStateException(
                    "OpenRewrite upgrade run failed:\n" + failed.output());
        }
    }
}