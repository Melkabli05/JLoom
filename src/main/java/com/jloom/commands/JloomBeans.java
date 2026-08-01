package com.jloom.commands;

import com.jloom.exec.GradleRewriteRunner;
import com.jloom.io.JloomOutput;
import com.jloom.orchestrate.ModuleApplier;
import com.jloom.orchestrate.UpgradeEngine;
import com.jloom.registry.ArchetypeRegistry;
import com.jloom.registry.ModuleRegistry;
import com.jloom.registry.ServiceRegistry;
import com.jloom.state.ProjectStateStore;
import org.jline.terminal.Terminal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.shell.core.command.CommandOption;
import org.springframework.shell.core.command.completion.CompletionContext;
import org.springframework.shell.core.command.completion.CompletionProposal;
import org.springframework.shell.core.command.completion.CompletionProvider;
import org.springframework.shell.jline.tui.component.flow.ComponentFlow;
import org.springframework.shell.jline.tui.style.TemplateExecutor;
import org.springframework.shell.jline.tui.style.ThemeActive;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
class JloomBeans {

    @Bean ModuleRegistry moduleRegistry() { return ModuleRegistry.loadBundled(); }
    @Bean ServiceRegistry serviceRegistry() { return ServiceRegistry.loadBundled(); }
    @Bean ArchetypeRegistry archetypeRegistry() { return ArchetypeRegistry.loadBundled(); }
    @Bean ProjectStateStore projectStateStore() { return new ProjectStateStore(); }

    @Bean InteractivePrompts interactivePrompts(ComponentFlow.Builder componentFlowBuilder, Terminal terminal) {
        return new InteractivePrompts(componentFlowBuilder, terminal);
    }

    @Bean JloomOutput jloomOutput(TemplateExecutor templateExecutor, Terminal terminal) {
        return new JloomOutput(templateExecutor, terminal);
    }

    @Bean NewCommands newCommands(ArchetypeRegistry archetypes, ServiceRegistry services,
                                  ModuleApplier applier,
                                  InteractivePrompts prompts, JloomOutput output) {
        return new NewCommands(archetypes, services, applier, prompts, output);
    }

    @Bean ReadCommands readCommands(ModuleRegistry modules, ServiceRegistry services, ArchetypeRegistry archetypes) {
        return new ReadCommands(modules, services, archetypes);
    }

    @Bean UpgradeCommands upgradeCommands(ModuleRegistry modules, ProjectStateStore stateStore,
                                          UpgradeEngine upgradeEngine, JloomOutput output) {
        return new UpgradeCommands(modules, stateStore, upgradeEngine, output);
    }

    @Bean UpgradeEngine upgradeEngine(ModuleRegistry registry) {
        return new UpgradeEngine(registry);
    }

    @Bean ConfigCommand configCommand(ThemeActive themeActive) {
        return new ConfigCommand(themeActive);
    }

    @Bean ModuleApplier moduleApplier(ModuleRegistry registry) {
        return new ModuleApplier(registry);
    }

    @Bean GradleRewriteRunner gradleRewriteRunner() {
        return new GradleRewriteRunner();
    }

    @Bean
    Converter<String, Map<String, String>> setOptionConverter() {
        return source -> {
            Map<String, String> result = new LinkedHashMap<>();
            if (!StringUtils.hasText(source)) {
                return result;
            }
            for (String pair : source.split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length != 2) {
                    throw new IllegalArgumentException(
                            "Invalid --set entry '" + pair + "', expected key=value");
                }
                result.put(kv[0].trim(), kv[1].trim());
            }
            return result;
        };
    }

    @Bean("jloomCompletionNew")
    CompletionProvider completionNew(ServiceRegistry services, ArchetypeRegistry archetypes) {
        return ctx -> switch (optionName(ctx)) {
            case "framework"      -> proposals(List.of("spring-boot", "micronaut"));
            case "service"        -> proposals(services.all().stream().map(s -> s.id()).toList());
            case "archetype"      -> proposals(archetypes.all().stream().map(a -> a.id()).toList());
            case "database"       -> proposals(List.of("postgres", "mysql", "mariadb", "h2", "none"));
            case "capabilities"   -> proposals(List.of("validation", "migrations", "security", "caching",
                    "aop", "scheduling", "async", "auditing", "observability", "openapi", "testing"));
            case "cache-provider" -> proposals(List.of("caffeine", "redis"));
            default               -> List.of();
        };
    }

    @Bean("jloomCompletionList")
    CompletionProvider completionList() {
        return ctx -> proposals(List.of("modules", "services", "archetypes"));
    }

    @Bean("jloomCompletionModule")
    CompletionProvider completionModule(ModuleRegistry modules) {
        return ctx -> switch (optionName(ctx)) {
            case "", "module" -> proposals(modules.all().stream().map(m -> m.id()).toList());
            default -> List.of();
        };
    }

    private static String optionName(CompletionContext ctx) {
        CommandOption opt = ctx.getCommandOption();
        if (opt == null) return "";
        return opt.longName() != null ? opt.longName() : String.valueOf(opt.shortName());
    }

    private static List<CompletionProposal> proposals(List<String> options) {
        return options.stream().<CompletionProposal>map(CompletionProposal::new).toList();
    }
}