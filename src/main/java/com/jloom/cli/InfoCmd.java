package com.jloom.cli;

import com.jloom.registry.ModuleManifest;
import com.jloom.registry.ModuleRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "info", mixinStandardHelpOptions = true, description = "Show what a module changes before applying it.")
public class InfoCmd extends CliCommand implements Runnable {

    @ParentCommand
    JloomCommand parent;

    @Option(names = "--module", description = "Module id, e.g. 'postgres'")
    String moduleId;

    @Override
    public void run() {
        JloomContext ctx = parent.context();
        ModuleRegistry modules = ctx.modules();
        String id = ctx.prompts().requireNonBlankText(moduleId, "module",
                "Which module? (see 'jloom list' for ids)");
        ModuleManifest mod = modules.find(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No such module: '" + id + "'. Run 'jloom list' to see available modules."));

        StringBuilder out = new StringBuilder();
        out.append(JloomOutput.accent(mod.id())).append(' ').append(mod.version()).append('\n');

        if (!mod.requires().isEmpty()) {
            out.append("  requires: ").append(mod.requires()).append('\n');
        }
        if (!mod.fileTemplates().isEmpty()) {
            out.append(JloomOutput.heading("  adds new files:")).append('\n');
            mod.fileTemplates().forEach(t -> out.append("    + ").append(t).append('\n'));
        }
        if (!mod.mergeRecipes().isEmpty()) {
            out.append(JloomOutput.heading("  edits existing files via OpenRewrite recipes:")).append('\n');
            mod.mergeRecipes().forEach(t -> out.append("    ~ ").append(t).append('\n'));
        }
        System.out.print(out);
    }
}