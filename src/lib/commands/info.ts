import { CommanderStatic } from 'commander';
import * as cheerio from 'cheerio';
import { die } from '../common';
import { hubitatFetch } from '../request';

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
  const response = await hubitatFetch(`/device/edit/${id}`);
  if (response.status !== 200) {
    throw new Error(`Error getting device ${id}: ${response.statusText}`);
  }
  const html = await response.text();
  const $ = cheerio.load(html);
  const name = $('h1').text();

  const script = $('.panel-body script').filter((_i, script) => {
    const text = $(script).html()!;
    return text ? text.indexOf('var currentStates =') !== -1 : false;
  })[0];

  const text = $(script).html()!;
  const match = /var currentStates =\s*(\{.*?\});/.exec(text)!;
  const value = match[1];
  const obj = JSON.parse(value);
  const deviceTypeId = obj.deviceTypeId;

  const currentStates = obj.currentStates;
  const states: { [name: string]: string } = {};
  Object.keys(currentStates).forEach(key => {
    const val = currentStates[key];
    states[key] = val.dataType === 'NUMBER' ? val.jsonValue : val.value;
  });

  const commands: { [name: string]: string[] } = {};
  $('.panel-body form[action="/device/runmethod"]').each((_i, elem) => {
    const name = $(elem)
      .find('input[name="method"]')
      .attr('value');
    const cmdArgs: string[] = [];
    commands[name] = cmdArgs;

    const argTypes = $(elem).find('input[name^="argType"]');
    argTypes.each((_i, elem) => {
      cmdArgs.push($(elem).attr('value'));
    });
  });

  return { name, deviceTypeId, states, commands };
}

interface DeviceInfo {
  name: string;
  deviceTypeId: number;
  states: {
    [name: string]: string;
  };
  commands: {
    [name: string]: string[];
  };
}
