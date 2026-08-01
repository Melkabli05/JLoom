package com.jloom;

import com.jloom.cli.JloomCommand;
import com.jloom.cli.JloomContext;
import com.jloom.cli.JloomRepl;
import org.jline.terminal.Terminal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

@SpringBootApplication
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext spring = SpringApplication.run(Main.class, args);
        try {
            JloomCommand root = spring.getBean(JloomCommand.class);
            JloomContext context = spring.getBean(JloomContext.class);
            Terminal terminal = spring.getBean(Terminal.class);
            int exitCode = new CommandLine(root)
                    .setOut(new java.io.PrintWriter(System.out, true))
                    .setErr(new java.io.PrintWriter(System.err, true))
                    .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                        cmd.getErr().println(picocli.CommandLine.Help.Ansi.AUTO.string("@|red ✗ " + c.getMessage() + "|@"));
                        return cmd.getCommandSpec().exitCodeOnExecutionException();
                    })
                    .setParameterExceptionHandler((ex, args2) -> {
                        String msg = ex instanceof picocli.CommandLine.UnmatchedArgumentException uae
                                ? "Unknown argument: " + uae.getUnmatched().get(0)
                                : ex.getMessage();
                        ex.getCommandLine().getErr().println(picocli.CommandLine.Help.Ansi.AUTO.string("@|red ✗ " + msg + "|@"));
                        ex.getCommandLine().getErr().println();
                        ex.getCommandLine().getErr().println("Run 'jloom --help' for usage.");
                        return ex.getCommandLine().getCommandSpec().exitCodeOnInvalidInput();
                    })
                    .setCaseInsensitiveEnumValuesAllowed(true)
                    .setUnmatchedArgumentsAllowed(false)
                    .execute(args);
            if (exitCode == 0 && args.length == 0) {
                JloomRepl.run(terminal, context);
            }
            System.exit(exitCode);
        } finally {
            spring.close();
        }
    }
}