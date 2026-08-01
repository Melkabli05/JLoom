package com.jloom;

import com.jloom.cli.JloomCommand;
import com.jloom.cli.JloomExceptionHandler;
import com.jloom.cli.JloomContext;
import org.jline.terminal.Terminal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@SpringBootApplication
public final class Main {

    private static final Map<String, List<String>> COMPLETIONS = new TreeMap<>(Map.of(
            "new",       List.of("--name", "--service", "--base-package", "--framework", "--dry-run"),
            "add",       List.of("--project", "--set", "--dry-run"),
            "list",      List.of("--what", "modules", "services", "archetypes"),
            "info",      List.of("--module"),
            "status",    List.of("--project"),
            "upgrade",   List.of("--project", "--module", "--dry-run"),
            "config",    List.of(),
            "help",      List.of(),
            "interactive", List.of()
    ));

    private Main() {
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext spring = SpringApplication.run(Main.class, args);
        try {
            JloomCommand root = spring.getBean(JloomCommand.class);
            JloomContext context = spring.getBean(JloomContext.class);
            Terminal terminal = spring.getBean(Terminal.class);
            JloomExceptionHandler handler = new JloomExceptionHandler();
            int exitCode = new CommandLine(root)
                    .setOut(new java.io.PrintWriter(System.out, true))
                    .setErr(new java.io.PrintWriter(System.err, true))
                    .setExecutionExceptionHandler(handler::handleExecutionException)
                    .setParameterExceptionHandler(handler::handleParseException)
                    .setCaseInsensitiveEnumValuesAllowed(true)
                    .setUnmatchedArgumentsAllowed(false)
                    .execute(args);
            if (exitCode == 0 && args.length == 0) {
                runInteractive(terminal, context);
            }
            System.exit(exitCode);
        } finally {
            spring.close();
        }
    }

    private static void runInteractive(Terminal terminal, JloomContext context) {
        org.jline.reader.LineReader reader = org.jline.reader.LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new org.jline.reader.Completer() {
                    @Override
                    public void complete(org.jline.reader.LineReader r, org.jline.reader.ParsedLine line, List<org.jline.reader.Candidate> candidates) {
                        String word = line.word();
                        String last = word == null ? "" : word;
                        if (last.isEmpty()) {
                            for (String c : COMPLETIONS.keySet()) {
                                candidates.add(new org.jline.reader.Candidate(c));
                            }
                            return;
                        }
                        for (Map.Entry<String, List<String>> e : COMPLETIONS.entrySet()) {
                            if (e.getKey().startsWith(last)) {
                                candidates.add(new org.jline.reader.Candidate(e.getKey()));
                            }
                        }
                    }
                })
                .variable(org.jline.reader.LineReader.HISTORY_FILE,
                        java.nio.file.Path.of(System.getProperty("user.home"), ".jloom", "history"))
                .build();
        picocli.CommandLine cmd = new picocli.CommandLine(new com.jloom.cli.JloomCommand(context))
                .setOut(new java.io.PrintWriter(System.out, true))
                .setErr(new java.io.PrintWriter(System.err, true))
                .setExecutionExceptionHandler(new JloomExceptionHandler()::handleExecutionException)
                .setParameterExceptionHandler(new JloomExceptionHandler()::handleParseException);
        try {
            String line;
            while ((line = reader.readLine("jloom> ")) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.equalsIgnoreCase("quit") || trimmed.equalsIgnoreCase("exit")) break;
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