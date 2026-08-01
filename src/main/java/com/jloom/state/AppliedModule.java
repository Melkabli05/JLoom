package com.jloom.state;

import java.time.Instant;
import java.util.Map;

public record AppliedModule(
        String id,
        String version,
        Instant appliedAt,
        Map<String, String> answers
) {
}
