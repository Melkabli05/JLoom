package com.jloom.cli;

import com.jloom.exec.GradleRewriteRunner;
import com.jloom.orchestrate.ModuleApplier;
import com.jloom.orchestrate.UpgradeEngine;
import com.jloom.registry.ArchetypeRegistry;
import com.jloom.registry.ModuleRegistry;
import com.jloom.registry.ServiceRegistry;
import com.jloom.scaffold.FileTreeCopier;
import com.jloom.state.ProjectStateStore;
import com.jloom.compose.RecipeComposer;
import org.jline.terminal.Terminal;

public class JloomContext {

    private final ModuleRegistry modules;
    private final ServiceRegistry services;
    private final ArchetypeRegistry archetypes;
    private final ProjectStateStore stateStore;
    private final ModuleApplier applier;
    private final UpgradeEngine upgradeEngine;
    private final Terminal terminal;

    public JloomContext(Terminal terminal) {
        this.modules = ModuleRegistry.loadBundled();
        this.services = ServiceRegistry.loadBundled();
        this.archetypes = ArchetypeRegistry.loadBundled();
        this.stateStore = new ProjectStateStore();
        this.terminal = terminal;
        this.applier = new ModuleApplier(modules,
                new RecipeComposer(),
                new GradleRewriteRunner(),
                stateStore,
                new FileTreeCopier());
        this.upgradeEngine = new UpgradeEngine(modules);
    }

    public ModuleApplier applier() { return applier; }
    public UpgradeEngine upgradeEngine() { return upgradeEngine; }
    public ModuleRegistry modules() { return modules; }
    public ServiceRegistry services() { return services; }
    public ArchetypeRegistry archetypes() { return archetypes; }
    public ProjectStateStore stateStore() { return stateStore; }
    public Terminal terminal() { return terminal; }
    public JloomPrompts prompts() { return new JloomPrompts(); }
}