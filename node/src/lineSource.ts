import type { Interface } from "node:readline/promises";

export interface LineSource {
  next(): Promise<string | null>;
}

/** readline/promises' question() cannot be called repeatedly against piped/closing stdin
 * reliably (the second call hangs or aborts even with buffered input still pending), and
 * can't be nested inside a `for await...of rl` loop either (both try to consume the same
 * 'line' event exclusively). This is a single persistent 'line' listener shared by the whole
 * process - the REPL's own loop and any nested wizard prompt both pull from the same queue,
 * so reads compose safely no matter how deep the call stack. */
export function createLineSource(rl: Interface): LineSource {
  const queue: string[] = [];
  const waiters: Array<(value: string | null) => void> = [];
  let closed = false;

  rl.on("line", (line: string) => {
    const waiter = waiters.shift();
    if (waiter !== undefined) {
      waiter(line);
    } else {
      queue.push(line);
    }
  });
  rl.on("close", () => {
    closed = true;
    while (waiters.length > 0) {
      waiters.shift()!(null);
    }
  });

  return {
    next(): Promise<string | null> {
      const queued = queue.shift();
      if (queued !== undefined) {
        return Promise.resolve(queued);
      }
      if (closed) {
        return Promise.resolve(null);
      }
      return new Promise((resolve) => waiters.push(resolve));
    },
  };
}

export interface ReplIo {
  rl: Interface;
  lines: LineSource;
}

export function createReplIo(rl: Interface): ReplIo {
  return { rl, lines: createLineSource(rl) };
}
