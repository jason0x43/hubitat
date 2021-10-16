/**
 * WeMo Connect
 *
 * Author: Jason Cheatham
 * Last updated: 2021-03-21, 17:07:01-0400
 *
 * Based on the original Wemo (Connect) Advanced app by SmartThings, updated by
 * superuser-ule 2016-02-24
 *
 * Original Copyright 2015 SmartThings
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

definition(
    name: 'WeMo Connect',
    namespace: 'jason0x43',
    author: 'Jason Cheatham',

    description: 'Allows you to integrate your WeMo devices with Hubitat.',
    singleInstance: true,
    iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Partner/wemo.png',
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Partner/wemo@2x.png',
    importUrl: 'https://raw.githubusercontent.com/jason0x43/hubitat/master/apps/jason0x43-wemo_connect.groovy'
)

preferences {
    page(name: 'mainPage')
}

import hubitat.helper.HexUtils

def mainPage() {
    debugLog("mainPage: Rendering main with state: ${state}")

    // Reset the refresh state if the last refresh was more than 60 seconds ago
    if (
        !state.refreshCount
        || !state.lastRefresh
        || (now() - state.lastRefresh) > 60000
    ) {
        debugLog("mainPage: Resetting refresh count and discovered devices")
        state.refreshCount = 0
        state.discoveredDevices = [:]
    }

    state.minDriverVersion = 4

    def refreshCount = state.refreshCount

    state.refreshCount = refreshCount + 1
    state.lastRefresh = now()

    // ssdp request every 30 seconds
    discoverAllWemoTypes()

    def devices = getKnownDevices()
    debugLog("mainPage: Known devices: ${devices}")
    def deviceLabels = [:]
    devices.each { mac, data -> deviceLabels[mac] = data.label }

    dynamicPage(
        name: 'mainPage',
        install: true,
        uninstall: true,
        refreshInterval: 30
    ) {
        section('<h2>Device Discovery</h2>') {
            paragraph(
                'Device discovery messages are being broadcast every 30 ' +
                'seconds. Any devices on the local network should show up ' +
                'within a minute or two.'
            )

            input(
                'selectedDevices',
                'enum',
                required: false,
                title: "Select Wemo Devices \n(${devices.size() ?: 0} found)",
                multiple: true,
                options: deviceLabels
            )
        }

        section('<h2>Options</h2>') {
            input(
                'interval',
                'number',
                title: 'How often should WeMo devices be refreshed? ' +
                    '(minutes, default is 5, max is 59)',
                defaultValue: 5
            )

            input(
                'debugLogging',
                'bool',
                title: 'Enable debug logging',
                defaultValue: false,
                submitOnChange: true
            )
        }
    }
}

def installed() {
    log.info('Installed')
    initialize()
}

def updated() {
    log.info('Updated')
    initialize()
}

def uninstalled() {
    log.info('Uninstalling')
    // Remove any child devices created by this app
    getChildDevices().each { device ->
        log.info("Removing child device ${device}")
        deleteChildDevice(device.deviceNetworkId)
    }
}

def initialize() {
    log.info('Initializing')
    unschedule()
    if (selectedDevices) {
        initDevices()
    }

    def interval = Math.min(settings.interval ?: 5, 59)

    // cron fields:
    //   seconds
    //   minutes
    //   hours
    //   day of month
    //   month
    //   day of week
    //   year
    debugLog("initialize: scheduling discovery for every ${interval} minutes")
    schedule("0 0/${interval} * * * ?", refreshDevices)
}

def refreshDevices() {
    log.info('Refreshing Wemo devices')
    getChildDevices().each { device -> device.refresh() }
    discoverAllWemoTypes()
}

def childGetHostAddress(device) {
    debugLog("childGetHostAddress: getting address for ${device}")
    def hexIp = device.getDataValue('ip')
    def hexPort = device.getDataValue('port')
    debugLog("childGetHostAddress: hexIp = ${hexIp}")
    debugLog("childGetHostAddress: hexPort = ${hexPort}")
    try {
        return toDecimalAddress("${hexIp}:${hexPort}")
    } catch (Throwable t) {
        log.warn("Error parsing child address: $t");
        return null
    }
}

def childGetBinaryState(child) {
    log.info("Getting state for ${child}")
    debugLog(
        "childGetBinaryState: sending request to ${childGetHostAddress(child)}"
    )
    new hubitat.device.HubSoapAction(
        path: '/upnp/control/basicevent1',
        urn: 'urn:Belkin:service:basicevent:1',
        action: 'GetBinaryState',
        headers: [
            HOST: childGetHostAddress(child)

        ]
    )
}

def childResubscribe(child) {
    log.info("Resubscribing ${child}")
    def sid = child.getDataValue('subscriptionId')
    if (sid == null) {
        debugLog(
            "childResubscribe: No existing subscription for ${child} -- " +
            "subscribing"
        )
        return childSubscribe(child)
    }

    debugLog("childResubscribe: renewing ${child} subscription to ${sid}")

    // Clear the existing SID -- it should be set if the resubscribe succeeds
    debugLog('childReubscribe: clearing existing sid')
    child.updateDataValue('subscriptionId', null)

    debugLog(
        "childResubscribe: sending request to ${childGetHostAddress(child)}"
    )
    new hubitat.device.HubAction([
        method: 'SUBSCRIBE',
        path: '/upnp/event/basicevent1',
        headers: [
            HOST: childGetHostAddress(child),
            TIMEOUT: "Second-${getSubscriptionTimeout()}",
            SID: "uuid:${sid}"
        ]
    ], child.deviceNetworkId)
}

def childSetBinaryState(child, state, brightness = null) {
    log.info("Setting binary state for ${child}")
    def body = [ BinaryState: "$state" ]

    if (brightness != null) {
        body.brightness = "$brightness"
    }

    debugLog(
        "childSetBinaryState: sending binary state request to " +
        "${childGetHostAddress(child)}"
    )
    new hubitat.device.HubSoapAction(
        path: '/upnp/control/basicevent1',
        urn: 'urn:Belkin:service:basicevent:1',
        action: 'SetBinaryState',
        body: body,
        headers: [
            Host: childGetHostAddress(child)
        ]
    )
}

def childSubscribe(child) {
    log.info("Subscribing to events for ${child}")

    // Clear out any current subscription ID; will be reset when the
    // subscription completes
    debugLog('childSubscribe: clearing existing sid')
    child.updateDataValue('subscriptionId', '')

    debugLog(
        "childSubscribe: sending subscribe request to " +
        "${childGetHostAddress(child)}"
    )
    new hubitat.device.HubAction([
        method: 'SUBSCRIBE',
        path: '/upnp/event/basicevent1',
        headers: [
            HOST: childGetHostAddress(child),
            CALLBACK: "<http://${getCallbackAddress()}/>",
            NT: 'upnp:event',
            TIMEOUT: "Second-${getSubscriptionTimeout()}"
        ]
    ], child.deviceNetworkId)
}

def childSubscribeIfNecessary(child) {
    debugLog("childSubscribeIfNecessary: checking subscription for ${child}")

    def sid = child.getDataValue('subscriptionId')
    if (sid == null) {
        debugLog(
            "childSubscribeIfNecessary: no active subscription -- subscribing"
        )
        childSubscribe(child)
    } else {
        debugLog("childSubscribeIfNecessary: active subscription -- skipping")
    }
}

def childSyncTime(child) {
    debugLog("childSyncTime: requesting sync for ${child}")

    def now = new Date();
    def tz = location.timeZone;
    def offset = tz.getOffset(now.getTime())
    def offsetHours = (offset / 1000 / 60 / 60).intValue()
    def tzOffset = (offsetHours < 0 ? '-' : '') +
        String.format('%02d.00', Math.abs(offsetHours))
    def isDst = tz.inDaylightTime(now)
    def hasDst = tz.observesDaylightTime()

    debugLog(
        "childSyncTime: sending sync request to ${childGetHostAddress(child)}"
    )
    new hubitat.device.HubSoapAction(
        path: '/upnp/control/timesync1',
        url: 'urn:Belkin:service:timesync:1',
        action: 'TimeSync',
        body: [
            UTC: getTime(),
            TimeZone: tzOffset,
            dst: isDst ? 1 : 0,
            DstSupported: hasDst ? 1 : 0
        ],
        headers: [
            HOST: childGetHostAddress(child)
        ]
    )
}

def childUnsubscribe(child) {
    debugLog("childUnsubscribe: unsubscribing ${child}")

    def sid = child.getDataValue('subscriptionId')

    // Clear out the current subscription ID
    debugLog('childUnsubscribe: clearing existing sid')
    child.updateDataValue('subscriptionId', '')

    debugLog(
        "childUnsubscribe: sending unsubscribe request to " +
        "${childGetHostAddress(child)}"
    )
    new hubitat.device.HubAction([
        method: 'UNSUBSCRIBE',
        path: '/upnp/event/basicevent1',
        headers: [
            HOST: childGetHostAddress(child),
            SID: "uuid:${sid}"
        ]
    ], child.deviceNetworkId)
}

def childUpdateSubscription(message, child) {
    def headerString = message.header

    if (isSubscriptionHeader(headerString)) {
        def sid = getSubscriptionId(headerString)
        debugLog(
            "childUpdateSubscription: updating subscriptionId for ${child} " +
            "to ${sid}"
        )
        child.updateDataValue('subscriptionId', sid)
    }
}

def getSubscriptionId(header) {
    def sid = (header =~ /SID: uuid:.*/) ?
        (header =~ /SID: uuid:.*/)[0] :
        '0'
    sid -= 'SID: uuid:'.trim()
    return sid;
}

