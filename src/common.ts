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

  return $(selector)
    .toArray()
    .reduce(
      (allResources, elem) => [
        ...allResources,
        ...processRow($, $(elem), type)
      ],
      <Resource[]>[]
    );
}

/**
 * Encode a JS object to x-www-form-urlencoded format
 */
export function simpleEncode(value: any, key?: string, list?: string[]) {
  list = list || [];
  if (typeof value === 'object') {
    for (let k in value) {
      simpleEncode(value[k], key ? `${key}[${k}]` : k, list);
    }
  } else {
    list.push(`${key}=${encodeURIComponent(value)}`);
  }
  return list.join('&');
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

export type CodeResourceType = 'app' | 'driver';

/**
 * Validate a code type argument.
 */
export function validateCodeType(type: string): CodeResourceType {
  if (/apps?/.test(type)) {
    return 'app';
  }
  if (/drivers?/.test(type)) {
    return 'driver';
  }

  die(`Invalid type "${type}"`);
  return <CodeResourceType>'';
}

/**
 * Validate an ID argument
 */
export function validateId(value?: string) {
  if (value == null) {
    return value;
  }
  const id = Number(value);
  if (isNaN(id)) {
    die('ID must be a number');
  }
  return id;
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
  if (/installed(app)?/.test(type)) {
    return 'installedapp';
  }

  die(`Invalid type "${type}"`);
  return <ResourceType>'';
}

export interface Logger {
  (...args: any[]): void;
}

export interface Resource {
  id: number;
  name: string;
  type: string;
}

export interface CodeResource extends Resource {
  namespace: string;
}

export interface InstalledResource extends Resource {
  app: string;
}

export interface DeviceResource extends Resource {
  source: SourceType;
  driver: string;
}

export type SourceType = 'System' | 'User';

export type ResourceType = 'driver' | 'app' | 'device' | 'installedapp';

function processCodeRow(
  $: CheerioStatic,
  row: Cheerio,
  type: ResourceType
): CodeResource[] {
  const id = Number(row.data(`app-id`));
  const link = $(row.find('td')[0]).find('a');
  const name = link.text().trim();
  const namespace = $(row.find('td')[1])
    .text()
    .trim();

  if (!id || !name || !namespace) {
    throw new Error(`Invalid row: ${row}`);
  }

  return [
    {
      id,
      type,
      name: name!,
      namespace: namespace!
    }
  ];
}

function processDeviceRow(
  $: CheerioStatic,
  row: Cheerio,
  type: ResourceType
): DeviceResource[] {
  const id = Number(row.data(`${type}-id`));
  const link = $(row.find('td')[0]).find('a');
  const driver = <SourceType>$(row.find('td')[1])
    .text()
    .trim();
  const source = <SourceType>$(row.find('td')[2])
    .text()
    .trim();
  const name = link.text().trim();

  return [
    {
      id,
      driver,
      type,
      source,
      name: name
    }
  ];
}

function processInstalledRow(
  $: CheerioStatic,
  row: Cheerio,
  type: ResourceType
): InstalledResource[] {
  const apps: InstalledResource[] = [];

  const nameCell = $(row.find('td')[0]);
  const names = nameCell.find('a:first-child');
  const typeCell = $(row.find('td')[1]);
  const types = typeCell.find('div,li');
  names.each((i, link) => {
    const name = $(link)
      .text()
      .trim();
    const id = Number(
      $(link)
        .attr('href')
        .split('/')[3]
    );
    const app = $(types[i])
      .text()
      .trim();
    apps.push({ name, id, app, type });
  });

  return apps;
}

const tableSelectors = {
  app: '#hubitapps-table tbody .app-row',
  installedapp: '#app-table tbody .app-row',
  driver: '#devicetype-table tbody .driver-row',
  device: '#device-table tbody .device-row'
};
const rowProcessors = {
  app: processCodeRow,
  driver: processCodeRow,
  device: processDeviceRow,
  installedapp: processInstalledRow
};
