package com.jloom.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectStateStoreTest {

    @TempDir
    Path tempDir;

    private final ProjectStateStore store = new ProjectStateStore();

    @Test
    void loadReturnsEmptyWhenNoStateFileExists() {
        assertThat(store.load(tempDir)).isEqualTo(ProjectState.empty());
    }

    @Test
    void saveThenLoadRoundTrips() {
        ProjectState seeded = ProjectState.empty()
                .withBasePackage("com.acme.demo")
                .withProjectName("acme")
                .withApplied(new AppliedModule("postgres", "1.0.0", Instant.parse("2026-01-01T00:00:00Z"), java.util.Map.of("db_name", "demo")));

        store.save(tempDir, seeded);
        ProjectState loaded = store.load(tempDir);

        assertThat(loaded.basePackage()).isEqualTo("com.acme.demo");
        assertThat(loaded.projectName()).isEqualTo("acme");
        assertThat(loaded.appliedIds()).containsExactly("postgres");
        assertThat(loaded.modules().get(0).version()).isEqualTo("1.0.0");
        assertThat(loaded.modules().get(0).answers()).containsEntry("db_name", "demo");
    }

    @Test
    void saveCreatesTheStateDirectoryIfMissing() {
        Path fresh = tempDir.resolve("brand-new-project");
        store.save(fresh, ProjectState.empty().withProjectName("brand-new-project"));

        assertThat(store.load(fresh).projectName()).isEqualTo("brand-new-project");
    }
}