def getSubscriptionTimeout() {
    return 60 * (settings.interval ?: 5)
}

def getTime() {
    // This is essentially System.currentTimeMillis()/1000, but System is
    // disallowed by the sandbox.
    ((new GregorianCalendar().time.time / 1000l).toInteger()).toString()
}

/**
 * Handle the setup.xml data for a device
 *
 * The device descriptor in body.device should match up, more or less, with
 * the device descriptor returned by parseDiscoveryMessage.
 */
def handleSetupXml(response) {
    def body = response.xml
    def device = body.device
    def deviceType = "${device.deviceType}"
    def friendlyName = "${device.friendlyName}"

    debugLog(
        "handleSetupXml: Handling setup.xml for ${deviceType}" +
        " (friendly name is '${friendlyName}')"
    )

    if (
        deviceType.startsWith('urn:Belkin:device:controllee:1') ||
        deviceType.startsWith('urn:Belkin:device:insight:1') ||
        deviceType.startsWith('urn:Belkin:device:Maker:1') ||
        deviceType.startsWith('urn:Belkin:device:sensor') ||
        deviceType.startsWith('urn:Belkin:device:lightswitch') ||
        deviceType.startsWith('urn:Belkin:device:dimmer')
    ) {
        def entry = getDiscoveredDevices().find {
            it.key.contains("${device.UDN}")
        }

        if (entry) {
            def dev = entry.value
            debugLog("handleSetupXml: updating ${dev}")
            dev.name = friendlyName
            dev.verified = true
        } else {
            log.error("/setup.xml returned a wemo device that doesn't exist")
        }
    }
}

