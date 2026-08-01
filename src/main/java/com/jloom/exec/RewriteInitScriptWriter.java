package com.jloom.exec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RewriteInitScriptWriter {

    private static final String REWRITE_PLUGIN_VERSION = "7.37.0";
    private static final String REWRITE_RECIPE_BOM_VERSION = "3.35.0";
    private static final String REWRITE_KUBERNETES_VERSION = "3.17.3";

    public Path write(Path scratchDir, Path recipeConfigFile, String recipeName) {
        String script = """
                initscript {
                    repositories { maven { url = uri("https://plugins.gradle.org/m2") } }
                    dependencies { classpath("org.openrewrite.rewrite:org.openrewrite.rewrite.gradle.plugin:%s") }
                }

                rootProject {
                    plugins.apply(org.openrewrite.gradle.RewritePlugin::class.java)

                    repositories { mavenCentral() }

                    dependencies {
                        add("rewrite", platform("org.openrewrite.recipe:rewrite-recipe-bom:%s"))
                        add("rewrite", "org.openrewrite:rewrite-gradle")
                        add("rewrite", "org.openrewrite:rewrite-yaml")
                        add("rewrite", "org.openrewrite:rewrite-docker")
                        add("rewrite", "org.openrewrite.recipe:rewrite-kubernetes:%s")
                    }

                    extensions.configure<org.openrewrite.gradle.RewriteExtension> {
                        configFile = file("%s")
                        activeRecipe("%s")
                    }

                    afterEvaluate {
                        if (repositories.isEmpty()) { repositories { mavenCentral() } }
                    }
                }
                """.formatted(
                REWRITE_PLUGIN_VERSION,
                REWRITE_RECIPE_BOM_VERSION,
                REWRITE_KUBERNETES_VERSION,
                recipeConfigFile.toAbsolutePath().toString().replace("\\", "\\\\"),
                recipeName
        );

        Path initScript = scratchDir.resolve("jloom-rewrite-init.gradle.kts");
        try {
            Files.createDirectories(scratchDir);
            Files.writeString(initScript, script, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return initScript;
    }
}
