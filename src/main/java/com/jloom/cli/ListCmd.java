package com.jloom.cli;

import com.jloom.registry.ArchetypeRegistry;
import com.jloom.registry.ArchetypeManifest;
import com.jloom.registry.ModuleManifest;
import com.jloom.registry.ModuleRegistry;
import com.jloom.registry.ServiceManifest;
import com.jloom.registry.ServiceRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.stream.Collectors;

@Command(name = "list", description = "List available modules, services, or archetypes.")
public class ListCmd implements Runnable {

    @Option(names = "--what",
            defaultValue = "modules",
            description = "What to list: ${COMPLETION-CANDIDATES}.",
            completionCandidates = ListCmd.WhatCandidates.class)
    String what;

    @ParentCommand
    JloomCommand parent;

    @Override
    public void run() {
        JloomContext ctx = parent.context();
        switch (what.toLowerCase()) {
            case "archetypes" -> listArchetypes(ctx.archetypes());
            case "services"   -> listServices(ctx.services());
            default           -> listModules(ctx.modules());
        }
    }

    private void listModules(ModuleRegistry modules) {
        String body = modules.all().stream()
                .map(ListCmd::formatModuleLine)
                .collect(Collectors.joining("\n"));
        System.out.println("Available modules:\n" + body);
    }

    private void listServices(ServiceRegistry services) {
        String body = services.all().stream()
                .map(ListCmd::formatServiceLine)
                .collect(Collectors.joining("\n"));
        System.out.println("Available services:\n" + body);
    }

    private void listArchetypes(ArchetypeRegistry archetypes) {
        String body = archetypes.all().stream()
                .map(ListCmd::formatArchetypeLine)
                .collect(Collectors.joining("\n"));
        System.out.println("Available archetypes:\n" + body);
    }

    private static String formatModuleLine(ModuleManifest m) {
        String provides = m.provides() == null ? "" : "  provides=" + m.provides();
        String requires = m.requires().isEmpty() ? "" : "  requires=" + m.requires();
        return String.format("  %-20s %-10s%s%s", m.id(), m.version(), provides, requires);
    }

    private static String formatServiceLine(ServiceManifest s) {
        return String.format("  %-25s %-25s frameworks=%s", s.id(), s.displayName(), s.framework());
    }

    private static String formatArchetypeLine(ArchetypeManifest a) {
        return String.format("  %-25s modules=%s", a.id(), a.modules());
    }

    static class WhatCandidates implements Iterable<String> {
        @Override
        public java.util.Iterator<String> iterator() {
            return java.util.List.of("modules", "services", "archetypes").iterator();
        }
    }
}