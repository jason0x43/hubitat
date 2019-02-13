import { CommanderStatic } from 'commander';
import Table from 'easy-table';

import { trim } from '../common';
import { makerFetch } from '../request';
import {
  validateType,
  ResourceType,
  Resource,
  InstalledResource,
  DeviceResource,
  CodeResource,
  getResources
} from '../resource';

// Setup cli ------------------------------------------------------------------

export default function init(program: CommanderStatic) {
  program
    .command('list <type>')
    .description(`List drivers, apps, or devices on Hubitat`)
    .action(async type => {
      try {
        const rtype = validateType(type);
        const t = new Table();

        if (rtype === 'driver') {
          const drivers = await listResources(rtype);
          drivers.forEach(driver =>
            addCodeRow(t, driver, type ? undefined : rtype)
          );
        } else if (rtype === 'app') {
          const apps = await listResources(rtype);
          apps.forEach(app => addCodeRow(t, app, type ? undefined : rtype));
        } else if (rtype === 'installedapp') {
          const apps = await listResources(rtype);
          apps.forEach(app => addInstalledRow(t, app));
        } else {
          const devices = await listDevices();
          devices.forEach(dev => addDeviceRow(t, dev));
        }

        console.log(trim(t.toString()));
      } catch (error) {
        console.error(error);
      }
    });

  function addCodeRow(t: Table, resource: CodeResource, type?: ResourceType) {
    if (type) {
      t.cell('type', type);
    }
    t.cell('id', resource.id, Table.number());
    t.cell('name', resource.name, Table.string);
    t.newRow();
  }

  function addInstalledRow(t: Table, resource: InstalledResource) {
    t.cell('id', resource.id, Table.number());
    t.cell('name', resource.name, Table.string);
    t.cell('app', resource.app, Table.string);
    t.newRow();
  }

  function addDeviceRow(t: Table, resource: DeviceResource) {
    t.cell('id', resource.id, Table.number());
    t.cell('name', resource.name, Table.string);
    t.cell('driver', resource.driver, Table.string);
    t.newRow();
  }
}

// Implementation -------------------------------------------------------------

/**
 * Get a resource list from Hubitat
 */
async function listResources(
  resource: 'installedapp'
): Promise<InstalledResource[]>;
async function listResources(resource: ResourceType): Promise<CodeResource[]>;
async function listResources(resource: ResourceType): Promise<Resource[]> {
  return await getResources(resource);
}

/**
 * Get a device list using the Maker API
 */
async function listDevices(): Promise<DeviceResource[]> {
  const devResponse = await makerFetch(`/devices`);
  const devObj: MakerDevice[] = await devResponse.json();
  return devObj.map(obj => ({
    id: Number(obj.id),
    name: obj.label,
    type: 'driver',
    driver: obj.name
  }));
}

interface MakerDevice {
  // The device ID
  id: string;
  // The device type name
  name: string;
  // The device name
  label: string;
}
