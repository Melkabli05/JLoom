package {{package}}.infrastructure.observation;

import io.micrometer.observation.annotation.Observed;

// Base class every domain ServiceImpl can extend to opt into method-level timing + tracing
// without per-method annotations. The aspect auto-attaches class + method low-cardinality tags,
// so a class-level @Observed is the recommended idiomatic pattern (per Micrometer's own docs)
// when the goal is "observe every method by default."
//
// On its own, this class emits a Timer metric and a tracing span per method call. Once the
// otel-tracing module is also applied, those spans nest under the HTTP server span the OTel
// bridge emits automatically — no manual correlation required.
@Observed(name = "service.call")
public abstract class ObservedService {
}
