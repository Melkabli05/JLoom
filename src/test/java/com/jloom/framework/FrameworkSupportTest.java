package com.jloom.framework;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameworkSupportTest {

    @Test
    void springBootSupportsEveryDeclaredCapability() {
        FrameworkSupport fw = FrameworkSupport.byId("spring-boot");
        for (String capability : fw.supportedCapabilities()) {
            assertThat(fw.supports(capability))
                    .as("spring-boot supports '%s'", capability)
                    .isTrue();
        }
    }

    @Test
    void micronautSupportsEveryDeclaredCapabilityExceptCaching() {
        FrameworkSupport fw = FrameworkSupport.byId("micronaut");
        for (String capability : fw.supportedCapabilities()) {
            assertThat(fw.supports(capability))
                    .as("micronaut supports '%s'", capability)
                    .isTrue();
        }
        assertThat(fw.supports("caching")).isFalse();
    }

    @Test
    void neitherFrameworkAdvertisesCapabilitiesForWhichNoModuleExists() {
        java.util.Set<String> advertised = new java.util.HashSet<>();
        advertised.addAll(FrameworkSupport.byId("spring-boot").supportedCapabilities());
        advertised.addAll(FrameworkSupport.byId("micronaut").supportedCapabilities());

        for (com.jloom.registry.ModuleManifest m : com.jloom.registry.ModuleRegistry.loadBundled().all()) {
            if (m.provides() != null && m.provides().startsWith("capability:")) {
                String cap = m.provides().substring("capability:".length());
                assertThat(advertised).as("capability '%s' is provided by %s but not advertised", cap, m.id())
                        .contains(cap);
            }
        }
    }

    @Test
    void byIdRejectsUnknownFrameworkWithAllKnownIdsInTheMessage() {
        assertThatThrownBy(() -> FrameworkSupport.byId("quarkus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spring-boot")
                .hasMessageContaining("micronaut");
    }

    @Test
    void allIdsReturnsEveryFrameworkInDeclarationOrder() {
        assertThat(FrameworkSupport.allIds())
                .containsExactly("spring-boot", "micronaut");
    }
}
