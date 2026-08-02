package com.jloom.cli;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

import java.util.List;
import java.util.Map;

/**
 * Wizard-style prompts for {@code jloom new}. Reads through the same JLine
 * {@link Terminal} the REPL uses (rather than a separate {@code Scanner} over
 * {@code System.in}), so there is exactly one line-reading stack regardless
 * of whether the wizard runs standalone or from inside the REPL.
 */
public class JloomPrompts {

    private final LineReader reader;

    public JloomPrompts(Terminal terminal) {
        this.reader = LineReaderBuilder.builder().terminal(terminal).build();
    }

    public static boolean isInteractive() {
        return System.console() != null;
    }

    public String requireText(String provided, String promptLabel, String prompt, String defaultValue) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        if (!isInteractive()) {
            throw new IllegalArgumentException("--" + promptLabel + " is required (no interactive terminal to prompt on)");
        }
        System.out.println(prompt);
        String line = readLine(promptLabel + " [" + defaultValue + "]: ");
        return line.isBlank() ? defaultValue : line.trim();
    }

    public String requireNonBlankText(String provided, String promptLabel, String prompt) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        if (!isInteractive()) {
            throw new IllegalArgumentException("--" + promptLabel + " is required (no interactive terminal to prompt on)");
        }
        System.out.println(prompt);
        while (true) {
            String line;
            try {
                line = reader.readLine(promptLabel + ": ");
            } catch (EndOfFileException | UserInterruptException e) {
                throw new IllegalArgumentException("--" + promptLabel + " is required");
            }
            if (!line.isBlank()) {
                return line.trim();
            }
        }
    }

    public String promptWithDefault(String provided, String promptLabel, String prompt, String defaultValue) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        if (!isInteractive()) {
            return defaultValue;
        }
        System.out.println(prompt);
        String line = readLine(promptLabel + " [" + defaultValue + "]: ");
        return line.isBlank() ? defaultValue : line.trim();
    }

    public String chooseOptional(String provided, String promptLabel, String prompt,
                                 Map<String, String> choices, String noneLabel) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        if (!isInteractive()) {
            return null;
        }
        List<String> labels = choices.keySet().stream().toList();
        System.out.println(prompt);
        for (int i = 0; i < labels.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + labels.get(i));
        }
        System.out.println("  0) " + noneLabel);
        String line = readLine("Choose [0-" + labels.size() + "]: ");
        if (line.isBlank() || "0".equals(line.trim())) {
            return null;
        }
        try {
            int idx = Integer.parseInt(line.trim());
            if (idx >= 1 && idx <= labels.size()) {
                return choices.get(labels.get(idx - 1));
            }
        } catch (NumberFormatException _) {
        }
        return null;
    }

    public String requireChoice(String provided, String promptLabel, String prompt,
                                Map<String, String> choices, String defaultLabel) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        if (!isInteractive()) {
            throw new IllegalArgumentException("--" + promptLabel + " is required (no interactive terminal to prompt on)");
        }
        List<String> labels = choices.keySet().stream().toList();
        String defaultId = choices.get(defaultLabel);
        System.out.println(prompt);
        for (int i = 0; i < labels.size(); i++) {
            String marker = labels.get(i).equals(defaultLabel) ? " (default)" : "";
            System.out.println("  " + (i + 1) + ") " + labels.get(i) + marker);
        }
        String line = readLine("Choose [1-" + labels.size() + "]: ");
        if (line.isBlank()) {
            return defaultId;
        }
        try {
            int idx = Integer.parseInt(line.trim());
            if (idx >= 1 && idx <= labels.size()) {
                return choices.get(labels.get(idx - 1));
            }
        } catch (NumberFormatException _) {
        }
        return defaultId;
    }

    public List<String> chooseMultiple(String promptLabel, String prompt, Map<String, String> choices) {
        if (!isInteractive()) {
            return List.of();
        }
        List<String> labels = choices.keySet().stream().toList();
        System.out.println(prompt + " (space to toggle, enter to confirm)");
        boolean[] selected = new boolean[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            System.out.printf("  [%s] %d) %s%n", selected[i] ? "x" : " ", (i + 1), labels.get(i));
        }
        String line = readLine("Toggle which? (e.g. '1 3' to toggle 1 and 3, blank to confirm): ");
        if (line.isBlank()) {
            return java.util.stream.IntStream.range(0, labels.size())
                    .filter(i -> selected[i])
                    .mapToObj(i -> choices.get(labels.get(i)))
                    .toList();
        }
        for (String token : line.trim().split("\\s+")) {
            try {
                int idx = Integer.parseInt(token);
                if (idx >= 1 && idx <= labels.size()) {
                    selected[idx - 1] = !selected[idx - 1];
                }
            } catch (NumberFormatException _) {
            }
        }
        return java.util.stream.IntStream.range(0, labels.size())
                .filter(i -> selected[i])
                .mapToObj(i -> choices.get(labels.get(i)))
                .toList();
    }

    private String readLine(String prompt) {
        try {
            return reader.readLine(prompt);
        } catch (EndOfFileException | UserInterruptException e) {
            return "";
        }
    }
}
