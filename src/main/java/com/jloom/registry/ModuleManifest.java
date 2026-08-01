package com.jloom.registry;

import java.util.List;

public record ModuleManifest(
        String id,
        String version,
        List<String> requires,
        List<String> conflicts,
        String provides,
        List<Prompt> prompts,
        List<String> mergeRecipes,
        List<String> fileTemplates,
        List<Upgrade> upgrades,
        boolean scaffold
) {
    public record Prompt(String key, String type, String defaultValue) {
    }

    public record Upgrade(String from, String to, String recipe) {
    }
}