def handleSsdpEvent(evt) {
    def description = evt.description
    def hub = evt?.hubId

    def parsedEvent = parseDiscoveryMessage(description)
    parsedEvent << ['hub': hub]
    debugLog("handleSsdpEvent: Parsed discovery message: ${parsedEvent}")

    def usn = parsedEvent.ssdpUSN.toString()
    def device = getDiscoveredDevice(usn)

    if (device) {
        debugLog("handleSsdpEvent: Found cached device data for ${usn}")

        // Ensure the cached ip and port agree with what's in the discovery
        // event
        device.ip = parsedEvent.ip
        device.port = parsedEvent.port

        def child = getChildDevice(device.mac)
        if (child != null) {
            debugLog(
                "handleSsdpEvent: Updating IP address for" +
                " ${child} [${device.mac}]"
            )
            updateChildAddress(child, device.ip, device.port)
        }
    } else {
        debugLog(
            "handleSsdpEvent: Adding ${parsedEvent.mac} to list of" +
            " known devices"
        )
        def id = parsedEvent.ssdpUSN.toString()
        device = parsedEvent
        state.discoveredDevices[id] = device
    }

    if (!device.verified) {
        debugLog("handleSsdpEvent: Verifying ${device}")
        getSetupXml("${device.ip}:${device.port}")
    }
}

