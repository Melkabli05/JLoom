export interface FrameworkSupport {
  id: string;
  displayName: string;
  currentVersion: string;
  buildDsl: string;
  smokeModule: string;
  supportedCapabilities: Set<string>;
  supports(capabilityId: string): boolean;
}

const SUPPORTED = new Set([
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
  "integration-testing",
]);

const SUPPORTED_WITHOUT_CACHING = new Set(SUPPORTED);
SUPPORTED_WITHOUT_CACHING.delete("caching");

export const SPRING_BOOT: FrameworkSupport = {
  id: "spring-boot",
  displayName: "Spring Boot 4",
  currentVersion: "4.x",
  buildDsl: "gradle-kotlin",
  smokeModule: "base",
  supportedCapabilities: SUPPORTED,
  supports(capabilityId: string): boolean {
    return SUPPORTED.has(capabilityId);
  },
};

export const MICRONAUT: FrameworkSupport = {
  id: "micronaut",
  displayName: "Micronaut 4",
  currentVersion: "4.x",
  buildDsl: "gradle-groovy",
  smokeModule: "base-micronaut",
  supportedCapabilities: SUPPORTED_WITHOUT_CACHING,
  supports(capabilityId: string): boolean {
    return SUPPORTED_WITHOUT_CACHING.has(capabilityId);
  },
};

export function allIds(): string[] {
  return [SPRING_BOOT.id, MICRONAUT.id];
}

export function byId(id: string): FrameworkSupport {
  switch (id) {
    case "spring-boot":
      return SPRING_BOOT;
    case "micronaut":
      return MICRONAUT;
    default:
      throw new Error(`Unsupported framework: '${id}'. Supported: [${allIds().join(", ")}]`);
  }
}
