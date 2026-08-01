package com.jloom.cli;

import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

import java.io.PrintWriter;

public class JloomExceptionHandler implements IExecutionExceptionHandler, IParameterExceptionHandler {

    @Override
    public int handleExecutionException(Exception ex, CommandLine cmd, CommandLine.ParseResult parseResult) {
        cmd.getErr().println(JloomOutput.error(cause(ex).getMessage()));
        return cmd.getCommandSpec().exitCodeOnExecutionException();
    }

    @Override
    public int handleParseException(ParameterException ex, String[] args) {
        CommandLine cmd = ex.getCommandLine();
        PrintWriter err = cmd.getErr();
        if (ex instanceof UnmatchedArgumentException uae) {
            err.println(JloomOutput.error("Unknown argument: " + uae.getUnmatched().get(0)));
        } else {
            err.println(JloomOutput.error(ex.getMessage()));
        }
        cmd.getErr().println();
        cmd.getErr().println("Run 'jloom --help' for usage.");
        return cmd.getCommandSpec().exitCodeOnInvalidInput();
    }

    private static Throwable cause(Throwable t) {
        return t.getCause() != null ? t.getCause() : t;
    }
}