import { CommanderStatic } from 'commander';
import Table from 'easy-table';
import { die, trim } from '../common';
import { hubitatFetch } from '../request';

// Setup cli ------------------------------------------------------------------

export default function init(program: CommanderStatic) {
  program
    .command('events <id>')
    .option('-f, --filter <text>', 'Filter on a string')
    .option('-l, --length <n>', 'How many records to retrieve (10)', parseInt)
    .description('Get events for a specific device')
    .action(async (id, cmd) => {
      try {
        const events = await getDeviceEvents(id, cmd);

        const t = new Table();
        events.forEach(event => {
          t.cell('date', event.date, formatTimestamp);
          t.cell('name', event.name, Table.string);
          t.cell('value', event.value, Table.string);
          t.newRow();
        });

        console.log(trim(t.toString()));
      } catch (error) {
        die(error);
      }
    });
}

interface Column {
  name: string;
  searchable: boolean;
  orderable: boolean;
}

/**
 * Retrieve a specific device
 */
async function getDeviceEvents(
  id: string,
  options: DeviceQueryOptions = {}
): Promise<DeviceEvent[]> {
  const columns: Column[] = [
    // {
    //   name: 'ID',
    //   searchable: false,
    //   orderable: true
    // },
    {
      name: 'NAME',
      searchable: true,
      orderable: true
    },
    {
      name: 'VALUE',
      searchable: true,
      orderable: true
    },
    // {
    //   name: 'UNIT',
    //   searchable: true,
    //   orderable: true
    // },
    // {
    //   name: 'DESCRIPTION_TEXT',
    //   searchable: true,
    //   orderable: true
    // },
    // {
    //   name: 'SOURCE',
    //   searchable: true,
    //   orderable: true
    // },
    // {
    //   name: 'EVENT_TYPE',
    //   searchable: true,
    //   orderable: true
    // },
    {
      name: 'DATE',
      searchable: true,
      orderable: true
    }
  ];

  const params = new URLSearchParams([
    ['_', String(Date.now())],

    ['draw', '1'],
    ['order[0][column]', String(columns.length - 1)],
    ['order[0][dir]', 'desc'],
    ['start', '0'],
    ['length', String(options.length || 10)],
    ['search[value]', options.filter || ''],
    ['search[regex]', 'false']
  ]);

  columns.forEach((column, i) => {
    params.set(`columns[${i}][data]`, String(i));
    Object.keys(column).forEach(key => {
      const colKey = <keyof Column>key;
      params.set(`columns[${i}][${key}]`, String(column[colKey]));
    });
  });

  const eventResponse = await hubitatFetch(
    `/device/events/${id}/dataTablesJson?${params.toString()}`
  );
  const eventObjs: DeviceEventsResponse = await eventResponse.json();

  return eventObjs.data.map(event => {
    const [id, name, value, unit, , source, , date] = event;
    return {
      id,
      name,
      value,
      unit,
      source,
      date: new Date(date)
    };
  });
}

function formatTimestamp(time: Date, _width: number) {
  const year = time.getFullYear();
  const month = padValue(time.getMonth() + 1);
  const day = padValue(time.getDate());
  const hour = padValue(time.getHours());
  const minute = padValue(time.getMinutes());
  const second = padValue(time.getSeconds());
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
}

function padValue(num: number) {
  return String(num).padStart(2, '0');
}

interface DeviceEvent {
  id: number;
  name: string;
  value: string;
  unit: string | null;
  date: Date;
  source: string;
}

interface DeviceEventsResponse {
  draw: number;
  recordsTotal: number;
  recordsFiltered: number;
  data: [number, string, string, null, string, string, string, string][];
}

interface DeviceQueryOptions {
  filter?: string;
  length?: number
}
