import * as WebSocket from 'ws';
import { Context, die } from './common';
import { Command } from 'commander';
import { XmlEntities } from 'html-entities';

// Setup cli ------------------------------------------------------------------

export default function init(context: Context) {
  const { program, hubitatHost } = context;

  program
    .command('log')
    .option('-n, --name <name>', 'App or device name', /.*/, '')
    .option('-i, --id <id>', 'App or device id', validateId, '')
    .option('-t, --type <type>', "Source type ('app' or 'dev')", validateType)
    .description(
      'Log events for a given source, type of source, or all sources'
    )
    .action((cmd: { name?: string; type?: string; id?: number }) => {
      const ws = new WebSocket(`ws://${hubitatHost}/logsocket`);
      const entities = new XmlEntities();

      ws.on('open', () => {
        console.log('Opened connection to Hubitat');
      });

      ws.on('message', (data: string) => {
        const msg: Message = JSON.parse(data);
        if (cmd.name && msg.name !== cmd.name) {
          return;
        }
        if (cmd.type && msg.type !== cmd.type) {
          return;
        }
        if (cmd.id && msg.id !== cmd.id) {
          return;
        }
        logMessage(entities, msg);
      });
    });
}

function logMessage(entities: TextConverter, message: Message) {
  const { time, type, msg, level, id, name } = message;
  console.log(
    `${time} [${type}:${id}] (${level}) ${name} - ${entities.decode(msg)}`
  );
}

function validateType(type?: string) {
  if (type && type !== 'app' && type !== 'dev') {
    die('Type must be "app" or "dev"');
  }
  return type;
}

function validateId(value?: string) {
  if (value == null) {
    return value;
  }
  const id = Number(value);
  if (isNaN(id)) {
    die('ID must be a number');
  }
  return id;
}

interface Message {
  name: string;
  type: string;
  level: string;
  time: string;
  id: number;
  msg: string;
}

interface TextConverter {
  decode(input: string): string;
}
