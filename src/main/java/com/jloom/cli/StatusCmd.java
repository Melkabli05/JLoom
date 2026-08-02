package com.jloom.cli;

import com.jloom.registry.ModuleRegistry;
import com.jloom.state.AppliedModule;
import com.jloom.state.ProjectState;
import com.jloom.state.ProjectStateStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;

@Command(name = "status", mixinStandardHelpOptions = true, description = "Show applied modules and whether newer versions exist.")
public class StatusCmd extends CliCommand implements Runnable {

    @ParentCommand
    JloomCommand parent;

    @Option(names = "--project", defaultValue = ".", description = "Project directory.")
    String project;

    @Override
    public void run() {
        JloomContext ctx = parent.context();
        Path projectPath = Path.of(project);
        ProjectState state = ctx.stateStore().load(projectPath);
        if (state.modules().isEmpty()) {
            System.out.println("No applied modules in " + projectPath.toAbsolutePath() + ".");
            return;
        }
        ModuleRegistry modules = ctx.modules();
        StringBuilder out = new StringBuilder("Applied modules in ")
                .append(projectPath.toAbsolutePath()).append(":\n");
        for (AppliedModule applied : state.modules()) {
            var latest = modules.find(applied.id());
            String note = (latest.isPresent() && !latest.get().version().equals(applied.version()))
                    ? "catalog has " + latest.get().version() + " — run 'jloom upgrade' to pick it up"
                    : "up to date";
            out.append(String.format("  %-25s %-10s %s%n", applied.id(), applied.version(), note));
        }
        System.out.print(out);
    }
}