import {
  Context,
  validateType,
  ResourceType,
  Resource,
  DeviceResource,
  CodeResource,
  getResources,
  trim
} from './common';
import { CommanderStatic } from 'commander';

const Table = require('easy-table');

let program: CommanderStatic;
let hubitatHost: string;

// Setup cli ------------------------------------------------------------------

export default function init(context: Context) {
  program = context.program;
  hubitatHost = context.hubitatHost;

  program
    .command('list <type>')
    .description(`List drivers, apps, or devices on Hubitat`)
    .action(async type => {
      const rtype = validateType(type);
      const t = new Table();

      if (rtype == 'driver') {
        const drivers = await listResources('driver');
        drivers.forEach(driver => {
          addCodeRow(t, driver, type ? undefined : 'driver');
        });
      } else if (rtype == 'app') {
        const apps = await listResources('app');
        apps.forEach(app => {
          addCodeRow(t, app, type ? undefined : 'app');
        });
      } else {
        const devices = await listResources('device');
        devices.forEach(dev => {
          addDeviceRow(t, dev);
        });
      }

      console.log(trim(t.toString()));
    });

  function addCodeRow(
    t: typeof Table,
    resource: CodeResource,
    type?: ResourceType
  ) {
    if (type) {
      t.cell('type', type);
    }
    t.cell('id', resource.id, Table.number());
    t.cell('name', resource.name, Table.string());
    t.newRow();
  }

  function addDeviceRow(t: typeof Table, resource: DeviceResource) {
    t.cell('id', resource.id, Table.number());
    t.cell('name', resource.name, Table.string());
    t.cell('driver', resource.driver, Table.string());
    t.newRow();
  }
}

// Implementation -------------------------------------------------------------

/**
 * Get a resource list from Hubitat
 */
async function listResources(resource: 'device'): Promise<DeviceResource[]>;
async function listResources(
  resource: 'app' | 'driver'
): Promise<CodeResource[]>;
async function listResources(resource: ResourceType): Promise<Resource[]> {
  const resources = await getResources(hubitatHost, resource);
  return resources;
}
