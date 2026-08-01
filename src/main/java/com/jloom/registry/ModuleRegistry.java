package com.jloom.registry;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModuleRegistry {

    private final Map<String, ModuleManifest> modulesById;

    private ModuleRegistry(Map<String, ModuleManifest> modulesById) {
        this.modulesById = modulesById;
    }

    public static ModuleRegistry loadBundled() {
        ManifestLoader loader = new ManifestLoader();
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, ModuleManifest> byId = new LinkedHashMap<>();

        for (String indexResource : CatalogRoots.indexResources()) {
            try (InputStream indexStream = ModuleRegistry.class.getResourceAsStream(indexResource)) {
                if (indexStream == null) {
                    throw new IllegalStateException("Missing bundled catalog index: " + indexResource);
                }
                Map<String, Object> index = yaml.load(indexStream);
                @SuppressWarnings("unchecked")
                List<String> moduleIds = (List<String>) index.getOrDefault("modules", List.of());

                for (String id : moduleIds) {
                    try (InputStream manifestStream = CatalogRoots.open(id, "module.yml")) {
                        if (manifestStream == null) {
                            throw new IllegalStateException(
                                    "Listed in " + indexResource + " but missing manifest for: " + id);
                        }
                        ModuleManifest manifest = loader.load(manifestStream);
                        if (!manifest.id().equals(id)) {
                            throw new IllegalStateException(indexResource + " lists id '" + id
                                    + "' but manifest declares id '" + manifest.id() + "'");
                        }
                        if (byId.containsKey(id)) {
                            throw new IllegalStateException(
                                    "Module id '" + id + "' is declared in more than one catalog index");
                        }
                        byId.put(id, manifest);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return new ModuleRegistry(Map.copyOf(byId));
    }

    public List<ModuleManifest> all() {
        return List.copyOf(modulesById.values());
    }

    public Optional<ModuleManifest> find(String id) {
        return Optional.ofNullable(modulesById.get(id));
    }

    public ModuleManifest require(String id) {
        return find(id).orElseThrow(() ->
                new IllegalArgumentException("No such module: '" + id + "'. Run 'jloom list' to see available modules."));
    }

    public List<ModuleManifest.Upgrade> findUpgradePath(String moduleId, String fromVersion) {
        ModuleManifest manifest = require(moduleId);
        Map<String, ModuleManifest.Upgrade> byFromVersion = new LinkedHashMap<>();
        for (ModuleManifest.Upgrade upgrade : manifest.upgrades()) {
            byFromVersion.put(upgrade.from(), upgrade);
        }

        List<ModuleManifest.Upgrade> path = new ArrayList<>();
        String current = fromVersion;
        while (!current.equals(manifest.version())) {
            ModuleManifest.Upgrade step = byFromVersion.get(current);
            if (step == null) {
                return List.of();
            }
            path.add(step);
            current = step.to();
        }
        return List.copyOf(path);
    }

    public List<String> validate(List<String> alreadyApplied, List<String> toApply) {
        List<String> problems = new ArrayList<>();

        for (int i = 0; i < toApply.size(); i++) {
            String id = toApply.get(i);
            ModuleManifest manifest = modulesById.get(id);
            if (manifest == null) {
                problems.add("Unknown module: " + id);
                continue;
            }

            List<String> satisfiedSoFar = new ArrayList<>(alreadyApplied);
            satisfiedSoFar.addAll(toApply.subList(0, i));

            for (String required : manifest.requires()) {
                if (!isSatisfied(required, satisfiedSoFar)) {
                    boolean satisfiedLaterInBatch = isSatisfied(required, toApply.subList(i + 1, toApply.size()));
                    if (satisfiedLaterInBatch) {
                        problems.add(id + " requires '" + required + "' which is in this request but listed "
                                + "AFTER " + id + " — list it earlier, e.g.: jloom add <provider>," + id);
                    } else {
                        problems.add(id + " requires '" + required + "' which is not applied and not being applied now");
                    }
                }
            }

            List<String> effective = new ArrayList<>(alreadyApplied);
            effective.addAll(toApply);
            for (String conflict : manifest.conflicts()) {
                if (effective.contains(conflict)) {
                    problems.add(id + " conflicts with '" + conflict + "', which is already applied or in this request");
                }
            }
        }
        return List.copyOf(problems);
    }

    private boolean isSatisfied(String requirement, List<String> effectiveModuleIds) {
        if (requirement.startsWith("capability:")) {
            return effectiveModuleIds.stream()
                    .map(modulesById::get)
                    .filter(Objects::nonNull)
                    .anyMatch(m -> requirement.equals(m.provides()));
        }
        return effectiveModuleIds.contains(requirement);
    }
}