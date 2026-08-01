package com.jloom.orchestrate;

import com.jloom.compose.RecipeComposer;
import com.jloom.exec.GradleRewriteRunner;
import com.jloom.registry.ModuleManifest;
import com.jloom.registry.ModuleRegistry;
import com.jloom.state.AppliedModule;
import com.jloom.state.ProjectState;
import com.jloom.state.ProjectStateStore;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class UpgradeEngine {

    private final ModuleRegistry registry;
    private final RecipeComposer recipeComposer;
    private final GradleRewriteRunner runner;
    private final ProjectStateStore stateStore;

    public UpgradeEngine(ModuleRegistry registry) {
        this(registry, new RecipeComposer(), new GradleRewriteRunner(), new ProjectStateStore());
    }

    public UpgradeEngine(ModuleRegistry registry, RecipeComposer recipeComposer,
                         GradleRewriteRunner runner, ProjectStateStore stateStore) {
        this.registry = registry;
        this.recipeComposer = recipeComposer;
        this.runner = runner;
        this.stateStore = stateStore;
    }

    public UpgradeResult upgrade(Path targetProject, String onlyModuleId, boolean dryRun) {
        ProjectState state = stateStore.load(targetProject);
        List<AppliedModule> candidates = onlyModuleId == null
                ? state.modules()
                : state.modules().stream().filter(m -> m.id().equals(onlyModuleId)).toList();
        if (onlyModuleId != null && candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Module '" + onlyModuleId + "' is not applied to this project. Run 'jloom status' to see what is.");
        }

        List<PlannedUpgrade> planned = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        for (AppliedModule applied : candidates) {
            var current = registry.find(applied.id());
            if (current.isEmpty() || current.get().version().equals(applied.version())) {
                continue;
            }
            List<ModuleManifest.Upgrade> path = registry.findUpgradePath(applied.id(), applied.version());
            if (path.isEmpty()) {
                blocked.add(applied.id() + " is at " + applied.version() + ", catalog has "
                        + current.get().version() + ", but no upgrade recipe bridges them");
                continue;
            }
            planned.add(new PlannedUpgrade(applied, current.get().version(), path));
        }

        if (!blocked.isEmpty()) {
            return UpgradeResult.blocked(blocked);
        }
        if (planned.isEmpty()) {
            return UpgradeResult.upToDate();
        }

        List<RecipeComposer.UpgradeStep> steps = new ArrayList<>();
        for (PlannedUpgrade p : planned) {
            for (ModuleManifest.Upgrade step : p.path()) {
                steps.add(new RecipeComposer.UpgradeStep(p.applied().id(), step.recipe(), p.applied().answers()));
            }
        }

        String recipeYaml = recipeComposer.composeUpgrade(steps);
        GradleRewriteRunner.Result result = runner.run(targetProject, recipeYaml, recipeComposer.recipeName(), dryRun);
        if (!result.success()) {
            return UpgradeResult.failed(result.output());
        }

        List<String> changes = planned.stream()
                .map(p -> p.applied().id() + ": " + p.applied().version() + " -> " + p.newVersion())
                .toList();
        if (dryRun) {
            return UpgradeResult.dryRun(changes);
        }

        ProjectState updated = state;
        for (PlannedUpgrade p : planned) {
            updated = updated.withApplied(new AppliedModule(
                    p.applied().id(), p.newVersion(), Instant.now(), p.applied().answers()));
        }
        stateStore.save(targetProject, updated);
        return UpgradeResult.upgraded(changes);
    }

    private record PlannedUpgrade(AppliedModule applied, String newVersion, List<ModuleManifest.Upgrade> path) {
    }

    public sealed interface UpgradeResult {
        record UpToDate() implements UpgradeResult {
        }
        record Upgraded(List<String> changes) implements UpgradeResult {
        }
        record DryRun(List<String> changes) implements UpgradeResult {
        }
        record Blocked(List<String> reasons) implements UpgradeResult {
        }
        record Failed(String output) implements UpgradeResult {
        }

        static UpgradeResult upToDate() { return new UpToDate(); }
        static UpgradeResult upgraded(List<String> changes) { return new Upgraded(changes); }
        static UpgradeResult dryRun(List<String> changes) { return new DryRun(changes); }
        static UpgradeResult blocked(List<String> reasons) { return new Blocked(reasons); }
        static UpgradeResult failed(String output) { return new Failed(output); }
    }
}
