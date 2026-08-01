package com.jloom.registry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceRegistryTest {

    private final ServiceRegistry registry = ServiceRegistry.loadBundled();

    @Test
    void bundledCatalogContainsAllShippedServices() {
        assertThat(registry.all()).extracting(ServiceManifest::id)
                .containsExactlyInAnyOrder(
                        "user-service", "notification-service", "identity-service",
                        "micronaut-skeleton", "file-service");
    }

    @Test
    void listShapeFallsBackToDefaultForEveryFrameworkTheServiceSupports() {
        ServiceManifest svc = registry.require("notification-service");
        assertThat(svc.modulesFor("spring-boot"))
                .as("list-shape service resolves to its __default__ list under spring-boot")
                .isNotEmpty();
        assertThat(svc.framework()).contains("spring-boot");
    }

    @Test
    void unknownServiceThrowsWithHelpfulHint() {
        assertThatThrownBy(() -> registry.require("does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist")
                .hasMessageContaining("jloom list services");
    }

    @Test
    void findReturnsEmptyForUnknownIdWithoutThrowing() {
        assertThat(registry.find("does-not-exist")).isEmpty();
    }
}