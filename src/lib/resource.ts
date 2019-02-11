import { join, relative } from 'path';
import { createHash } from 'crypto';
import * as cheerio from 'cheerio';

import { die, simpleEncode } from './common';
import { hubitatFetch } from './request';

export type CodeResourceType = 'app' | 'driver';

export const resourceDirs = {
  app: relative(process.cwd(), join(__dirname, '..', 'apps')),
  driver: relative(process.cwd(), join(__dirname, '..', 'drivers'))
};

/**
 * Get a filename for a resource
 */
export function getFilename(resource: CodeResource) {
  const { name, namespace } = resource;
  if (!name) {
    throw new Error(`Empty name for ${JSON.stringify(resource)}`);
  }
  return `${namespace}-${name!.toLowerCase().replace(/\s/g, '_')}.groovy`;
}

/**
 * Get a resource list from Hubitat
 */
export async function getFileResources(
  type: CodeResourceType
): Promise<FileResource[]> {
  const resources = await getResources(type);

  return Promise.all(
    resources.map(async res => {
      const { id } = res;
      const filename = getFilename(res);
      const item = await getResource(type, Number(id));
      const hash = hashSource(item.source);
      return { filename, hash, type, ...item };
    })
  );
}

/**
 * Retrieve a specific resource (driver or app)
 */
export async function getResource(
  type: ResourceType,
  id: number
): Promise<ResponseResource> {
  const response = await hubitatFetch(`/${type}/ajax/code?id=${id}`);
  if (response.status !== 200) {
    throw new Error(`Error getting ${type} ${id}: ${response.statusText}`);
  }
  return <Promise<ResponseResource>>response.json();
}

/**
 * Get a resource list from Hubitat
 */
export async function getResources(type: 'device'): Promise<DeviceResource[]>;
export async function getResources(
  type: 'app' | 'driver'
): Promise<CodeResource[]>;
export async function getResources(type: ResourceType): Promise<Resource[]>;
export async function getResources(type: ResourceType): Promise<Resource[]> {
  const response = await hubitatFetch(`/${type}/list`);
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
 * Generate a SHA512 hash of a source string
 */
export function hashSource(source: string) {
  const hash = createHash('sha512');
  hash.update(source);
  return hash.digest('hex');
}

/**
 * Store a specific resource (driver or app)
 */
export async function putResource(
  type: ResourceType,
  id: number,
  version: number,
  source: string
): Promise<ResponseResource> {
  const response = await hubitatFetch(`/${type}/ajax/update`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: simpleEncode({ id, version, source })
  });
  if (response.status !== 200) {
    throw new Error(`Error putting ${type} ${id}: ${response.statusText}`);
  }
  return <Promise<ResponseResource>>response.json();
}

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

export interface ResponseResource {
  id: number;
  version: number;
  source: string;
  status: string;
  errorMessage?: string;
}

export interface FileResource extends ResponseResource {
  filename: string;
  hash: string;
  type: ResourceType;
}

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
  const name = <SourceType>$(row.find('td')[1])
    .text()
    .trim()
    .split('\n')[0]
    .trim();
  const driver = $(row.find('td')[2])
    .text()
    .trim();
  const source = <SourceType>$(row.find('td')[3])
    .text()
    .trim();

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
