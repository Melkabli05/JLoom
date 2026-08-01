package com.jloom.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectPaths {

    private ProjectPaths() { }

    public static boolean isEmpty(Path dir) {
        if (!Files.isDirectory(dir)) return true;
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        } catch (Exception _) {
            return false;
        }
    }

    public static void requireEmpty(Path target) {
        if (!isEmpty(target)) {
            throw new IllegalArgumentException(
                    "'" + target.toAbsolutePath() + "' already exists and isn't empty — "
                            + "pass a different --name or remove it first.");
        }
    }
}