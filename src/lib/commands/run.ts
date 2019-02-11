import { CommanderStatic } from 'commander';

import { die, simpleEncode } from '../common';
import { hubitatFetch } from '../request';
import { validateId } from '../resource';

// Setup cli ------------------------------------------------------------------

export default function init(program: CommanderStatic) {
  program
    .command('run <id> <command> [args...]')
    .description('Run a command on a device')
    .action(async (id: string, command: string, args: string[]) => {
      try {
        const _id = validateId(id)!;
        await runCommand(_id, command, args);
      } catch (error) {
        die(error);
      }
    });
}

async function runCommand(id: number, command: string, args: string[]) {
  const body: { [name: string]: string } = { id: String(id), method: command };
  if (args) {
    args.forEach((arg, i) => {
      body[`arg[${i + 1}]`] = arg;
      if (!isNaN(Number(arg))) {
        body[`argType.${i + 1}`] = 'NUMBER';
      }
    });
  }

  const response = await hubitatFetch(`/device/runmethod`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: simpleEncode(body)
  });

  if (response.status !== 200) {
    throw new Error(`Error running command: ${response.statusText}`);
  }
}
