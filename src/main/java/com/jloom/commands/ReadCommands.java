package com.jloom.commands;

import com.jloom.registry.ArchetypeManifest;
import com.jloom.registry.ArchetypeRegistry;
import com.jloom.registry.ModuleManifest;
import com.jloom.registry.ModuleRegistry;
import com.jloom.registry.ServiceManifest;
import com.jloom.registry.ServiceRegistry;
import jakarta.validation.constraints.NotBlank;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.core.command.annotation.Option;

import java.util.stream.Collectors;

@CommandGroup(name = "jloom")
final class ReadCommands {

    private final ModuleRegistry modules;
    private final ServiceRegistry services;
    private final ArchetypeRegistry archetypes;

    ReadCommands(ModuleRegistry modules, ServiceRegistry services, ArchetypeRegistry archetypes) {
        this.modules = modules;
        this.services = services;
        this.archetypes = archetypes;
    }

    @Command(name = "help", description = "Show available commands. Run with no args, this is the default action.", exitStatusExceptionMapper = "jloomExitStatusMapper")
    public String help() {
        return """
                jloom — generate and evolve production-ready backends

                Usage:
                  jloom <command> [options]

                Commands:
                  new       Create a new project (interactive or via flags)
                  add       Add a module to an existing project
                  list      List available modules, services, or archetypes
                  info      Show what a module does before applying it
                  status    Show what modules are applied and which can be upgraded
                  upgrade   Pull newer versions of one or more modules
                  config    Print the resolved jloom configuration
                  help      Show this message

                Examples:
                  jloom new --name my-app --service file-service
                  jloom add postgres flyway --set postgres.db_name=demo
                  jloom list
                  jloom info --module postgres
                  jloom upgrade

                Run 'jloom <command> --help' for command-specific options.""";
    }

    @Command(name = "list", description = "List available modules, services, or archetypes.", exitStatusExceptionMapper = "jloomExitStatusMapper",
            completionProvider = "jloomCompletionList")
    public String list(
            @Option(defaultValue = "modules", description = "'modules', 'services', or 'archetypes'") String what) {
        return switch (what.toLowerCase()) {
            case "archetypes" -> listArchetypes();
            case "services"   -> listServices();
            default           -> listModules();
        };
    }

    @Command(name = "info", description = "Show what a module changes before applying it.", exitStatusExceptionMapper = "jloomExitStatusMapper",
            completionProvider = "jloomCompletionModule")
    public String info(
            @NotBlank
            @Option(longName = "module", description = "Module id, e.g. 'postgres'", required = true) String moduleId) {
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
        return out.toString();
    }

    private String listModules() {
        return "Available modules:\n" + modules.all().stream()
                .map(this::formatModuleLine)
                .collect(Collectors.joining("\n"));
    }

    private String listServices() {
        return "Available services:\n" + services.all().stream()
                .map(this::formatServiceLine)
                .collect(Collectors.joining("\n"));
    }

    private String listArchetypes() {
        return "Available archetypes:\n" + archetypes.all().stream()
                .map(this::formatArchetypeLine)
                .collect(Collectors.joining("\n"));
    }

    private String formatModuleLine(ModuleManifest m) {
        String provides = m.provides() == null ? "" : "  provides=" + m.provides();
        String requires = m.requires().isEmpty() ? "" : "  requires=" + m.requires();
        return String.format("  %-20s %-10s%s%s", m.id(), m.version(), provides, requires);
    }

    private String formatServiceLine(ServiceManifest s) {
        return String.format("  %-25s %-25s frameworks=%s", s.id(), s.displayName(), s.framework());
    }

    private String formatArchetypeLine(ArchetypeManifest a) {
        return String.format("  %-25s modules=%s", a.id(), a.modules());
    }
}