package com.jloom.cli;

import com.jloom.orchestrate.ModuleApplier;
import com.jloom.orchestrate.ModuleApplier.ApplyResult;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
@Command(name = "add", mixinStandardHelpOptions = true, description = "Apply one or more modules to a project.")
public class AddCmd extends CliCommand implements Runnable {

    @ParentCommand
    JloomCommand parent;

    @Option(names = "--project", defaultValue = ".", description = "Target project directory.")
    String project;

    @NotEmpty
    @Parameters(arity = "1..*", description = "Module ids to add.")
    List<String> moduleIds;

    @Option(names = "--set", description = "Override module prompts, e.g. --set postgres.db_name=demo,postgres.port=5433")
    Map<String, String> set;

    @Option(names = "--dry-run", defaultValue = "false", description = "Preview without writing.")
    boolean dryRun;

    @Override
    public void run() {
        ModuleApplier applier = parent.context().applier();
        ApplyResult result = applier.apply(Path.of(project), moduleIds, set == null ? Map.of() : set, dryRun, null, null);
        switch (result) {
            case ApplyResult.Applied ignored -> System.out.println(JloomOutput.success("Applied: " + moduleIds));
            case ApplyResult.DryRun ignored -> System.out.println("Dry run — no changes written.");
            case ApplyResult.Rejected rejected -> throw new IllegalArgumentException(formatProblems(rejected.problems()));
            case ApplyResult.Failed f -> throw new IllegalStateException("OpenRewrite run failed:\n" + f.output());
        }
    }

    private static String formatProblems(List<String> problems) {
        return String.join("\n  - ", problems);
    }
}