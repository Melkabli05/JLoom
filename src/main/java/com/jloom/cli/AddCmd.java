package com.jloom.cli;

import com.jloom.orchestrate.ModuleApplier;
import com.jloom.orchestrate.ModuleApplier.ApplyResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Command(name = "add", mixinStandardHelpOptions = true, description = "Apply one or more modules to a project.")
public class AddCmd extends CliCommand implements Runnable {

    @ParentCommand
    JloomCommand parent;

    @Option(names = "--project", defaultValue = ".", description = "Target project directory.")
    String project;

    @Parameters(arity = "0..*", description = "Module ids to add.")
    List<String> moduleIds;

    @Option(names = "--set", description = "Override module prompts, e.g. --set postgres.db_name=demo,postgres.port=5433")
    Map<String, String> set;

    @Option(names = "--dry-run", defaultValue = "false", description = "Preview without writing.")
    boolean dryRun;

    @Override
    public void run() {
        JloomContext ctx = parent.context();
        List<String> ids = resolveModuleIds(ctx);
        ModuleApplier applier = ctx.applier();
        ApplyResult result = applier.apply(Path.of(project), ids, set == null ? Map.of() : set, dryRun, null, null);
        switch (result) {
            case ApplyResult.Applied ignored -> System.out.println(JloomOutput.success("Applied: " + ids));
            case ApplyResult.DryRun ignored -> System.out.println("Dry run — no changes written.");
            case ApplyResult.Rejected rejected -> throw new IllegalArgumentException(formatProblems(rejected.problems()));
            case ApplyResult.Failed f -> throw new IllegalStateException("OpenRewrite run failed:\n" + f.output());
        }
    }

    private List<String> resolveModuleIds(JloomContext ctx) {
        if (moduleIds != null && !moduleIds.isEmpty()) {
            return moduleIds;
        }
        String typed = ctx.prompts().requireNonBlankText(null, "modules",
                "Which modules? (comma or space separated ids, see 'jloom list')");
        return java.util.Arrays.stream(typed.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String formatProblems(List<String> problems) {
        return String.join("\n  - ", problems);
    }
}