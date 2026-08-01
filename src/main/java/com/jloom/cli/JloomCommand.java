package com.jloom.cli;

import com.jloom.cli.AddCmd;
import com.jloom.cli.ConfigCmd;
import com.jloom.cli.HelpCmd;
import com.jloom.cli.InfoCmd;
import com.jloom.cli.InteractiveCmd;
import com.jloom.cli.ListCmd;
import com.jloom.cli.NewCmd;
import com.jloom.cli.StatusCmd;
import com.jloom.cli.UpgradeCmd;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Component
@Command(
        name = "jloom",
        mixinStandardHelpOptions = true,
        version = "jloom 0.2.0",
        description = "Generate and evolve production-ready backends.",
        subcommands = {
                NewCmd.class, AddCmd.class, ListCmd.class, InfoCmd.class,
                StatusCmd.class, UpgradeCmd.class, ConfigCmd.class, HelpCmd.class,
                InteractiveCmd.class
        }
)
public class JloomCommand {

    @Spec
    CommandSpec spec;

    private final JloomContext context;

    public JloomCommand(JloomContext context) {
        this.context = context;
    }

    public JloomContext context() {
        return context;
    }
}