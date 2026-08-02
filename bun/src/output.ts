// Mirrors JloomOutput.java's 6 styles, via raw ANSI escape codes instead of a chalk dependency.
// Auto-detects color support the same way picocli's Ansi.AUTO effectively did: only colorize
// when stdout is a real TTY and the user hasn't opted out via NO_COLOR (https://no-color.org/).
const colorEnabled = process.stdout.isTTY === true && process.env.NO_COLOR === undefined;

function wrap(code: string, text: string): string {
  return colorEnabled ? `\x1b[${code}m${text}\x1b[0m` : text;
}

export function success(message: string): string {
  return wrap("32", `✓ ${message}`);
}

export function error(message: string): string {
  return wrap("31", `✗ ${message}`);
}

export function heading(text: string): string {
  return wrap("1;33", text);
}

/** Wizard questions and menu titles. */
export function question(text: string): string {
  return wrap("1;36", text);
}

/** Secondary/instructional text - hints, defaults, dry-run notices. */
export function hint(text: string): string {
  return wrap("2", text);
}

/** An id, path, or other token worth calling out inline. */
export function accent(text: string): string {
  return wrap("36", text);
}

export function isColorEnabled(): boolean {
  return colorEnabled;
}
