package com.jloom.cli;

import org.jline.terminal.Terminal;
import org.springframework.context.ApplicationContext;

import com.jloom.orchestrate.ModuleApplier;
import com.jloom.orchestrate.UpgradeEngine;
import com.jloom.registry.ArchetypeRegistry;
import com.jloom.registry.ModuleRegistry;
import com.jloom.registry.ServiceRegistry;
import com.jloom.state.ProjectStateStore;

public class JloomContext {

    private final ApplicationContext spring;

    public JloomContext(ApplicationContext spring) {
        this.spring = spring;
    }

    public ModuleApplier applier() { return spring.getBean(ModuleApplier.class); }
    public UpgradeEngine upgradeEngine() { return spring.getBean(UpgradeEngine.class); }
    public ModuleRegistry modules() { return spring.getBean(ModuleRegistry.class); }
    public ServiceRegistry services() { return spring.getBean(ServiceRegistry.class); }
    public ArchetypeRegistry archetypes() { return spring.getBean(ArchetypeRegistry.class); }
    public ProjectStateStore stateStore() { return spring.getBean(ProjectStateStore.class); }
    public Terminal terminal() { return spring.getBean(Terminal.class); }
    public JloomPrompts prompts() { return new JloomPrompts(terminal()); }
}