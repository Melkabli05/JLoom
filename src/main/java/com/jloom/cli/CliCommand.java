package com.jloom.cli;

import picocli.CommandLine.Option;

/**
 * Marker parent for every jloom subcommand. Contributes a shared
 * {@code --quiet / -q} flag that de-noises a command's non-essential output
 * for scripts. Note: picocli does not merge {@code @Command} attributes
 * (like {@code mixinStandardHelpOptions}) from this superclass onto
 * subclasses — each subcommand's own {@code @Command} annotation must set
 * {@code mixinStandardHelpOptions = true} itself for {@code -h/--help} to work.
 */
public abstract class CliCommand {

    @Option(names = {"-q", "--quiet"}, description = "Suppress startup banner and other non-essential output.")
    boolean quiet;
}
