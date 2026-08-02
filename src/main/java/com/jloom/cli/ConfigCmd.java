package com.jloom.cli;

import org.jline.terminal.Terminal;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "config", mixinStandardHelpOptions = true, description = "Print the resolved jloom configuration (env vars + defaults).")
public class ConfigCmd extends CliCommand implements Runnable {

    @ParentCommand
    JloomCommand parent;

    @Override
    public void run() {
        Terminal terminal = parent.context().terminal();
        String type = terminal.getType();
        boolean color = !"dumb".equals(type) && !"dumb-color".equals(type);
        System.out.println("""
                %s
                  color:       %s (terminal-type=%s)
                  state dir:   <project>/.jloom (per-project; not configurable)
                """.formatted(JloomOutput.heading("jloom config:"), color ? "ON" : "OFF", type));
    }
}