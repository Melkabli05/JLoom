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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Command(name = "list", mixinStandardHelpOptions = true, description = "List available modules, services, or archetypes.")
public class ListCmd extends CliCommand implements Runnable {

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
        List<ModuleManifest> sorted = modules.all().stream()
                .sorted(Comparator.comparing(ModuleManifest::id))
                .toList();
        int idWidth = widthOf(sorted, ModuleManifest::id);
        int versionWidth = widthOf(sorted, ModuleManifest::version);
        String body = sorted.stream()
                .map(m -> formatModuleLine(m, idWidth, versionWidth))
                .collect(Collectors.joining("\n"));
        System.out.println(JloomOutput.heading("Available modules:") + "\n" + body);
    }

    private void listServices(ServiceRegistry services) {
        List<ServiceManifest> sorted = services.all().stream()
                .sorted(Comparator.comparing(ServiceManifest::id))
                .toList();
        int idWidth = widthOf(sorted, ServiceManifest::id);
        int nameWidth = widthOf(sorted, ServiceManifest::displayName);
        String body = sorted.stream()
                .map(s -> formatServiceLine(s, idWidth, nameWidth))
                .collect(Collectors.joining("\n"));
        System.out.println(JloomOutput.heading("Available services:") + "\n" + body);
    }

    private void listArchetypes(ArchetypeRegistry archetypes) {
        List<ArchetypeManifest> sorted = archetypes.all().stream()
                .sorted(Comparator.comparing(ArchetypeManifest::id))
                .toList();
        int idWidth = widthOf(sorted, ArchetypeManifest::id);
        String body = sorted.stream()
                .map(a -> formatArchetypeLine(a, idWidth))
                .collect(Collectors.joining("\n"));
        System.out.println(JloomOutput.heading("Available archetypes:") + "\n" + body);
    }

    private static <T> int widthOf(List<T> items, java.util.function.Function<T, String> field) {
        return items.stream().mapToInt(item -> field.apply(item).length()).max().orElse(0);
    }

    private static String formatModuleLine(ModuleManifest m, int idWidth, int versionWidth) {
        String provides = m.provides() == null ? "" : "  provides=" + m.provides();
        String requires = m.requires().isEmpty() ? "" : "  requires=" + m.requires();
        // Pad the plain id to width first, then colorize — colorizing before padding would
        // count the invisible ANSI escape bytes toward the column width and break alignment.
        String paddedId = JloomOutput.accent(String.format("%-" + idWidth + "s", m.id()));
        return "  " + paddedId + "  " + String.format("%-" + versionWidth + "s", m.version()) + provides + requires;
    }

    private static String formatServiceLine(ServiceManifest s, int idWidth, int nameWidth) {
        String paddedId = JloomOutput.accent(String.format("%-" + idWidth + "s", s.id()));
        return "  " + paddedId + "  " + String.format("%-" + nameWidth + "s", s.displayName()) + "  frameworks=" + s.framework();
    }

    private static String formatArchetypeLine(ArchetypeManifest a, int idWidth) {
        String paddedId = JloomOutput.accent(String.format("%-" + idWidth + "s", a.id()));
        return "  " + paddedId + "  modules=" + a.modules();
    }

    static class WhatCandidates implements Iterable<String> {
        @Override
        public java.util.Iterator<String> iterator() {
            return java.util.List.of("modules", "services", "archetypes").iterator();
        }
    }
}