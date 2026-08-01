package com.jloom.registry;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ArchetypeRegistry {

    private static final String INDEX_RESOURCE = "/archetypes/archetypes.yml";

    private final Map<String, ArchetypeManifest> byId;

    private ArchetypeRegistry(Map<String, ArchetypeManifest> byId) {
        this.byId = byId;
    }

    @SuppressWarnings("unchecked")
    public static ArchetypeRegistry loadBundled() {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, ArchetypeManifest> result = new LinkedHashMap<>();

        try (InputStream indexStream = ArchetypeRegistry.class.getResourceAsStream(INDEX_RESOURCE)) {
            if (indexStream == null) {
                return new ArchetypeRegistry(Map.of());
            }
            Map<String, Object> index = yaml.load(indexStream);
            List<String> ids = (List<String>) index.getOrDefault("archetypes", List.of());

            for (String id : ids) {
                String path = "/archetypes/" + id + ".yml";
                try (InputStream in = ArchetypeRegistry.class.getResourceAsStream(path)) {
                    if (in == null) {
                        throw new IllegalStateException("Listed in archetypes.yml but missing: " + path);
                    }
                    Map<String, Object> raw = yaml.load(in);
                    List<String> modules = (List<String>) raw.getOrDefault("modules", List.of());
                    Map<String, String> answers = new LinkedHashMap<>();
                    Object answersRaw = raw.get("answers");
                    if (answersRaw instanceof Map<?, ?> m) {
                        m.forEach((k, v) -> answers.put(String.valueOf(k), String.valueOf(v)));
                    }
                    result.put(id, new ArchetypeManifest(id, modules, answers));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new ArchetypeRegistry(Map.copyOf(result));
    }

    public List<ArchetypeManifest> all() {
        return List.copyOf(byId.values());
    }

    public Optional<ArchetypeManifest> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}