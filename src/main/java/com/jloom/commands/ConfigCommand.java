package com.jloom.commands;

import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.jline.tui.style.ThemeActive;

@CommandGroup(name = "jloom")
final class ConfigCommand {

    private final ThemeActive themeActive;

    ConfigCommand(ThemeActive themeActive) {
        this.themeActive = themeActive;
    }

    @Command(name = "config", description = "Print the resolved jloom configuration (env vars + defaults).", exitStatusExceptionMapper = "jloomExitStatusMapper")
    public String config() {
        String theme = themeActive.get();
        String color = "default".equals(theme) ? "ON" : "OFF";
        return """
                jloom config:
                  color:       %s (theme=%s)
                  state dir:   <project>/.jloom (per-project; not configurable)
                """.formatted(color, theme);
    }
}