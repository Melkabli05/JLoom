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

    private record MenuEntry(String command, String description) {
    }

    private static final List<MenuEntry> MENU = List.of(
            new MenuEntry("new", "Create a new project"),
            new MenuEntry("add", "Add a module to an existing project"),
            new MenuEntry("list", "List available modules, services, or archetypes"),
            new MenuEntry("info", "Show what a module does before applying it"),
            new MenuEntry("status", "Show applied modules and any newer versions"),
            new MenuEntry("upgrade", "Pull newer versions of one or more modules"),
            new MenuEntry("config", "Print the resolved jloom configuration"),
            new MenuEntry("help", "Show full usage")
    );

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
            printMenu();
            while (true) {
                String line;
                try {
                    line = reader.readLine("jloom> ");
                } catch (org.jline.reader.EndOfFileException e) {
                    break;
                } catch (org.jline.reader.UserInterruptException e) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.equalsIgnoreCase("quit") || trimmed.equalsIgnoreCase("exit") || trimmed.equals("0")) break;
                if (trimmed.equalsIgnoreCase("menu") || trimmed.equals("?")) {
                    printMenu();
                    continue;
                }

                String[] tokens = tokenize(trimmed);
                cmd.execute(tokens);
            }
        } finally {
            try { reader.getTerminal().close(); } catch (IOException _) { }
        }
    }

    private static String[] tokenize(String trimmed) {
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length > 0 && tokens[0].equals("jloom")) {
            String[] stripped = new String[tokens.length - 1];
            System.arraycopy(tokens, 1, stripped, 0, stripped.length);
            return stripped;
        }
        Integer choice = asMenuChoice(tokens[0]);
        if (choice != null) {
            tokens[0] = MENU.get(choice - 1).command();
        }
        return tokens;
    }

    private static Integer asMenuChoice(String token) {
        try {
            int idx = Integer.parseInt(token);
            return (idx >= 1 && idx <= MENU.size()) ? idx : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void printMenu() {
        System.out.println("What would you like to do?");
        for (int i = 0; i < MENU.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + MENU.get(i).description());
        }
        System.out.println("  0) Quit");
        System.out.println();
        System.out.println("Pick a number, or type a full command (tab-completes; 'menu' shows this again).");
        System.out.println();
    }
}
