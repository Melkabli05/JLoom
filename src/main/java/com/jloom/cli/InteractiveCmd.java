package com.jloom.cli;

import org.jline.terminal.Terminal;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Path;

@Component
@Command(name = "interactive", description = "Enter the interactive REPL (same as running 'jloom' with no args).")
public class InteractiveCmd implements Runnable {

    @ParentCommand
    JloomCommand parent;

    @Override
    public void run() {
        Terminal terminal = parent.context().terminal();
        org.jline.reader.LineReader reader = org.jline.reader.LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(org.jline.reader.LineReader.HISTORY_FILE,
                        Path.of(System.getProperty("user.home"), ".jloom", "history"))
                .build();
        try {
            picocli.CommandLine cmd = new picocli.CommandLine(new JloomCommand(parent.context()))
                    .setExecutionExceptionHandler(new JloomExceptionHandler())
                    .setParameterExceptionHandler(new JloomExceptionHandler());
            String line;
            while ((line = reader.readLine("jloom> ")) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.equalsIgnoreCase("quit") || trimmed.equalsIgnoreCase("exit")) break;
                cmd.execute(trimmed.split("\\s+"));
            }
        } finally {
            try { reader.getTerminal().close(); } catch (IOException _) { }
        }
    }
}