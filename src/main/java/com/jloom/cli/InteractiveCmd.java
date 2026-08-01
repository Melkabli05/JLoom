package com.jloom.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Component
@Command(name = "interactive", description = "Enter the interactive REPL (same as running 'jloom' with no args).")
public class InteractiveCmd implements Runnable {

    @ParentCommand
    JloomCommand parent;

    @Override
    public void run() {
        JloomRepl.run(parent.context().terminal(), parent.context());
    }
}