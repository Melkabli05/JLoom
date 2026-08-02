/** Mirrors util/Tokens.java: naive literal {{key}} -> value substring replacement, no escaping. */
export function substitute(text: string, tokens: Record<string, string>): string {
  let result = text;
  for (const [key, value] of Object.entries(tokens)) {
    result = result.split(`{{${key}}}`).join(value);
  }
  return result;
}
