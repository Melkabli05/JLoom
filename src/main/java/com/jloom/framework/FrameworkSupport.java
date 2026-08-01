package com.jloom.framework;

import java.util.Set;

public sealed interface FrameworkSupport
        permits FrameworkSupport.SpringBoot, FrameworkSupport.Micronaut {

    String id();

    String displayName();

    String currentVersion();

    boolean supports(String capabilityId);

    Set<String> supportedCapabilities();

    String buildDsl();

    String smokeModule();

    enum SpringBoot implements FrameworkSupport {
        INSTANCE;

        @Override public String id() { return "spring-boot"; }
        @Override public String displayName() { return "Spring Boot 4"; }
        @Override public String currentVersion() { return "4.x"; }
        @Override public String buildDsl() { return "gradle-kotlin"; }
        @Override public String smokeModule() { return "base"; }

        @Override
        public boolean supports(String capabilityId) {
            return SUPPORTED.contains(capabilityId);
        }

        @Override
        public Set<String> supportedCapabilities() {
            return SUPPORTED;
        }
    }

    enum Micronaut implements FrameworkSupport {
        INSTANCE;

        @Override public String id() { return "micronaut"; }
        @Override public String displayName() { return "Micronaut 4"; }
        @Override public String currentVersion() { return "4.x"; }
        @Override public String buildDsl() { return "gradle-groovy"; }
        @Override public String smokeModule() { return "base-micronaut"; }

        @Override
        public boolean supports(String capabilityId) {
            return SUPPORTED_WITHOUT_CACHING.contains(capabilityId);
        }

        @Override
        public Set<String> supportedCapabilities() {
            return SUPPORTED_WITHOUT_CACHING;
        }
    }

    Set<String> SUPPORTED = Set.of(
            "http-api",
            "database",
            "relational-db",
            "auth",
            "tracing",
            "observability",
            "migrations",
            "caching",
            "validation",
            "async",
            "scheduling",
            "auditing",
            "aop",
            "api-docs",
            "integration-testing"
    );

    Set<String> SUPPORTED_WITHOUT_CACHING = Set.of(
            "http-api", "database", "relational-db", "auth", "tracing",
            "observability", "migrations",
            "validation", "async", "scheduling", "auditing", "aop",
            "api-docs", "integration-testing"
    );

    static FrameworkSupport byId(String id) {
        return switch (id) {
            case "spring-boot" -> SpringBoot.INSTANCE;
            case "micronaut" -> Micronaut.INSTANCE;
            default -> throw new IllegalArgumentException(
                    "Unsupported framework: '" + id + "'. Supported: " + allIds());
        };
    }

    static java.util.List<String> allIds() {
        return java.util.List.of(SpringBoot.INSTANCE.id(), Micronaut.INSTANCE.id());
    }
}