private hexToInt(hex) {
    Integer.parseInt(hex, 16)
}

private hexToIp(hex) {
    [
        hexToInt(hex[0..1]),
        hexToInt(hex[2..3]),
        hexToInt(hex[4..5]),
        hexToInt(hex[6..7])
    ].join('.')
}

private debugLog(message) {
    if (settings.debugLogging) {
        log.debug message
    }
}

private discoverAllWemoTypes() {
    def targets = [
        'urn:Belkin:device:insight:1',
        'urn:Belkin:device:Maker:1',
        'urn:Belkin:device:controllee:1',
        'urn:Belkin:device:sensor:1',
        'urn:Belkin:device:lightswitch:1',
        'urn:Belkin:device:dimmer:1'
    ]

    def targetStr = "${targets}"

    if (state.subscribed != targetStr) {
        targets.each { target ->
            subscribe(location, "ssdpTerm.${target}", handleSsdpEvent)
            debugLog('discoverAllWemoTypes: subscribed to ' + target)
        }
        state.subscribed = targetStr
    }

    debugLog("discoverAllWemoTypes: Sending discovery message for ${targets}")
    sendHubCommand(
        new hubitat.device.HubAction(
            "lan discovery ${targets.join('/')}",
            hubitat.device.Protocol.LAN
        )
    )
}

private getCallbackAddress() {
    def hub = location.hubs[0];
    def localIp = hub.getDataValue('localIP')
    def localPort = hub.getDataValue('localSrvPortTCP')
    "${localIp}:${localPort}"
}

private getKnownDevices() {
    debugLog('getKnownDevices: Creating list of known devices')

    // Known devices are a combination of existing (child) devices and newly
    // discovered devices.
    def knownDevices = [:]

    // First, populate the known devices list with existing devices
    def existingDevices = getChildDevices()
    existingDevices.each { device ->
        def mac = device.deviceNetworkId
        def name = device.label ?: device.name
        knownDevices[mac] = [
            mac: mac,
            name: name,
            ip: device.getDataValue('ip'),
            port: device.getDataValue('port'),
            typeName: device.typeName,
            needsUpdate: device.getDriverVersion() < state.minDriverVersion
        ]
        debugLog(
            "getKnownDevices: Added already-installed device ${mac}:${name}"
        )
    }

    // Next, populate the list with verified devices (those from which a
    // setup.xml has been retrieved).
    def verifiedDevices = getDiscoveredDevices(true)
    debugLog("getKnownDevices: verified devices: ${verifiedDevices}")
    verifiedDevices.each { key, device ->
        def mac = device.mac
        if (knownDevices.containsKey(mac)) {
            def knownDevice = knownDevices[mac]
            // If there's a verified device corresponding to an already-
            // installed device, update the installed device's name based
            // on the name of the verified device.
            def name = device.name
            if (name != null && name != knownDevice.name) {
                knownDevice.name =
                    "${name} (installed as ${knownDevice.name})"
                debugLog("getKnownDevices: Updated name for ${mac} to ${name}")
            }
        } else {
            def name = device.name ?:
                "WeMo device ${device.ssdpUSN.split(':')[1][-3..-1]}"
            knownDevices[mac] = [
                mac: mac,
                name: name,
                ip: device.ip,
                port: device.port,
                // The ssdpTerm and hub will be used if a new child device is
                // created
                ssdpTerm: device.ssdpTerm,
                hub: device.hub
            ]
            debugLog(
                "getKnownDevices: Added discovered device ${knownDevices[mac]}"
            )
        }
    }

    debugLog("getKnownDevices: Known devices: ${knownDevices}")

    knownDevices.each { mac, device ->
        def address
        try {
            address = "${hexToIp(device.ip)}:${hexToInt(device.port)}"
        } catch (Throwable t) {
            address = "<unknown>"
            log.warn("Error parsing device address: $t");
        }

        def text = "${device.name} [MAC: ${mac}, IP: ${address}"

        if (device.typeName) {
            def needsUpdate = device.needsUpdate
                ? '&nbsp;&nbsp;<< Driver needs update >>'
                : ''
            text += ", Driver: ${device.typeName}] ${needsUpdate}"
        } else {
            text += ']'
        }
        knownDevices[mac].label = "<li>${text}</li>"
    }

    return knownDevices
}

