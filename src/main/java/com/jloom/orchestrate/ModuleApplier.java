package com.jloom.orchestrate;

import com.jloom.compose.RecipeComposer;
import com.jloom.exec.GradleRewriteRunner;
import com.jloom.registry.ModuleManifest;
import com.jloom.registry.ModuleRegistry;
import com.jloom.scaffold.FileTreeCopier;
import com.jloom.state.AppliedModule;
import com.jloom.state.ProjectState;
import com.jloom.state.ProjectStateStore;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleApplier {

    private final ModuleRegistry registry;
    private final RecipeComposer recipeComposer;
    private final GradleRewriteRunner runner;
    private final ProjectStateStore stateStore;
    private final FileTreeCopier fileCopier;

    public ModuleApplier(ModuleRegistry registry) {
        this(registry, new RecipeComposer(), new GradleRewriteRunner(),
                new ProjectStateStore(), new FileTreeCopier());
    }

    public ModuleApplier(ModuleRegistry registry, RecipeComposer recipeComposer,
                         GradleRewriteRunner runner, ProjectStateStore stateStore,
                         FileTreeCopier fileCopier) {
        this.registry = registry;
        this.recipeComposer = recipeComposer;
        this.runner = runner;
        this.stateStore = stateStore;
        this.fileCopier = fileCopier;
    }

    public ApplyResult apply(Path targetProject, List<String> moduleIds, Map<String, String> overrides,
                              boolean dryRun, String basePackage, String projectName) {
        ProjectState state = stateStore.load(targetProject);

        List<String> problems = registry.validate(state.appliedIds(), moduleIds);
        if (!problems.isEmpty()) {
            return ApplyResult.rejected(problems);
        }

        ProjectState tokenState = state.appliedIds().isEmpty() && (basePackage != null || projectName != null)
                ? state.withProjectName(projectName != null ? projectName : state.projectName())
                        .withBasePackage(basePackage != null ? basePackage : state.basePackage())
                : state;
        Map<String, String> projectTokens = projectLevelTokens(tokenState);

        List<RecipeComposer.ModuleSelection> selections = new ArrayList<>();
        Map<String, Map<String, String>> answersByModule = new LinkedHashMap<>();

        for (String id : moduleIds) {
            ModuleManifest manifest = registry.require(id);
            Map<String, String> answers = resolveDefaultsOnly(manifest, overrides);
            answersByModule.put(id, answers);

            if (!manifest.mergeRecipes().isEmpty()) {
                selections.add(new RecipeComposer.ModuleSelection(manifest, answers));
            }
        }

        if (!dryRun) {
            for (String id : moduleIds) {
                ModuleManifest manifest = registry.require(id);
                if (manifest.scaffold()) {
                    Map<String, String> fileTokens = new LinkedHashMap<>(projectTokens);
                    fileTokens.putAll(answersByModule.get(id));
                    fileCopier.copy(manifest, targetProject, fileTokens);
                }
            }
        }

        String recipeOutput = "no merges required";
        if (!selections.isEmpty()) {
            if (dryRun && !runner.hasGradleWrapper(targetProject)) {
                return finish(targetProject, state, moduleIds, answersByModule, dryRun,
                        "recipe preview skipped — no Gradle wrapper on disk yet to diff against",
                        basePackage, projectName);
            }

            String recipeYaml = recipeComposer.compose(selections);
            GradleRewriteRunner.Result result = runner.run(targetProject, recipeYaml, recipeComposer.recipeName(), dryRun);

            if (!result.success()) {
                return ApplyResult.failed(result.output());
            }
            recipeOutput = result.streamed() ? "" : result.output();
        }

        if (!dryRun) {
            for (String id : moduleIds) {
                ModuleManifest manifest = registry.require(id);
                if (!manifest.scaffold() && !manifest.fileTemplates().isEmpty()) {
                    Map<String, String> fileTokens = new LinkedHashMap<>(projectTokens);
                    fileTokens.putAll(answersByModule.get(id));
                    fileCopier.copy(manifest, targetProject, fileTokens);
                }
            }
        }

        return finish(targetProject, state, moduleIds, answersByModule, dryRun, recipeOutput, basePackage, projectName);
    }

    private static Map<String, String> resolveDefaultsOnly(ModuleManifest module, Map<String, String> overrides) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (ModuleManifest.Prompt prompt : module.prompts()) {
            String overrideKey = module.id() + "." + prompt.key();
            if (overrides != null && overrides.containsKey(overrideKey)) {
                answers.put(prompt.key(), overrides.get(overrideKey));
                continue;
            }
            if (prompt.defaultValue() != null) {
                answers.put(prompt.key(), prompt.defaultValue());
                continue;
            }
            throw new IllegalArgumentException(
                    "Module '" + module.id() + "' requires --set " + overrideKey + "=<value> (no default)");
        }
        return answers;
    }

    private Map<String, String> projectLevelTokens(ProjectState state) {
        if (state.basePackage() == null && state.projectName() == null) {
            return Map.of();
        }
        Map<String, String> tokens = new LinkedHashMap<>();
        if (state.projectName() != null) {
            tokens.put("project_name", state.projectName());
        }
        if (state.basePackage() != null) {
            tokens.put("package", state.basePackage());
            tokens.put("package_path", state.basePackage().replace('.', '/'));
        }
        return tokens;
    }

    private ApplyResult finish(Path targetProject, ProjectState state, List<String> moduleIds,
                                Map<String, Map<String, String>> answersByModule, boolean dryRun, String output,
                                String basePackage, String projectName) {
        if (dryRun) {
            return ApplyResult.dryRun(output);
        }
        ProjectState seeded = state;
        if (state.appliedIds().isEmpty()) {
            String name = projectName != null ? projectName : targetProject.getFileName().toString();
            seeded = seeded.withProjectName(name);
            if (basePackage != null) {
                seeded = seeded.withBasePackage(basePackage);
            }
        }
        ProjectState updated = seeded;
        for (String id : moduleIds) {
            ModuleManifest manifest = registry.require(id);
            updated = updated.withApplied(new AppliedModule(
                    id, manifest.version(), Instant.now(), answersByModule.getOrDefault(id, Map.of())));
        }
        stateStore.save(targetProject, updated);
        return ApplyResult.applied(output);
    }

    public sealed interface ApplyResult {
        record Applied(String output) implements ApplyResult {
        }
        record DryRun(String diff) implements ApplyResult {
        }
        record Rejected(List<String> problems) implements ApplyResult {
        }
        record Failed(String output) implements ApplyResult {
        }

        static ApplyResult applied(String output) { return new Applied(output); }
        static ApplyResult dryRun(String diff) { return new DryRun(diff); }
        static ApplyResult rejected(List<String> problems) { return new Rejected(problems); }
        static ApplyResult failed(String output) { return new Failed(output); }
    }
}