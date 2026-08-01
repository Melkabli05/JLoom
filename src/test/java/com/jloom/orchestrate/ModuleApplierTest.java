package com.jloom.orchestrate;

import com.jloom.exec.GradleRewriteRunner;
import com.jloom.registry.ModuleRegistry;
import com.jloom.state.ProjectStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleApplierTest {

    @TempDir
    Path tempDir;

    @Test
    void dryRunOnBrandNewProjectSeedsPackageAndName() {
        ModuleApplier applier = new ModuleApplier(
                ModuleRegistry.loadBundled(),
                new com.jloom.compose.RecipeComposer(),
                new GradleRewriteRunner(),
                new ProjectStateStore(),
                new com.jloom.scaffold.FileTreeCopier());

        Path target = tempDir.resolve("my-app");
        var result = applier.apply(target, List.of("base"),
                Map.of(), true, "com.acme.demo", "my-app");

        assertThat(result).isInstanceOf(ModuleApplier.ApplyResult.DryRun.class);
        assertThat(target.resolve(".jloom/state.json")).doesNotExist();
    }

    @Test
    void rejectionLeavesNoStateFileOnDisk() {
        ModuleApplier applier = new ModuleApplier(ModuleRegistry.loadBundled());

        Path target = tempDir.resolve("half-baked");
        var result = applier.apply(target, List.of("not-a-real-module"),
                Map.of(), false, "com.acme.demo", "half-baked");

        assertThat(result).isInstanceOf(ModuleApplier.ApplyResult.Rejected.class);
        assertThat(target.resolve(".jloom/state.json")).doesNotExist();
    }

    @Test
    void noArgConstructorStillWorks() {
        ModuleApplier applier = new ModuleApplier(ModuleRegistry.loadBundled());
        assertThat(applier).isNotNull();
    }
}