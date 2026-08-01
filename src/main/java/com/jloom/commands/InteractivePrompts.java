package com.jloom.commands;

import org.jline.terminal.Terminal;
import org.springframework.shell.jline.tui.component.flow.ComponentFlow;
import org.springframework.shell.jline.tui.component.flow.SelectItem;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

final class InteractivePrompts {

    private final ComponentFlow.Builder componentFlowBuilder;
    private final Terminal terminal;

    InteractivePrompts(ComponentFlow.Builder componentFlowBuilder, Terminal terminal) {
        this.componentFlowBuilder = componentFlowBuilder;
        this.terminal = terminal;
    }

    boolean isInteractive() {
        String type = terminal.getType();
        return !Terminal.TYPE_DUMB.equals(type) && !Terminal.TYPE_DUMB_COLOR.equals(type);
    }

    String requireText(String provided, String optionName, String promptLabel, String defaultValue) {
        if (StringUtils.hasText(provided)) {
            return provided;
        }
        requireInteractiveTerminal(optionName);
        var flow = componentFlowBuilder.clone().reset()
                .withStringInput(optionName)
                    .name(promptLabel)
                    .defaultValue(defaultValue)
                    .and()
                .build();
        String entered = readResult(flow, optionName);
        return requireNonBlank(StringUtils.hasText(entered) ? entered : defaultValue, optionName);
    }

    String promptWithDefault(String provided, String optionName, String promptLabel, String defaultValue) {
        if (StringUtils.hasText(provided)) {
            return provided;
        }
        if (!isInteractive()) {
            return defaultValue;
        }
        var flow = componentFlowBuilder.clone().reset()
                .withStringInput(optionName)
                    .name(promptLabel)
                    .defaultValue(defaultValue)
                    .and()
                .build();
        String entered = readResult(flow, optionName);
        return StringUtils.hasText(entered) ? entered : defaultValue;
    }

    String requireChoice(String provided, String optionName, String promptLabel,
                         Map<String, String> choices, String defaultLabel) {
        if (StringUtils.hasText(provided)) {
            return provided;
        }
        requireInteractiveTerminal(optionName);
        return requireNonBlank(runSelector(optionName, promptLabel, choices, defaultLabel), optionName);
    }

    String chooseOptional(String provided, String optionName, String promptLabel,
                           Map<String, String> choices, String noneLabel) {
        if (StringUtils.hasText(provided)) {
            return provided;
        }
        if (!isInteractive()) {
            return null;
        }
        Map<String, String> withNone = new LinkedHashMap<>();
        withNone.put(noneLabel, noneLabel);
        withNone.putAll(choices);
        String selected = runSelector(optionName, promptLabel, withNone, noneLabel);
        return noneLabel.equals(selected) ? null : selected;
    }

    List<String> chooseMultiple(List<String> provided, String optionName, String promptLabel, Map<String, String> choices) {
        if (provided != null) {
            return provided;
        }
        if (!isInteractive()) {
            return List.of();
        }
        List<SelectItem> items = new ArrayList<>();
        for (Map.Entry<String, String> choice : choices.entrySet()) {
            items.add(SelectItem.of(choice.getKey(), choice.getValue()));
        }
        var flow = componentFlowBuilder.clone().reset()
                .withMultiItemSelector(optionName)
                    .name(promptLabel)
                    .selectItems(items)
                    .and()
                .build();
        Terminal.SignalHandler previous = terminal.handle(Terminal.Signal.INT, signal -> {
            System.out.println("\nCancelled.");
            System.exit(130);
        });
        try {
            var context = flow.run().getContext();
            try {
                List<?> selected = context.get(optionName, List.class);
                return selected == null ? List.of() : toStringValues(selected);
            } catch (NoSuchElementException e) {
                return List.of();
            }
        } finally {
            terminal.handle(Terminal.Signal.INT, previous);
        }
    }

    private static List<String> toStringValues(List<?> raw) {
        List<String> values = new ArrayList<>(raw.size());
        for (Object element : raw) {
            if (element instanceof String s) {
                values.add(s);
            } else if (element instanceof org.springframework.shell.jline.tui.component.support.Itemable<?> itemable) {
                values.add(String.valueOf(itemable.getItem()));
            } else {
                values.add(String.valueOf(element));
            }
        }
        return values;
    }

    private String runSelector(String optionName, String promptLabel, Map<String, String> choices, String defaultLabel) {
        var spec = componentFlowBuilder.clone().reset()
                .withSingleItemSelector(optionName)
                    .name(promptLabel)
                    .selectItems(choices);
        if (defaultLabel != null) {
            spec = spec.defaultSelect(defaultLabel);
        }
        return readResult(spec.and().build(), optionName);
    }

    private String readResult(ComponentFlow flow, String key) {
        Terminal.SignalHandler previous = terminal.handle(Terminal.Signal.INT, signal -> {
            System.out.println("\nCancelled.");
            System.exit(130);
        });
        try {
            var context = flow.run().getContext();
            try {
                return context.get(key, String.class);
            } catch (NoSuchElementException e) {
                return null;
            }
        } finally {
            terminal.handle(Terminal.Signal.INT, previous);
        }
    }

    private void requireInteractiveTerminal(String optionName) {
        if (!isInteractive()) {
            throw new IllegalArgumentException("--" + optionName + " is required (no interactive terminal to prompt on)");
        }
    }

    private static String requireNonBlank(String value, String optionName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("--" + optionName + " is required");
        }
        return value;
    }
}
