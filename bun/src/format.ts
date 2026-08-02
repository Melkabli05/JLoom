export function javaList(items: string[]): string {
  return `[${items.join(", ")}]`;
}
export function widthOf<T>(items: T[], field: (item: T) => string): number {
  return items.reduce((max, item) => Math.max(max, field(item).length), 0);
}
export function pad(text: string, width: number): string {
  return text + " ".repeat(Math.max(0, width - text.length));
}
