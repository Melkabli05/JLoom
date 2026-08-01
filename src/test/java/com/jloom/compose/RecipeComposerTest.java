package com.jloom.compose;

import com.jloom.registry.ModuleManifest;
import com.jloom.registry.ModuleRegistry;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeComposerTest {

    private final RecipeComposer composer = new RecipeComposer();
    private final ModuleRegistry registry = ModuleRegistry.loadBundled();

    @Test
    void composesBundledFragmentsIntoAValidRecipeList() {
        ModuleManifest postgres = registry.require("postgres");
        ModuleManifest flyway = registry.require("flyway");

        String yaml = composer.compose(List.of(
                new RecipeComposer.ModuleSelection(postgres, Map.of("db_name", "demo")),
                new RecipeComposer.ModuleSelection(flyway, Map.of())));

        Map<String, Object> doc = new Yaml().load(yaml);
        assertThat(doc).containsKeys("type", "name", "displayName", "recipeList");
        assertThat(doc.get("name")).isEqualTo("com.jloom.generated.ApplyModules");
        assertThat(doc.get("type")).isEqualTo("specs.openrewrite.org/v1beta/recipe");
        List<?> recipeList = (List<?>) doc.get("recipeList");
        assertThat(recipeList).hasSizeGreaterThan(0);
        for (Object entry : recipeList) {
            assertThat(entry).isInstanceOf(Map.class);
        }
    }

    @Test
    void substitutesPromptAnswersIntoFragmentText() {
        ModuleManifest postgres = registry.require("postgres");

        String yaml = composer.compose(List.of(
                new RecipeComposer.ModuleSelection(postgres, Map.of("db_name", "demo-db"))));

        assertThat(yaml).contains("demo-db");
        assertThat(yaml).doesNotContain("{{db_name}}");
    }

    @Test
    void emittedYamlContainsTheRecipeHeader() {
        ModuleManifest postgres = registry.require("postgres");
        String yaml = composer.compose(List.of(
                new RecipeComposer.ModuleSelection(postgres, Map.of())));

        assertThat(yaml).contains("type: specs.openrewrite.org/v1beta/recipe");
        assertThat(yaml).contains("name: com.jloom.generated.ApplyModules");
    }

    @Test
    void emittedYamlIsReParseableBySnakeYamlRoundTrip() {
        ModuleManifest postgres = registry.require("postgres");
        String yaml = composer.compose(List.of(
                new RecipeComposer.ModuleSelection(postgres, Map.of("db_name", "x"))));

        Map<String, Object> doc = new Yaml().load(yaml);
        List<?> recipeList = (List<?>) doc.get("recipeList");
        assertThat(recipeList).isNotEmpty();

        String again = new Yaml().dump(doc);
        Map<String, Object> redoced = new Yaml().load(again);
        assertThat((List<?>) redoced.get("recipeList")).hasSameSizeAs(recipeList);
    }

    @Test
    void rejectsFragmentWhoseResourceIsMissing() {
        ModuleManifest phantom = new ModuleManifest("phantom", "1.0.0", List.of(), List.of(),
                null, List.of(), List.of("does-not-exist.yml"), List.of(), List.of(), false);

        assertThatThrownBy(() -> composer.compose(List.of(
                new RecipeComposer.ModuleSelection(phantom, Map.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("phantom")
                .hasMessageContaining("does-not-exist.yml");
    }

    @Test
    void composeUpgradeChainsRealBundledUpgradeRecipesUnderOneRecipeName() {
        ModuleManifest postgres = registry.require("postgres");
        assertThat(postgres.upgrades()).hasSizeGreaterThanOrEqualTo(2);

        List<RecipeComposer.UpgradeStep> steps = postgres.upgrades().subList(0, 2).stream()
                .map(u -> new RecipeComposer.UpgradeStep("postgres", u.recipe(), Map.of()))
                .toList();

        String yaml = composer.composeUpgrade(steps);
        Map<String, Object> doc = new Yaml().load(yaml);
        assertThat((List<?>) doc.get("recipeList")).hasSize(3);
        assertThat(doc.get("name")).isEqualTo("com.jloom.generated.ApplyModules");
    }

    @Test
    void recipeNameMatchesDocumentedConstant() {
        assertThat(composer.recipeName()).isEqualTo("com.jloom.generated.ApplyModules");
    }

    @Test
    void composeFileServiceGradleMergePreservesVersions() throws IOException {
        String fragment;
        try (InputStream in = getClass().getResourceAsStream("/services/file-service/merges/gradle.yml")) {
            fragment = new String(in.readAllBytes());
        }
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        Object parsed = yaml.load(fragment);
        String composed = composer.compose(List.of(new RecipeComposer.ModuleSelection(
                new ModuleManifest("file-service", "0.1.0", List.of(), List.of(), null,
                        List.of(new ModuleManifest.Prompt("internal_service_key", "secret", "x")),
                        List.of("merges/gradle.yml"), List.of(), List.of(), false),
                Map.of("internal_service_key", "x"))));
        assertThat(composed).contains("version: 8.6.0");
    }
}
