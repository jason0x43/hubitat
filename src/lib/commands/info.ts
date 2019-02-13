import { CommanderStatic } from 'commander';
import { die } from '../common';
import { makerFetch } from '../request';

// Setup cli ------------------------------------------------------------------

export default function init(program: CommanderStatic) {
  program
    .command('info <id>')
    .description('Show information about a specific device')
    .action(async id => {
      try {
        const dev = await getDevice(id);
        console.log(dev);
      } catch (error) {
        die(error);
      }
    });
}

/**
 * Retrieve a specific device
 */
async function getDevice(id: number): Promise<DeviceInfo> {
  const infoResponse = await makerFetch(`/devices/${id}`);
  const infoObj: MakerDeviceInfo = await infoResponse.json();

  const states: DeviceInfo['states'] = {};
  infoObj.attributes.forEach(attr => {
    const { name, currentValue } = attr;
    states[name] = currentValue;
  });

  const commandsResponse = await makerFetch(`/devices/${id}/commands`);
  const commandsObj: MakerCommand[] = await commandsResponse.json();
  const commands: { [name: string]: string[] } = {};
  commandsObj.forEach(cmd => {
    const name = cmd.command;
    commands[name] = cmd.type.filter(t => t != 'n/a');
  });

  return { name: infoObj.label, states, commands };
}

interface DeviceInfo {
  name: string;
  states: {
    [name: string]: string | number | null;
  };
  commands: {
    [name: string]: string[];
  };
}

interface MakerDeviceInfo {
  id: string;
  name: string;
  label: string;
  attributes: (
    | MakerNumberAttribute
    | MakerEnumAttribute
    | MakerStringAttribute)[];
  capabilities: (string | MakerCapabilityAttribute)[];
  commands: string[];
}

interface MakerAttribute<T> {
  name: string;
  currentValue: T;
}

interface MakerStringAttribute extends MakerAttribute<string | null> {
  dataType: 'STRING';
}

interface MakerNumberAttribute extends MakerAttribute<number> {
  dataType: 'NUMBER';
}

interface MakerEnumAttribute extends MakerAttribute<string> {
  dataType: 'ENUM';
  values: string[];
}

interface MakerCapabilityAttribute {
  name: string;
  dataType: null;
}

interface MakerCommand {
  command: string;
  type: string[];
}
