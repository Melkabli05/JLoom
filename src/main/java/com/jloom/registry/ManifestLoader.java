package com.jloom.registry;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ManifestLoader {

    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    public ModuleManifest load(InputStream in) {
        Map<String, Object> raw = yaml.load(in);
        if (raw == null) {
            throw new ManifestParseException("module.yml is empty");
        }

        String id = requireString(raw, "id");
        String version = requireString(raw, "version");
        List<String> requires = stringList(raw.get("requires"));
        List<String> conflicts = stringList(raw.get("conflicts"));
        String provides = (String) raw.get("provides");

        List<ModuleManifest.Prompt> prompts = new ArrayList<>();
        Object promptsRaw = raw.get("prompts");
        if (promptsRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String key = String.valueOf(m.get("key"));
                    Object typeRaw = m.get("type");
                    String type = typeRaw == null ? "string" : String.valueOf(typeRaw);
                    if (!type.equals("string") && !type.equals("secret")) {
                        throw new ManifestParseException(
                                "module.yml prompt '" + key + "' has unsupported type '" + type
                                        + "' (supported: string, secret)");
                    }
                    Object def = m.get("default");
                    prompts.add(new ModuleManifest.Prompt(key, type, def == null ? null : String.valueOf(def)));
                }
            }
        }

        List<String> mergeRecipes = stringList(raw.get("mergeRecipes"));
        List<String> fileTemplates = stringList(raw.get("fileTemplates"));

        List<ModuleManifest.Upgrade> upgrades = new ArrayList<>();
        Object upgradesRaw = raw.get("upgrades");
        if (upgradesRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String from = requireString(m, "from");
                    String to = requireString(m, "to");
                    String recipe = requireString(m, "recipe");
                    upgrades.add(new ModuleManifest.Upgrade(from, to, recipe));
                }
            }
        }

        boolean scaffold = Boolean.TRUE.equals(raw.get("scaffold"));

        return new ModuleManifest(id, version, requires, conflicts, provides, prompts, mergeRecipes, fileTemplates, upgrades, scaffold);
    }

    private static String requireString(Map<?, ?> raw, String key) {
        Object v = raw.get(key);
        if (v == null) {
            throw new ManifestParseException("module.yml missing required field: " + key);
        }
        return String.valueOf(v);
    }

    private static List<String> stringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        throw new ManifestParseException("expected a YAML list, got: " + raw.getClass());
    }

    public static final class ManifestParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ManifestParseException(String message) {
            super(message);
        }
    }
}