package com.jloom.state;

import java.util.ArrayList;
import java.util.List;

public record ProjectState(List<AppliedModule> modules, String basePackage, String projectName) {

    public ProjectState {
        modules = List.copyOf(modules);
    }

    public static ProjectState empty() {
        return new ProjectState(List.of(), null, null);
    }

    public boolean hasModule(String id) {
        return modules.stream().anyMatch(m -> m.id().equals(id));
    }

    public List<String> appliedIds() {
        return modules.stream().map(AppliedModule::id).toList();
    }

    public ProjectState withApplied(AppliedModule module) {
        List<AppliedModule> updated = new ArrayList<>(modules);
        updated.removeIf(m -> m.id().equals(module.id()));
        updated.add(module);
        return new ProjectState(List.copyOf(updated), basePackage, projectName);
    }

    public ProjectState withBasePackage(String basePackage) {
        return new ProjectState(modules, basePackage, projectName);
    }

    public ProjectState withProjectName(String projectName) {
        return new ProjectState(modules, basePackage, projectName);
    }
}