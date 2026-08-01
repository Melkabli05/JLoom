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

public final class ServiceRegistry {

    private static final String INDEX_RESOURCE = "/services.yml";

    private final Map<String, ServiceManifest> servicesById;

    private ServiceRegistry(Map<String, ServiceManifest> servicesById) {
        this.servicesById = servicesById;
    }

    @SuppressWarnings("unchecked")
    public static ServiceRegistry loadBundled() {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, ServiceManifest> result = new LinkedHashMap<>();

        try (InputStream in = ServiceRegistry.class.getResourceAsStream(INDEX_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled service catalog: " + INDEX_RESOURCE);
            }
            Map<String, Object> root = yaml.load(in);
            Object servicesRaw = root == null ? null : root.get("services");
            if (!(servicesRaw instanceof List<?> list)) {
                return new ServiceRegistry(Map.of());
            }
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String id = String.valueOf(m.get("id"));
                String displayName = String.valueOf(m.get("displayName"));
                Object descRaw = m.get("description");
                String description = descRaw == null ? "" : String.valueOf(descRaw);
                List<String> framework = stringList(m.get("framework"));
                Object modulesRaw = m.get("modules");
                Map<String, List<String>> modulesPerFramework = parseModules(modulesRaw);
                result.put(id, new ServiceManifest(id, displayName, description, framework, modulesPerFramework));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ServiceRegistry(Map.copyOf(result));
    }

    private static Map<String, List<String>> parseModules(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> modules = list.stream().map(String::valueOf).toList();
            return Map.of("__default__", modules);
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() instanceof List<?> list) {
                    result.put(String.valueOf(e.getKey()), list.stream().map(String::valueOf).toList());
                }
            }
            return result;
        }
        return Map.of();
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    public List<ServiceManifest> all() {
        return List.copyOf(servicesById.values());
    }

    public Optional<ServiceManifest> find(String id) {
        return Optional.ofNullable(servicesById.get(id));
    }

    public ServiceManifest require(String id) {
        return find(id).orElseThrow(() ->
                new IllegalArgumentException("No such service: '" + id + "'. Run 'jloom list services' to see available service types."));
    }
}