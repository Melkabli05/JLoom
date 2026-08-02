import * as output from "./output.ts";
import type { ReplIo } from "./lineSource.ts";

export function isInteractive(): boolean {
  return process.stdin.isTTY === true && process.stdout.isTTY === true;
}

/** Writes the prompt text (so the readline Interface's own line-editing/history/completion
 * still applies) and reads the next line via the shared queue rather than question(). Returns
 * null on EOF (Ctrl-D / closed stream) - distinct from "" (a literal blank Enter). */
async function readLine(io: ReplIo, promptText: string): Promise<string | null> {
  io.rl.setPrompt(promptText);
  io.rl.prompt();
  return io.lines.next();
}

function hasText(value: string | undefined): value is string {
  return value !== undefined && value.trim() !== "";
}

export async function requireText(
  io: ReplIo,
  provided: string | undefined,
  promptLabel: string,
  prompt: string,
  defaultValue: string,
): Promise<string> {
  if (hasText(provided)) {
    return provided;
  }
  if (!isInteractive()) {
    throw new Error(`--${promptLabel} is required (no interactive terminal to prompt on)`);
  }
  console.log(output.question(prompt));
  const line = (await readLine(io, `${promptLabel} ${output.hint(`[${defaultValue}]`)}: `)) ?? "";
  return line.trim() === "" ? defaultValue : line.trim();
}

export async function requireNonBlankText(
  io: ReplIo,
  provided: string | undefined,
  promptLabel: string,
  prompt: string,
): Promise<string> {
  if (hasText(provided)) {
    return provided;
  }
  if (!isInteractive()) {
    throw new Error(`--${promptLabel} is required (no interactive terminal to prompt on)`);
  }
  console.log(output.question(prompt));
  while (true) {
    const line = await readLine(io, `${promptLabel}: `);
    if (line === null) {
      throw new Error(`--${promptLabel} is required`);
    }
    if (line.trim() !== "") {
      return line.trim();
    }
  }
}

export async function promptWithDefault(
  io: ReplIo,
  provided: string | undefined,
  promptLabel: string,
  prompt: string,
  defaultValue: string,
): Promise<string> {
  if (hasText(provided)) {
    return provided;
  }
  if (!isInteractive()) {
    return defaultValue;
  }
  console.log(output.question(prompt));
  const line = (await readLine(io, `${promptLabel} ${output.hint(`[${defaultValue}]`)}: `)) ?? "";
  return line.trim() === "" ? defaultValue : line.trim();
}

export async function chooseOptional(
  io: ReplIo,
  provided: string | undefined,
  promptLabel: string,
  prompt: string,
  choices: Map<string, string>,
  noneLabel: string,
): Promise<string | undefined> {
  if (hasText(provided)) {
    return provided;
  }
  if (!isInteractive()) {
    return undefined;
  }
  const labels = [...choices.keys()];
  console.log(output.question(prompt));
  labels.forEach((label, i) => console.log(`  ${output.accent(`${i + 1})`)} ${label}`));
  console.log(`  ${output.accent("0)")} ${noneLabel}`);
  const line = ((await readLine(io, `Choose [0-${labels.length}]: `)) ?? "").trim();
  if (line === "" || line === "0") {
    return undefined;
  }
  const idx = Number.parseInt(line, 10);
  if (Number.isInteger(idx) && idx >= 1 && idx <= labels.length) {
    return choices.get(labels[idx - 1]!);
  }
  return undefined;
}

export async function requireChoice(
  io: ReplIo,
  provided: string | undefined,
  promptLabel: string,
  prompt: string,
  choices: Map<string, string>,
  defaultLabel: string,
): Promise<string> {
  if (hasText(provided)) {
    return provided;
  }
  const defaultId = choices.get(defaultLabel)!;
  if (!isInteractive()) {
    throw new Error(`--${promptLabel} is required (no interactive terminal to prompt on)`);
  }
  const labels = [...choices.keys()];
  console.log(output.question(prompt));
  labels.forEach((label, i) => {
    const marker = label === defaultLabel ? ` ${output.hint("(default)")}` : "";
    console.log(`  ${output.accent(`${i + 1})`)} ${label}${marker}`);
  });
  const line = ((await readLine(io, `Choose [1-${labels.length}]: `)) ?? "").trim();
  if (line === "") {
    return defaultId;
  }
  const idx = Number.parseInt(line, 10);
  if (Number.isInteger(idx) && idx >= 1 && idx <= labels.length) {
    return choices.get(labels[idx - 1]!)!;
  }
  return defaultId;
}

export async function chooseMultiple(
  io: ReplIo,
  promptLabel: string,
  prompt: string,
  choices: Map<string, string>,
): Promise<string[]> {
  if (!isInteractive()) {
    return [];
  }
  const labels = [...choices.keys()];
  console.log(`${output.question(prompt)} ${output.hint("(space to toggle, enter to confirm)")}`);
  const selected = new Array(labels.length).fill(false);
  labels.forEach((label, i) => {
    console.log(`  [${selected[i] ? "x" : " "}] ${output.accent(`${i + 1})`)} ${label}`);
  });
  const line = ((await readLine(io, "Toggle which? (e.g. '1 3' to toggle 1 and 3, blank to confirm): ")) ?? "").trim();
  if (line !== "") {
    for (const token of line.split(/\s+/)) {
      const idx = Number.parseInt(token, 10);
      if (Number.isInteger(idx) && idx >= 1 && idx <= labels.length) {
        selected[idx - 1] = !selected[idx - 1];
      }
    }
  }
  return labels.filter((_, i) => selected[i]).map((label) => choices.get(label)!);
}
