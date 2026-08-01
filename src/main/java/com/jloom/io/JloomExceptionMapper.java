package com.jloom.io;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.core.command.CommandExecutionException;
import org.springframework.shell.core.command.ExitStatus;
import org.springframework.shell.core.command.exit.ExitStatusExceptionMapper;

import java.lang.reflect.InvocationTargetException;

@Configuration
public class JloomExceptionMapper {

    @Bean("jloomExitStatusMapper")
    ExitStatusExceptionMapper jloomExitStatusMapper(JloomOutput output) {
        return ex -> {
            Throwable cause = unwrapReflection(ex);
            return switch (cause) {
                case CommandExecutionException cee ->
                        new ExitStatus(cee.getExitCode(), output.error(cee.getMessage()));
                default -> new ExitStatus(1, output.error(cause.getMessage()));
            };
        };
    }

    private static Throwable unwrapReflection(Throwable ex) {
        while (ex instanceof InvocationTargetException ite && ite.getCause() != null) {
            ex = ite.getCause();
        }
        return ex;
    }
}