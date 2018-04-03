import * as WebSocket from 'ws';
import { Context, die, validateId } from './common';
import { XmlEntities } from 'html-entities';

// Setup cli ------------------------------------------------------------------

export default function init(context: Context) {
  const { program, hubitatHost } = context;

  program
    .command('log [type]')
    .option('-n, --name <name>', 'App or device name')
    .option('-i, --id <id>', 'App or device id', validateId)
    .description(
      'Log events for a given source, type of source, or all sources'
    )
    .action((type: string, cmd: { name?: string; id?: number }) => {
      const rtype = validateType(type);
      const _name = validateName(cmd.name);

      if ((cmd.id || _name) && !rtype) {
        die('An ID or name requires a type');
      }

      const ws = new WebSocket(`ws://${hubitatHost}/logsocket`);
      const entities = new XmlEntities();

      ws.on('open', () => {
        console.log('Opened connection to Hubitat');
      });

      ws.on('message', (data: string) => {
        const msg: Message = JSON.parse(data);
        if (_name && msg.name !== _name) {
          return;
        }
        if (rtype && msg.type !== rtype) {
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
  if (!type) {
    return type;
  }
  if (/apps?/.test(type)) {
    return 'app';
  }
  if (/dev(ices)?/.test(type)) {
    return 'dev';
  }
  die('Type should be "app" or "dev"');
}

function validateName(name?: string) {
  if (typeof name === 'string') {
    return name;
  }
  return null;
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
