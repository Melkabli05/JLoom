import * as clack from "@clack/prompts";
import type { Option } from "@clack/prompts";
export function isInteractive(): boolean {
  return process.stdin.isTTY === true && process.stdout.isTTY === true;
}
const colorOn = process.stdout.isTTY === true && process.env.NO_COLOR === undefined;
const c = (code: string, s: string): string => (colorOn ? `\x1b[${code}m${s}\x1b[0m` : s);
const question = (s: string): string => c("1;36", s);
const accent = (s: string): string => c("36", s);
const hint = (s: string): string => c("2", s);
const ok = (s: string): string => c("32", s);
const err = (s: string): string => c("31", s);
export const output = { question, accent, hint, ok, err, isColorOn: colorOn };
function hasText(value: string | undefined): value is string {
  return value !== undefined && value.trim() !== "";
}


function assertNotCancelled<T>(value: T | symbol): T {
  if (clack.isCancel(value)) {
    clack.cancel("Operation cancelled.");
    process.exit(0);
  }
  return value as T;
}
export async function askText(
  provided: string | undefined,
  promptLabel: string,
  message: string,
  defaultValue: string,
  validate?: (value: string) => string | undefined,
): Promise<string> {
  if (hasText(provided)) {
    const error = validate?.(provided);
    if (error !== undefined) throw new Error(`--${promptLabel}: ${error}`);
    return provided;
  }
  if (!isInteractive()) {
    throw new Error(`--${promptLabel} is required (no interactive terminal to prompt on)`);
  }
  const value = await clack.text({
    message,
    placeholder: defaultValue,
    defaultValue,


    validate: validate === undefined ? undefined : (v) => (hasText(v) ? validate(v) : undefined),
  });
  return assertNotCancelled(value);
}
export async function askNonBlankText(provided: string | undefined, promptLabel: string, message: string): Promise<string> {
  if (hasText(provided)) return provided;
  if (!isInteractive()) {
    throw new Error(`--${promptLabel} is required (no interactive terminal to prompt on)`);
  }
  const value = await clack.text({
    message,
    validate: (v) => (hasText(v) ? undefined : "Required."),
  });
  return assertNotCancelled(value);
}
export async function askOptional<T extends string>(
  provided: T | undefined,
  message: string,
  options: Option<T | undefined>[],
): Promise<T | undefined> {
  if (provided !== undefined) return provided;
  if (!isInteractive()) return undefined;
  const value = await clack.select<T | undefined>({ message, options });
  return assertNotCancelled(value);
}
export async function askChoice<T extends string>(
  provided: T | undefined,
  promptLabel: string,
  message: string,
  options: Option<T>[],
  defaultValue: T,
): Promise<T> {
  if (provided !== undefined) return provided;
  if (!isInteractive()) {
    throw new Error(`--${promptLabel} is required (no interactive terminal to prompt on)`);
  }
  const value = await clack.select<T>({ message, options, initialValue: defaultValue });
  return assertNotCancelled(value);
}
export async function askMultiple<T extends string>(message: string, options: Option<T>[]): Promise<T[]> {
  if (!isInteractive()) return [];
  const value = await clack.multiselect<T>({ message, options, required: false });
  return assertNotCancelled(value);
}
export async function askConfirm(message: string, defaultYes = true): Promise<boolean> {
  if (!isInteractive()) return defaultYes;
  const value = await clack.confirm({ message, initialValue: defaultYes });
  return assertNotCancelled(value);
}
