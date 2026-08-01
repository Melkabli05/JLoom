package com.jloom.cli;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JloomConfig {

    @Bean(destroyMethod = "close")
    Terminal jloomTerminal() throws Exception {
        return TerminalBuilder.builder().build();
    }

    @Bean
    JloomContext jloomContext(Terminal terminal) {
        return new JloomContext(terminal);
    }
}