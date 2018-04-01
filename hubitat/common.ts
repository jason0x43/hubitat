import { CommanderStatic } from 'commander';

export interface Context {
  hubitatHost: string;
  program: CommanderStatic;
}

/**
 * Display a message and quit
 */
export function die(message: string | Error) {
  if (typeof message === 'string') {
    console.error(message);
  } else {
    console.error(message.stack);
  }
  process.exit(1);
}
