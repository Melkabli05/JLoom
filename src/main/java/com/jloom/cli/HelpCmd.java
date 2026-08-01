package com.jloom.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "help", mixinStandardHelpOptions = true, description = "Show top-level usage (same as 'jloom --help').")
public class HelpCmd extends CliCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.root().commandLine().usage(System.out);
    }
}
