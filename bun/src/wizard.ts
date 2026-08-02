import type { ReplIo } from "./lineSource.ts";

export function isInteractive(): boolean {
  return process.stdin.isTTY === true && process.stdout.isTTY === true;
}

const RESET = "\x1b[0m";
const colorOn = process.stdout.isTTY === true && process.env.NO_COLOR === undefined;
const c = (code: string, s: string): string => (colorOn ? `\x1b[${code}m${s}${RESET}` : s);
const question = (s: string): string => c("1;36", s);
const accent = (s: string): string => c("36", s);
const hint = (s: string): string => c("2", s);
const ok = (s: string): string => c("32", s);
const err = (s: string): string => c("31", s);

export const output = { question, accent, hint, ok, err, isColorOn: colorOn };

function hasText(value: string | undefined): value is string {
  return value !== undefined && value.trim() !== "";
}

async function readLine(io: ReplIo, promptText: string): Promise<string | null> {
  io.rl.setPrompt(promptText);
  io.rl.prompt();
  return io.lines.next();
}

/** Free-text prompt with a default value applied on blank input. */
export async function askText(
  io: ReplIo,
  provided: string | undefined,
  promptLabel: string,
  prompt: string,
  defaultValue: string,
): Promise<string> {
  if (hasText(provided)) return provided;
  if (!isInteractive()) {
    throw new Error(`--${promptLabel} is required (no interactive terminal to prompt on)`);
  }
  console.log(question(prompt));
  const line = (await readLine(io, `${promptLabel} ${hint(`[${defaultValue}]`)}: `)) ?? "";
  return line.trim() === "" ? defaultValue : line.trim();
}

/** Free-text prompt that won't accept blank input - loops until non-empty. */
export async function askNonBlankText(
  io: ReplIo,
  provided: string | undefined,
  promptLabel: string,
  prompt: string,
): Promise<string> {
  if (hasText(provided)) return provided;
  if (!isInteractive()) {
    throw new Error(`--${promptLabel} is required (no interactive terminal to prompt on)`);
  }
  console.log(question(prompt));
  while (true) {
    const line = await readLine(io, `${promptLabel}: `);
    if (line === null) throw new Error(`--${promptLabel} is required`);
    if (line.trim() !== "") return line.trim();
  }
}

/** Numbered choice list with a "0" sentinel for an optional "none". */
export async function askOptional(
  io: ReplIo,
  provided: string | undefined,
  promptLabel: string,
  prompt: string,
  choices: Map<string, string>,
  noneLabel: string,
): Promise<string | undefined> {
  if (hasText(provided)) return provided;
  if (!isInteractive()) return undefined;
  const labels = [...choices.keys()];
  console.log(question(prompt));
  labels.forEach((label, i) => console.log(`  ${accent(`${i + 1})`)} ${label}`));
  console.log(`  ${accent("0)")} ${noneLabel}`);
  const line = ((await readLine(io, `Choose [0-${labels.length}]: `)) ?? "").trim();
  if (line === "" || line === "0") return undefined;
  const idx = Number.parseInt(line, 10);
  if (Number.isInteger(idx) && idx >= 1 && idx <= labels.length) {
    return choices.get(labels[idx - 1]!);
  }
  return undefined;
}

/** Numbered choice list with a required answer - throws on empty/non-interactive. */
export async function askChoice(
  io: ReplIo,
  provided: string | undefined,
  promptLabel: string,
  prompt: string,
  choices: Map<string, string>,
  defaultLabel: string,
): Promise<string> {
  if (hasText(provided)) return provided;
  if (!isInteractive()) {
    throw new Error(`--${promptLabel} is required (no interactive terminal to prompt on)`);
  }
  const defaultId = choices.get(defaultLabel)!;
  const labels = [...choices.keys()];
  console.log(question(prompt));
  labels.forEach((label, i) => {
    const marker = label === defaultLabel ? ` ${hint("(default)")}` : "";
    console.log(`  ${accent(`${i + 1})`)} ${label}${marker}`);
  });
  const line = ((await readLine(io, `Choose [1-${labels.length}]: `)) ?? "").trim();
  if (line === "") return defaultId;
  const idx = Number.parseInt(line, 10);
  if (Number.isInteger(idx) && idx >= 1 && idx <= labels.length) {
    return choices.get(labels[idx - 1]!)!;
  }
  return defaultId;
}

/** Multi-select via toggling numbered choices, blank line confirms. */
export async function askMultiple(
  io: ReplIo,
  prompt: string,
  choices: Map<string, string>,
): Promise<string[]> {
  if (!isInteractive()) return [];
  const labels = [...choices.keys()];
  console.log(`${question(prompt)} ${hint("(space to toggle, enter to confirm)")}`);
  const selected = new Array(labels.length).fill(false);
  labels.forEach((label, i) => {
    console.log(`  [${selected[i] ? "x" : " "}] ${accent(`${i + 1})`)} ${label}`);
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
