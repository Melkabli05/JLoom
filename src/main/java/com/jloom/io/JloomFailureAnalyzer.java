package com.jloom.io;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.shell.core.command.CommandExecutionException;

public class JloomFailureAnalyzer extends AbstractFailureAnalyzer<CommandExecutionException> {

    private static final char ESC = 0x1b;
    private static final char CROSS_MARK = '✗';

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, CommandExecutionException cause) {
        return new FailureAnalysis(cleanMessage(cause.getMessage()), null, cause);
    }

    private static String cleanMessage(String message) {
        int escIndex = message.indexOf(ESC);
        int glyphIndex = message.indexOf(CROSS_MARK);
        int idx = (escIndex >= 0 && (glyphIndex < 0 || escIndex < glyphIndex)) ? escIndex : glyphIndex;
        return idx >= 0 ? message.substring(idx) : message;
    }
}
