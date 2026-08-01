package com.jloom.cli;

import org.jline.terminal.Terminal;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class JloomRepl {

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

    private JloomRepl() {
    }

    public static void run(Terminal terminal, JloomContext context) {
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
                        Path.of(System.getProperty("user.home"), ".jloom", "history"))
                .build();
        CommandLine cmd = JloomCommandLine.create(new JloomCommand(context));
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
            try { reader.getTerminal().close(); } catch (IOException _) { }
        }
    }
}