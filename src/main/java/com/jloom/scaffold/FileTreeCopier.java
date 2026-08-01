package com.jloom.scaffold;

import com.jloom.registry.CatalogRoots;
import com.jloom.registry.ModuleManifest;
import com.jloom.util.Tokens;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class FileTreeCopier {

    public void copy(ModuleManifest manifest, Path targetRoot, Map<String, String> tokens) {
        for (String relativePath : manifest.fileTemplates()) {
            String destinationRelativePath = Tokens.substitute(relativePath, tokens);
            Path destination = targetRoot.resolve(destinationRelativePath);

            try (InputStream in = CatalogRoots.open(manifest.id(), "files/" + relativePath)) {
                if (in == null) {
                    throw new IllegalStateException(
                            "Module '" + manifest.id() + "' declares fileTemplate '" + relativePath
                                    + "' but no such resource exists under files/");
                }
                Files.createDirectories(destination.getParent());
                if (isBinary(relativePath)) {
                    Files.write(destination, in.readAllBytes());
                } else {
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    Files.writeString(destination, Tokens.substitute(content, tokens));
                }
                if (isExecutableScript(relativePath)) {
                    destination.toFile().setExecutable(true, false);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static boolean isBinary(String path) {
        return path.endsWith(".jar") || path.endsWith(".png") || path.endsWith(".ico");
    }

    private static boolean isExecutableScript(String path) {
        if (path.endsWith(".bat") || path.endsWith(".cmd")) {
            return false;
        }
        String fileName = Path.of(path).getFileName().toString();
        return fileName.equals("gradlew")
                || fileName.endsWith(".sh")
                || fileName.endsWith(".command");
    }
}