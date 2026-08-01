package com.jloom;

import com.jloom.cli.JloomCommand;
import com.jloom.cli.JloomExceptionHandler;
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
            Terminal terminal = spring.getBean(Terminal.class);
            int exitCode = new CommandLine(root)
                    .setOut(new java.io.PrintWriter(System.out, true))
                    .setErr(new java.io.PrintWriter(System.err, true))
                    .setExecutionExceptionHandler(new JloomExceptionHandler())
                    .setParameterExceptionHandler(new JloomExceptionHandler())
                    .setCaseInsensitiveEnumValuesAllowed(true)
                    .setUnmatchedArgumentsAllowed(false)
                    .execute(args);
            if (exitCode == 0 && args.length == 0) {
                runInteractive(spring, terminal, root);
            }
            System.exit(exitCode);
        } finally {
            spring.close();
        }
    }

    private static void runInteractive(ConfigurableApplicationContext spring, Terminal terminal, JloomCommand root) {
        org.jline.reader.LineReader reader = org.jline.reader.LineReaderBuilder.builder()
                .terminal(terminal)
                .variable(org.jline.reader.LineReader.HISTORY_FILE,
                        java.nio.file.Path.of(System.getProperty("user.home"), ".jloom", "history"))
                .build();
        try {
            picocli.CommandLine cmd = new picocli.CommandLine(root)
                    .setOut(new java.io.PrintWriter(System.out, true))
                    .setErr(new java.io.PrintWriter(System.err, true))
                    .setExecutionExceptionHandler(new JloomExceptionHandler())
                    .setParameterExceptionHandler(new JloomExceptionHandler());
            String line;
            while ((line = reader.readLine("jloom> ")) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.equalsIgnoreCase("quit") || trimmed.equalsIgnoreCase("exit")) break;
                // Strip a leading "jloom" token if the user typed it (mimicking CLI mode).
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length > 0 && tokens[0].equals("jloom")) {
                    String[] stripped = new String[tokens.length - 1];
                    System.arraycopy(tokens, 1, stripped, 0, stripped.length);
                    tokens = stripped;
                }
                cmd.execute(tokens);
            }
        } finally {
            try { reader.getTerminal().close(); } catch (java.io.IOException _) { }
        }
    }
}