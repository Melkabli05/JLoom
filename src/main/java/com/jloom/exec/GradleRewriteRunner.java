package com.jloom.exec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class GradleRewriteRunner {

    interface ProcessWaiter {
        ProcessWaiterResult waitFor(Process process) throws IOException, InterruptedException;
    }

    record ProcessWaiterResult(int exitCode, String output) {
    }

    static final class StreamingWaiter implements ProcessWaiter {
        private final boolean tty;
        private final OutputSink sink;

        StreamingWaiter(boolean tty, OutputSink sink) {
            this.tty = tty;
            this.sink = sink;
        }

        @Override
        public ProcessWaiterResult waitFor(Process process) throws IOException, InterruptedException {
            // Always capture silently rather than streaming the child Gradle/OpenRewrite
            // process's raw output live — that output is internal build-tool chatter
            // (task boilerplate, recipe descriptors, deprecation warnings) that isn't
            // meaningful to a user watching `jloom new`. The ticker (see startTickerIfTty)
            // already gives progress feedback; the full output is only worth showing when
            // the run actually failed, so callers can debug it.
            StringBuilder captured = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) >= 0) {
                    captured.append(buf, 0, n);
                }
            }
            int exitCode = process.waitFor();
            return new ProcessWaiterResult(exitCode, captured.toString());
        }
    }

    interface OutputSink {
        void tick(int elapsedSeconds);
    }

    private static final OutputSink STDERR_SINK = elapsedSeconds -> {
        System.err.printf("\r[ . ] Running OpenRewrite recipes… %ds   ", elapsedSeconds);
        System.err.flush();
    };

    private final RewriteInitScriptWriter initScriptWriter = new RewriteInitScriptWriter();
    private final ProcessWaiter waiter;

    public GradleRewriteRunner() {
        this(new StreamingWaiter(System.console() != null, STDERR_SINK));
    }

    GradleRewriteRunner(ProcessWaiter waiter) {
        this.waiter = waiter;
    }

    public Result run(Path targetProjectRoot, String recipeYaml, String recipeName, boolean dryRun) {
        Path gradlew = requireGradleWrapper(targetProjectRoot);

        Path scratchDir = targetProjectRoot.resolve(".jloom").resolve("tmp");
        Path recipeFile = scratchDir.resolve("generated-recipe.yml");
        try {
            Files.createDirectories(scratchDir);
            Files.writeString(recipeFile, recipeYaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RewriteExecutionException("Could not write generated recipe file", e);
        }

        Path initScript = initScriptWriter.write(scratchDir, recipeFile, recipeName);

        List<String> command = List.of(
                gradlew.toAbsolutePath().toString(),
                "--init-script",
                initScript.toAbsolutePath().toString(),
                dryRun ? "rewriteDryRun" : "rewriteRun");

        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(targetProjectRoot.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new RewriteExecutionException("Failed to invoke target project's Gradle wrapper", e);
        }

        TickerHandle ticker = startTickerIfTty();
        try {
            ProcessWaiterResult result = waiter.waitFor(process);
            String output = result.output();
            int exitCode = result.exitCode();

            if (exitCode == 137 && oomInOutput(output)) {
                output = output + "\nOpenRewrite process was killed by SIGKILL with an OutOfMemoryError in the log — "
                        + "raise the JVM heap (GRADLE_OPTS='-Xmx…') and re-run.\n";
            } else if (exitCode == 137) {
                output = output + "\nOpenRewrite process was killed by SIGKILL (exit code 137). "
                        + "If this is unexpected, check for an external OOM killer, container limits, or manual kill.\n";
            }
            if (exitCode != 0 && !output.isEmpty()) {
                System.err.println(output);
            }
            return new Result(exitCode == 0, exitCode, output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new RewriteExecutionException("Interrupted while running OpenRewrite", e);
        } catch (IOException e) {
            throw new RewriteExecutionException("Failed to read subprocess output", e);
        } finally {
            stopTicker(ticker);
        }
    }

    public boolean hasGradleWrapper(Path targetProjectRoot) {
        return Files.exists(targetProjectRoot.resolve(isWindows() ? "gradlew.bat" : "gradlew"));
    }

    private Path requireGradleWrapper(Path targetProjectRoot) {
        Path wrapper = targetProjectRoot.resolve(isWindows() ? "gradlew.bat" : "gradlew");
        if (!Files.exists(wrapper)) {
            throw new RewriteExecutionException(
                    "No Gradle wrapper found at " + wrapper + ". jloom applies modules via the "
                            + "target project's own wrapper — projects generated by `jloom new` "
                            + "always include one; Maven support is out of scope for v0.1.",
                    null);
        }
        return wrapper;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private TickerHandle startTickerIfTty() {
        if (!(waiter instanceof StreamingWaiter sw) || !sw.tty) {
            return null;
        }
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jloom-openrewrite-ticker");
            t.setDaemon(true);
            return t;
        });
        ScheduledFuture<?> future = exec.scheduleAtFixedRate(
                new TickerTask(sw.sink, tickCount),
                2, 2, TimeUnit.SECONDS);
        return new TickerHandle(exec, future);
    }

    record TickerHandle(ScheduledExecutorService executor, ScheduledFuture<?> future) {
    }

    record TickerTask(OutputSink sink, java.util.concurrent.atomic.AtomicInteger counter)
            implements Runnable {
        @Override public void run() {
            sink.tick(2 * (1 + counter.incrementAndGet()));
        }
    }

    private final java.util.concurrent.atomic.AtomicInteger tickCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

    private void stopTicker(TickerHandle ticker) {
        if (ticker == null) return;
        ticker.future().cancel(false);
        System.err.print("\n");
        System.err.flush();
        ticker.executor().shutdownNow();
    }

    private static boolean oomInOutput(String output) {
        if (output == null) return false;
        return output.contains("OutOfMemoryError")
                || output.contains("GC overhead limit exceeded")
                || output.contains("unable to create new native thread");
    }

    public record Result(boolean success, int exitCode, String output) {
    }

    public static final class RewriteExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public RewriteExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}