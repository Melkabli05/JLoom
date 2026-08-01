package com.jloom.cli;

import picocli.CommandLine.Command;

@Command(name = "help", description = "Show top-level usage. Run 'jloom <command> --help' for command-specific options.")
public class HelpCmd implements Runnable {

    @Override
    public void run() {
        System.out.println("""
                jloom — generate and evolve production-ready backends

                Usage:
                  jloom [command] [options]

                Commands:
                  new        Create a new project (interactive or via flags)
                  add        Add a module to an existing project
                  list       List available modules, services, or archetypes
                  info       Show what a module does before applying it
                  status     Show applied modules and any newer versions
                  upgrade    Pull newer versions of one or more modules
                  config     Print the resolved jloom configuration
                  help       Show this message
                  interactive Enter the interactive REPL (same as running 'jloom' with no args)

                Examples:
                  jloom new --name my-app --service file-service --base-package com.acme.demo
                  jloom add postgres flyway --set postgres.db_name=demo
                  jloom list
                  jloom info --module postgres
                  jloom upgrade
                """);
    }
}