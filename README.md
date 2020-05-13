# Hubitat stuff

This repository contains the local drivers and apps that are currently loaded
onto my Hubitat, along with a script to interact with the Hubitat. Some of the
apps and drivers are my own, some are from other sources, and a few are ones
from other sources that I cleaned up and/or modified.

## WeMo support

This repo contains an application and drivers supporting several WeMo devices:

- Dimmer
- Insight Switch
- Switch
- Motion
- Maker (still in development)

To use the WeMo drivers:

1. Add `apps/jason0x43-wemo_connect.groovy` to Apps Code
2. Add all of the `drivers/jason0x43-wemo_*.groovy` files (or at least the ones
   for your devices) to Drivers Code
3. In Apps, click Add User App and select WeMo Connect
4. In the Device Discovery page, wait for the app to detect any WeMo devices on
   your network. This can take anywhere from a few seconds to a couple of
   minutes.
5. Select the WeMo devices you want to use with Hubitat, and click the Done
   button at the bottom of the page.

After clicking Done, new Hubitat devices will be created for each of the
selected WeMo devices. By default their labels will be the same as their WeMo
names, and their types will start with Wemo.

## Nest Integration

This repo contains an integration app and driver to control Nest thermostats.
See the [Nest Integration app source](./apps/jason0x43-nest.groovy) for setup
instructions.

## hubitat script

The `hubitat` application is TypeScript application that allows some degree of
interaction with the Hubitat through the command line. The `hubitat` script in
the root of this repo is a small bash script that will run the `hubitat`
application, rebuilding it as necessary.

At the moment, `hubitat` supports 4 commands:

- **list** - List apps, devices, or drivers
- **log** - Log messages emitted by apps or devices
- **pull** - Pull code from the hubitat into the local repo
- **push** - Push code from the local repo to the hubitat

# License

Unless otherwise stated in a given file, everything in here uses the
[The Unlicense](./LICENSE).