private getSetupXml(hexIpAddress) {
    def hostAddress
    try {
        hostAddress = toDecimalAddress(hexIpAddress)
    } catch (Throwable t) {
        log.warn("Error parsing address ${hexIpAddress}: $t")
        return
    }

    debugLog("getSetupXml: requesting setup.xml from ${hostAddress}")
    sendHubCommand(
        new hubitat.device.HubAction(
            [
                method: 'GET',
                path: '/setup.xml',
                headers: [ HOST: hostAddress ],
            ],
            null,
            [ callback: handleSetupXml ]
        )
    )
}

private getDiscoveredDevices(isVerified = false) {
    debugLog(
        "getDiscoveredDevices: Getting discovered" +
        "${isVerified ? ' and verified' : ''} devices"
    )

    if (!state.discoveredDevices) {
        state.discoveredDevices = [:]
    }

    if (isVerified) {
        debugLog(
            "getDiscoveredDevices: Finding verified devices in " +
            "${state.discoveredDevices}"
        )
        return state.discoveredDevices.findAll { it.value?.verified }
    }
    return state.discoveredDevices
}

private getDiscoveredDevice(usn) {
    debugLog("getDiscoveredDevice: Getting discovered device with USN ${usn}")
    return getDiscoveredDevices()[usn]
}

private initDevices() {
    debugLog('initDevices: Initializing devices')

    def knownDevices = getKnownDevices()

    selectedDevices.each { dni ->
        debugLog(
            "initDevices: Looking for selected device ${dni} in known " +
            "devices..."
        )

        def selectedDevice = knownDevices[dni]

        if (selectedDevice) {
            debugLog(
                "initDevices: Found device; looking for existing child with " +
                "dni ${dni}"
            )
            def child = getChildDevice(dni)

            if (!child) {
                def driverName
                def namespace = 'jason0x43'
                debugLog(
                    "initDevices: Creating WeMo device for ${selectedDevice}"
                )

                switch (selectedDevice.ssdpTerm) {
                    case ~/.*insight.*/:
                        driverName = 'Wemo Insight Switch'
                        break

                    // The Light Switch and Switch use the same driver
                    case ~/.*lightswitch.*/:
                    case ~/.*controllee.*/:
                        driverName = 'Wemo Switch'
                        break

                    case ~/.*sensor.*/:
                        driverName = 'Wemo Motion'
                        break

                    case ~/.*dimmer.*/:
                        driverName = 'Wemo Dimmer'
                        break

                    case ~/.*Maker.*/:
                        driverName = 'Wemo Maker'
                        break
                }

                if (driverName) {
                    child = addChildDevice(
                        namespace,
                        driverName,
                        selectedDevice.mac,
                        selectedDevice.hub,
                        [
                            'label':  selectedDevice.name ?: 'Wemo Device',
                            'data': [
                                'mac': selectedDevice.mac,
                                'ip': selectedDevice.ip,
                                'port': selectedDevice.port
                            ]
                        ]
                    )
                    log.info(
                        "initDevices: Created ${child.displayName} with id: " +
                        "${child.id}, MAC: ${child.deviceNetworkId}"
                    )
                } else {
                    log.warn("initDevices: No driver for ${selectedDevice})")
                }
            } else {
                debugLog(
                    "initDevices: Updating IP address for ${child}"
                )
                updateChildAddress(
                    child,
                    selectedDevice.ip,
                    selectedDevice.port
                )
            }

            if (child) {
                debugLog('initDevices: Setting up device subscription...')
                child.refresh()
            }
        } else {
            log.warn(
                "initDevices: Could not find device ${dni} in ${knownDevices}"
            )
        }
    }
}

