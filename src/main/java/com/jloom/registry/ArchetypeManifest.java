package com.jloom.registry;

import java.util.List;
import java.util.Map;

public record ArchetypeManifest(String id, List<String> modules, Map<String, String> answers) {
}
