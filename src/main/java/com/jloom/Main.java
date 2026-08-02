package com.jloom;

import com.jloom.cli.JloomCommand;
import com.jloom.cli.JloomCommandLine;
import com.jloom.cli.JloomContext;
import com.jloom.cli.JloomRepl;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Terminal terminal = TerminalBuilder.builder().build();
        try {
            JloomContext context = new JloomContext(terminal);
            JloomCommand root = new JloomCommand(context);

            // No args → drop into the interactive REPL. We do this BEFORE invoking
            // Picocli.execute() because a subcommand list with mixinStandardHelpOptions
            // requires a subcommand (Picocli would otherwise emit 'Missing required
            // subcommand'). Routing to the REPL is the more useful default.
            if (args.length == 0) {
                JloomRepl.run(terminal, context);
                System.exit(0);
            }

            int exitCode = JloomCommandLine.create(root).execute(args);
            System.exit(exitCode);
        } finally {
            terminal.close();
        }
    }
}