private isSubscriptionHeader(header) {
    if (header == null) {
        return false;
    }
    header.contains("SID: uuid:") && header.contains('TIMEOUT:');
}

/**
 * Parse a discovery message, returning a device descriptor
 */
private parseDiscoveryMessage(description) {
    def device = [:]
    def parts = description.split(',')

    debugLog("parseDiscoveryMessage: Parsing discovery message: $description")

    parts.each { part ->
        part = part.trim()
        def valueStr;

        switch (part) {
            case { it .startsWith('devicetype:') }:
                valueString = part.split(':')[1].trim()
                device.deviceType = valueString
                break
            case { it.startsWith('mac:') }:
                valueString = part.split(':')[1].trim()
                if (valueString) {
                    device.mac = valueString
                }
                break
            case { it.startsWith('networkAddress:') }:
                valueString = part.split(':')[1].trim()
                if (valueString) {
                    device.ip = valueString
                }
                break
            case { it.startsWith('deviceAddress:') }:
                valueString = part.split(':')[1].trim()
                if (valueString) {
                    device.port = valueString
                }
                break
            case { it.startsWith('ssdpPath:') }:
                valueString = part.split(':')[1].trim()
                if (valueString) {
                    device.ssdpPath = valueString
                }
                break
            case { it.startsWith('ssdpUSN:') }:
                part -= 'ssdpUSN:'
                valueString = part.split('::')[0].trim()
                if (valueString) {
                    device.ssdpUSN = valueString
                }
                break
            case { it.startsWith('ssdpTerm:') }:
                part -= 'ssdpTerm:'
                valueString = part.trim()
                if (valueString) {
                    device.ssdpTerm = valueString
                }
                break
            case { it.startsWith('headers:') }:
                part -= 'headers:'
                valueString = part.trim()
                if (valueString) {
                    device.headers = valueString
                }
                break
            case { it.startsWith('body:') }:
                part -= 'body:'
                valueString = part.trim()
                if (valueString) {
                    device.body = valueString
                }
                break
        }
    }

    device
}

private toDecimalAddress(address) {
    debugLog("toDecimalAddress: converting ${address}")
    def parts = address.split(':')
    ip = parts[0]
    port = parts[1]
    "${hexToIp(ip)}:${hexToInt(port)}"
}

private updateChildAddress(child, ip, port) {
    debugLog(
        "updateChildAddress: Updating address of ${child} to ${ip}:${port}"
    )
    def address = "${ip}:${port}"

    def decimalAddress
    try {
        decimalAddress = toDecimalAddress(address)
    } catch (Throwable t) {
        log.warn("Error parsing address ${address}: $t")
        return
    }

    log.info(
        "Verifying that IP for ${child} is set to ${decimalAddress}"
    )

    def existingIp = child.getDataValue('ip')
    if (ip && existingIp && ip != existingIp) {
        try {
            debugLog(
                "childSync: Updating IP from ${hexToIp(existingIp)} to " +
                "${hexToIp(ip)}"
            )
        } catch (Throwable t) {
            log.warn("Error parsing addresses $existingIp, $ip: $t")
            debugLog("childSync: Updating IP from ${existingIp} to ${ip}")
        }
        child.updateDataValue('ip', ip)
    }

    def existingPort = child.getDataValue('port')
    if (port != null && existingPort != null && port != existingPort) {
        try {
            debugLog(
                "childSync: Updating port from ${hexToInt(existingPort)} to " +
                "${hexToInt(port)}"
            )
        } catch (Throwable t) {
            log.warn("Error parsing ports $existingPort, $port: $t")
            debugLog(
                "childSync: Updating port from ${existingPort} to ${port}"
            )
        }
        child.updateDataValue('port', port)
    }

    childSubscribe(child)
}
