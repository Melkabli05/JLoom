package com.jloom.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectStateStore {

    private static final String STATE_DIR = ".jloom";
    private static final String STATE_FILE = "state.json";

    private ObjectMapper mapper;

    public ProjectStateStore() {
        this(defaultMapper());
    }

    public ProjectStateStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void setMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ProjectState load(Path projectRoot) {
        Path stateFile = statePath(projectRoot);
        if (!Files.exists(stateFile)) {
            return ProjectState.empty();
        }
        try {
            return mapper.readValue(stateFile.toFile(), ProjectState.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + stateFile, e);
        }
    }

    public void save(Path projectRoot, ProjectState state) {
        Path stateFile = statePath(projectRoot);
        try {
            Files.createDirectories(stateFile.getParent());
            mapper.writeValue(stateFile.toFile(), state);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + stateFile, e);
        }
    }

    private static ObjectMapper defaultMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    private Path statePath(Path projectRoot) {
        return projectRoot.resolve(STATE_DIR).resolve(STATE_FILE);
    }
}