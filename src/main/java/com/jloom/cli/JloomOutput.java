package com.jloom.cli;

import picocli.CommandLine.Help.Ansi;

public final class JloomOutput {

    private JloomOutput() {
    }

    public static String success(String message) {
        return Ansi.AUTO.string("@|green ✓ " + message + "|@");
    }

    public static String error(String message) {
        return Ansi.AUTO.string("@|red ✗ " + message + "|@");
    }

    public static String heading(String text) {
        return Ansi.AUTO.string("@|bold,yellow " + text + "|@");
    }

    /** Wizard questions and menu titles — "What would you like to do?", "Project name". */
    public static String question(String text) {
        return Ansi.AUTO.string("@|bold,cyan " + text + "|@");
    }

    /** Secondary/instructional text — hints, defaults, ticker lines. */
    public static String hint(String text) {
        return Ansi.AUTO.string("@|faint " + text + "|@");
    }

    /** An id, path, or other token worth calling out inline. */
    public static String accent(String text) {
        return Ansi.AUTO.string("@|cyan " + text + "|@");
    }
}