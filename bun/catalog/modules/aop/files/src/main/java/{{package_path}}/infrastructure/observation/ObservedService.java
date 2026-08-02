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
//
// IMPORTANT — Spring AOP self-invocation gotcha (the #1 AOP bug):
// Spring AOP is proxy-based, so @Observed only fires for EXTERNAL method calls on the proxied
// bean — calls into the bean from another Spring-managed bean, from the controller layer, etc.
// A `this.someMethod()` call from WITHIN the same instance (e.g., one helper invoking another
// on the same ServiceImpl) bypasses the proxy and silently drops observation, with no warning.
// If you have intra-class delegation, refactor the called-out logic into a separate bean
// (the cleanest fix) or inject the bean into itself and call through the field.
//
// Throwing methods are automatically tagged with low-cardinality `error=...` / `exception=...`
// tags on the Timer and the tracing span — no try/catch wrapping needed inside advice callers.
@Observed(name = "service.call")
public abstract class ObservedService {
}
