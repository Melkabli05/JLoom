package com.jloom.io;

import org.jline.terminal.Terminal;
import org.springframework.shell.jline.tui.style.TemplateExecutor;

import java.util.Map;

public class JloomOutput {

    private final TemplateExecutor templateExecutor;
    private final Terminal terminal;

    public JloomOutput(TemplateExecutor templateExecutor, Terminal terminal) {
        this.templateExecutor = templateExecutor;
        this.terminal = terminal;
    }

    public String success(String message) {
        return styled("style-level-info", "✓ " + message);
    }

    public String error(String message) {
        return styled("style-level-error", "✗ " + message);
    }

    public String heading(String text) {
        return styled("style-title", text);
    }

    private String styled(String styleTag, String text) {
        var attributes = Map.<String, Object>of("text", text);
        String template = "<text; format=\"" + styleTag + "\">";
        return templateExecutor.render(template, attributes).toAnsi(terminal);
    }
}
