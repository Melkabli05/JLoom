#!/usr/bin/env node
import { Command } from "commander";

const program = new Command();

program
  .name("jloom")
  .description("Generate and evolve production-ready backends.")
  .version("jloom 0.2.0");

await program.parseAsync(process.argv);
