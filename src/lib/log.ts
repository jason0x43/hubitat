import { CommanderStatic } from 'commander';

/**
 * Debug log
 */
export function createLogger(program: CommanderStatic): Logger {
  if (program.verbose) {
    return console.log;
  } else {
    return (..._args: any[]) => {};
  }
}

export interface Logger {
  (...args: any[]): void;
}
