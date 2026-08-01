package com.jloom.registry;

import java.io.InputStream;
import java.util.List;

public final class CatalogRoots {

    private static final List<String> ALL = List.of("/modules/", "/services/");

    private CatalogRoots() {
    }

    public static InputStream open(String moduleId, String relativePath) {
        for (String root : ALL) {
            InputStream in = CatalogRoots.class.getResourceAsStream(root + moduleId + "/" + relativePath);
            if (in != null) {
                return in;
            }
        }
        return null;
    }

    static List<String> indexResources() {
        return ALL.stream().map(root -> root + "modules.yml").toList();
    }
}
