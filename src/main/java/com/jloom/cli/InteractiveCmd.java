package com.jloom.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "interactive", mixinStandardHelpOptions = true, description = "Enter the interactive REPL (same as running 'jloom' with no args).")
public class InteractiveCmd extends CliCommand implements Runnable {

    @ParentCommand
    JloomCommand parent;

    @Override
    public void run() {
        JloomRepl.run(parent.context().terminal(), parent.context());
    }
}