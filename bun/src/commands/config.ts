import * as output from "../output.ts";

export function runConfig(): void {
  console.log(
    [
      output.heading("jloom config:"),
      `  color:       ${output.isColorEnabled() ? "ON" : "OFF"} (tty=${process.stdout.isTTY === true})`,
      "  state dir:   <project>/.jloom (per-project; not configurable)",
      "",
    ].join("\n"),
  );
}
