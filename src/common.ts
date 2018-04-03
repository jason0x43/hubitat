import { CommanderStatic } from 'commander';
import fetch from 'node-fetch';
import * as cheerio from 'cheerio';

export interface Context {
  hubitatHost: string;
  program: CommanderStatic;
}

/**
 * Display a message and quit
 */
export function die(message: string | Error) {
  if (typeof message === 'string') {
    console.error(`\n  error: ${message}\n`);
  } else {
    const lines = message.stack!.split('\n');
    console.error(`\n  ${lines.join('\n  ')}\n`);
  }
  process.exit(1);
}

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

/**
 * Verify that a type variable is a ResourceType
 */
export function validateType(type: string): ResourceType {
  if (/apps?/.test(type)) {
    return 'app';
  }
  if (/drivers?/.test(type)) {
    return 'driver';
  }
  if (/devices?/.test(type)) {
    return 'device';
  }

  die(`Invalid type "${type}"`);
  return <ResourceType>'';
}

export type ResourceType = 'driver' | 'app' | 'device';

/**
 * Get a resource list from Hubitat
 */
export async function getResources(
  hubitatHost: string,
  type: 'device'
): Promise<DeviceResource[]>;
export async function getResources(
  hubitatHost: string,
  type: 'app' | 'driver'
): Promise<CodeResource[]>;
export async function getResources(
  hubitatHost: string,
  type: ResourceType
): Promise<Resource[]>;
export async function getResources(
  hubitatHost: string,
  type: ResourceType
): Promise<Resource[]> {
  const response = await fetch(`http://${hubitatHost}/${type}/list`);
  const html = await response.text();
  const $ = cheerio.load(html);
  const selector = tableSelectors[type];
  const processRow = rowProcessors[type];

  const rows = $(selector).toArray();
  return Promise.all(
    rows.map<Promise<Resource>>(async elem => {
      return processRow($, $(elem), type);
    })
  );
}

/**
 * Trim whitespace from either end of a string
 */
export function trim(str?: string) {
  if (!str) {
    return str;
  }
  return str.replace(/^\s+/, '').replace(/\s+$/, '');
}

export interface Logger {
  (...args: any[]): void;
}

export interface Resource {
  id: string;
  name: string;
  type: string;
}

export interface CodeResource extends Resource {
  namespace: string;
}

export interface DeviceResource extends Resource {
  source: SourceType;
  driver: string;
}

export type SourceType = 'System' | 'User';

function processCodeRow(
  $: CheerioStatic,
  row: Cheerio,
  type: ResourceType
): CodeResource {
  const id = <string>row.data(`${type}-id`);
  if (!id) {
    throw new Error(`No ID in row ${row.text()}`);
  }

  const link = $(row.find('td')[0]).find('a');
  const text = type === 'app' ? link.attr('title') : link.text();
  const [namespace, name] = text.split(':').map(trim);

  return {
    id,
    type,
    name: name!,
    namespace: namespace!
  };
}

function processDeviceRow(
  $: CheerioStatic,
  row: Cheerio,
  type: ResourceType
): DeviceResource {
  const id = <string>row.data(`${type}-id`);
  if (!id) {
    throw new Error(`No ID in row ${row.text()}`);
  }

  const link = $(row.find('td')[0]).find('a');
  const driver = <SourceType>$(row.find('td')[1]).text();
  const source = <SourceType>$(row.find('td')[2]).text();
  const name = link.text();

  return {
    id,
    driver,
    type,
    source,
    name: name
  };
}

const tableSelectors = {
  app: '#hubitapps-table tbody .app-row',
  driver: '#devicetype-table tbody .device-row',
  device: '#device-table tbody .device-row'
};
const rowProcessors = {
  app: processCodeRow,
  driver: processCodeRow,
  device: processDeviceRow
};
