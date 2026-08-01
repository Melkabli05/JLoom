package com.jloom.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleRegistryTest {

    private final ModuleRegistry registry = ModuleRegistry.loadBundled();

    @Test
    void upgradePathChainsMultipleStepsFromOldVersionToCurrent() {
        ModuleManifest postgres = registry.require("postgres");
        assertThat(postgres.upgrades()).isNotEmpty();
        String from = postgres.upgrades().get(0).from();
        String expectedFinal = postgres.upgrades().get(postgres.upgrades().size() - 1).to();

        List<ModuleManifest.Upgrade> path = registry.findUpgradePath("postgres", from);

        assertThat(path).hasSize(postgres.upgrades().size());
        assertThat(path.get(path.size() - 1).to()).isEqualTo(expectedFinal);
        for (int i = 1; i < path.size(); i++) {
            assertThat(path.get(i).from()).isEqualTo(path.get(i - 1).to());
        }
    }

    @Test
    void upgradePathIsEmptyWhenAlreadyAtCurrentVersion() {
        ModuleManifest postgres = registry.require("postgres");
        assertThat(registry.findUpgradePath("postgres", postgres.version())).isEmpty();
    }

    @Test
    void upgradePathIsEmptyWhenNoChainReachesCurrentVersion() {
        assertThat(registry.findUpgradePath("postgres", "0.0.0-not-a-version")).isEmpty();
    }

    @Test
    void validateReturnsEmptyForASelfConsistentBatch() {
        assertThat(registry.validate(List.of(), List.of("base"))).isEmpty();
    }

    @Test
    void validateReportsMissingRequires() {
        List<String> problems = registry.validate(List.of(), List.of("notification-service"));
        assertThat(problems).isNotEmpty();
        assertThat(problems.toString()).contains("requires");
    }

    @Test
    void validateEnforcesOrderWithinABatch() {
        List<String> problems = registry.validate(List.of(),
                List.of("notification-service", "base"));
        assertThat(problems.toString()).contains("AFTER");
    }

    @Test
    void validateRejectsConflictingModules() {
        Map<String, ModuleManifest> byId = new java.util.HashMap<>();
        for (ModuleManifest m : registry.all()) {
            byId.put(m.id(), m);
        }
        for (ModuleManifest m : registry.all()) {
            for (String conflict : m.conflicts()) {
                if (byId.containsKey(conflict)) {
                    List<String> problems = registry.validate(List.of(), List.of(m.id(), conflict));
                    assertThat(problems.toString()).contains("conflicts");
                    return;
                }
            }
        }
    }

    @Test
    void validateFlagsUnknownModuleIds() {
        List<String> problems = registry.validate(List.of(), List.of("not-a-real-module"));
        assertThat(problems).contains("Unknown module: not-a-real-module");
    }
}
