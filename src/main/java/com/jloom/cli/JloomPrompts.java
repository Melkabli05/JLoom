package com.jloom.cli;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class JloomPrompts {

    private final Scanner scanner = new Scanner(System.in);

    public JloomPrompts() {
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
        System.out.print(promptLabel + " [" + defaultValue + "]: ");
        String line = scanner.nextLine();
        return (line == null || line.isBlank()) ? defaultValue : line.trim();
    }

    public String promptWithDefault(String provided, String promptLabel, String prompt, String defaultValue) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        if (!isInteractive()) {
            return defaultValue;
        }
        System.out.println(prompt);
        System.out.print(promptLabel + " [" + defaultValue + "]: ");
        String line = scanner.nextLine();
        return (line == null || line.isBlank()) ? defaultValue : line.trim();
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
        System.out.print("Choose [0-" + labels.size() + "]: ");
        String line = scanner.nextLine();
        if (line == null || line.isBlank() || "0".equals(line.trim())) {
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
        System.out.print("Choose [1-" + labels.size() + "]: ");
        String line = scanner.nextLine();
        if (line == null || line.isBlank()) {
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
        System.out.print("Toggle which? (e.g. '1 3' to toggle 1 and 3, blank to confirm): ");
        String line = scanner.nextLine();
        if (line == null || line.isBlank()) {
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
}