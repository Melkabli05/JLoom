package com.jloom.compose;

import com.jloom.registry.CatalogRoots;
import com.jloom.registry.ModuleManifest;
import com.jloom.util.Tokens;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeComposer {

    private static final String RECIPE_NAME = "com.jloom.generated.ApplyModules";

    public String compose(List<ModuleSelection> modules) {
        return composeFragments(modules.stream()
                .map(s -> new Fragment(s.manifest().id(), s.manifest().mergeRecipes(), s.answers()))
                .toList());
    }

    public String composeUpgrade(List<UpgradeStep> steps) {
        return composeFragments(steps.stream()
                .map(s -> new Fragment(s.moduleId(), List.of(s.recipeResourcePath()), s.answers()))
                .toList());
    }

    public String recipeName() {
        return RECIPE_NAME;
    }

    private String composeFragments(List<Fragment> fragments) {
        List<Map<String, Object>> recipeList = new ArrayList<>();

        for (Fragment fragment : fragments) {
            for (String resourcePath : fragment.resourcePaths()) {
                String text = readFragment(fragment.moduleId(), resourcePath);
                String substituted = Tokens.substitute(text, fragment.answers());
                Object parsed = YAML.load(substituted);
                if (!(parsed instanceof List<?> list)) {
                    throw new IllegalStateException("Fragment " + fragment.moduleId() + "/"
                            + resourcePath + " is not a YAML list (got " + (parsed == null ? "null"
                            : parsed.getClass().getSimpleName()) + ")");
                }
                for (Object entry : list) {
                    if (!(entry instanceof Map<?, ?> entryMap)) {
                        throw new IllegalStateException("Fragment " + fragment.moduleId() + "/"
                                + resourcePath + " contains a non-map entry: " + entry);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> recipe = (Map<String, Object>) entryMap;
                    recipeList.add(recipe);
                }
            }
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndentWithIndicator(true);
        options.setIndicatorIndent(2);
        options.setIndent(2);
        Representer representer = new Representer(options);
        representer.addClassTag(java.util.Map.class, Tag.MAP);
        representer.addClassTag(java.util.List.class, Tag.SEQ);
        representer.addClassTag(String.class, Tag.STR);
        Yaml emitter = new Yaml(representer, options);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("type", "specs.openrewrite.org/v1beta/recipe");
        document.put("name", RECIPE_NAME);
        document.put("displayName", "jloom generated composite recipe");
        document.put("recipeList", recipeList);
        return emitter.dump(document);
    }

    private static final Yaml YAML = new Yaml(new SafeConstructor(new LoaderOptions()));

    private String readFragment(String moduleId, String resourcePath) {
        try (InputStream in = CatalogRoots.open(moduleId, resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Module '" + moduleId + "' declares recipe '"
                        + resourcePath + "' but no such resource exists");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Fragment(String moduleId, List<String> resourcePaths, Map<String, String> answers) {
    }

    public record ModuleSelection(ModuleManifest manifest, Map<String, String> answers) {
    }

    public record UpgradeStep(String moduleId, String recipeResourcePath, Map<String, String> answers) {
    }
}
