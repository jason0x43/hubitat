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
