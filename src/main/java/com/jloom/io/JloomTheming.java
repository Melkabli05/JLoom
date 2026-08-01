package com.jloom.io;

import org.jline.terminal.Terminal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.jline.tui.style.ThemeActive;
import org.springframework.util.StringUtils;

@Configuration
public class JloomTheming {

    @Bean
    public ThemeActive themeActive(Terminal terminal) {
        return () -> {
            if (StringUtils.hasText(System.getenv("JLOOM_FORCE_COLOR"))) {
                return "default";
            }
            if (isTruthy(System.getenv("JLOOM_NO_COLOR"))) {
                return "dump";
            }
            if (System.getenv("NO_COLOR") != null || System.getenv("CI") != null) {
                return "dump";
            }
            String type = terminal.getType();
            if (Terminal.TYPE_DUMB.equals(type) || Terminal.TYPE_DUMB_COLOR.equals(type)) {
                return "dump";
            }
            return "default";
        };
    }

    private static boolean isTruthy(String value) {
        return StringUtils.hasText(value) && !value.equals("0") && !value.equalsIgnoreCase("false");
    }
}
