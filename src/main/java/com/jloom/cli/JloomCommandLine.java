package com.jloom.cli;

import picocli.CommandLine;

import java.io.PrintWriter;

/**
 * Builds the single, shared picocli {@link CommandLine} configuration (output
 * streams, error formatting, unmatched-argument handling) used by both the
 * one-shot CLI entry point and the interactive REPL, so the two can't drift.
 */
public final class JloomCommandLine {

    private JloomCommandLine() {
    }

    public static CommandLine create(JloomCommand root) {
        return new CommandLine(root)
                .setOut(new PrintWriter(System.out, true))
                .setErr(new PrintWriter(System.err, true))
                .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cmd.getErr().println(JloomOutput.error(cause.getMessage()));
                    return cmd.getCommandSpec().exitCodeOnExecutionException();
                })
                .setParameterExceptionHandler((ex, args) -> {
                    String msg = ex instanceof CommandLine.UnmatchedArgumentException uae
                            ? "Unknown argument: " + uae.getUnmatched().get(0)
                            : ex.getMessage();
                    ex.getCommandLine().getErr().println(JloomOutput.error(msg));
                    ex.getCommandLine().getErr().println();
                    ex.getCommandLine().getErr().println("Run 'jloom --help' for usage.");
                    return ex.getCommandLine().getCommandSpec().exitCodeOnInvalidInput();
                })
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setUnmatchedArgumentsAllowed(false);
    }
}
