package com.jloom.registry;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record ServiceManifest(
        String id,
        String displayName,
        String description,
        List<String> framework,
        Map<String, List<String>> modulesPerFramework
) {

    public ServiceManifest {
        framework = framework == null ? List.of() : List.copyOf(framework);
        modulesPerFramework = modulesPerFramework == null
                ? Map.of()
                : Collections.unmodifiableMap(modulesPerFramework);
    }

    public List<String> modulesFor(String frameworkId) {
        List<String> specific = modulesPerFramework.get(frameworkId);
        if (specific != null) return specific;
        List<String> fallback = modulesPerFramework.get("__default__");
        return fallback != null ? fallback : List.of();
    }
}