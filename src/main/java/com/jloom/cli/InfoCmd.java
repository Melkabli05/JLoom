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

    @Option(names = "--module", description = "Module id, e.g. 'postgres'", required = true)
    String moduleId;

    @Override
    public void run() {
        ModuleRegistry modules = parent.context().modules();
        ModuleManifest mod = modules.find(moduleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No such module: '" + moduleId + "'. Run 'jloom list' to see available modules."));

        StringBuilder out = new StringBuilder();
        out.append(mod.id()).append(' ').append(mod.version()).append('\n');

        if (!mod.requires().isEmpty()) {
            out.append("  requires: ").append(mod.requires()).append('\n');
        }
        if (!mod.fileTemplates().isEmpty()) {
            out.append("  adds new files:\n");
            mod.fileTemplates().forEach(t -> out.append("    + ").append(t).append('\n'));
        }
        if (!mod.mergeRecipes().isEmpty()) {
            out.append("  edits existing files via OpenRewrite recipes:\n");
            mod.mergeRecipes().forEach(t -> out.append("    ~ ").append(t).append('\n'));
        }
        System.out.print(out);
    }
}