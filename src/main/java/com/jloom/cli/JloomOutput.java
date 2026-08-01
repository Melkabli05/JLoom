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
}