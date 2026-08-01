package com.jloom.cli;

import com.jloom.exec.GradleRewriteRunner;
import com.jloom.orchestrate.ModuleApplier;
import com.jloom.orchestrate.UpgradeEngine;
import com.jloom.registry.ArchetypeRegistry;
import com.jloom.registry.ModuleRegistry;
import com.jloom.registry.ServiceRegistry;
import com.jloom.scaffold.FileTreeCopier;
import com.jloom.state.ProjectStateStore;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JloomConfig {

    @Bean
    JloomContext jloomContext(ApplicationContext spring) {
        return new JloomContext(spring);
    }

    @Bean
    ModuleRegistry moduleRegistry() { return ModuleRegistry.loadBundled(); }

    @Bean
    ServiceRegistry serviceRegistry() { return ServiceRegistry.loadBundled(); }

    @Bean
    ArchetypeRegistry archetypeRegistry() { return ArchetypeRegistry.loadBundled(); }

    @Bean
    ProjectStateStore projectStateStore() { return new ProjectStateStore(); }

    @Bean
    ModuleApplier moduleApplier() {
        return new ModuleApplier(moduleRegistry());
    }

    @Bean
    UpgradeEngine upgradeEngine() {
        return new UpgradeEngine(moduleRegistry());
    }

    @Bean
    GradleRewriteRunner gradleRewriteRunner() {
        return new GradleRewriteRunner();
    }

    @Bean(destroyMethod = "close")
    Terminal jloomTerminal() throws Exception {
        return TerminalBuilder.builder().build();
    }
}