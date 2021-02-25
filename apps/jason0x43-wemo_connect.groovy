/**
 * WeMo Connect
 *
 * Author: Jason Cheatham
 * Last updated: 2021-02-24, 21:14:00-0500
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
        initDiscoveredDevices()
    }

    state.minDriverVersion = 3

    def refreshCount = state.refreshCount

    state.refreshCount = refreshCount + 1
    state.lastRefresh = now()

    // ssdp request every 30 seconds
    discoverAllWemoTypes()

    def devices = getKnownDevices()
    debugLog("mainPage: Known devices: ${devices}")
    def deviceLabels = [:]
    devices.each { mac, data -> deviceLabels[mac] = data.label }
    
    debugLog("mainPage: manualAddress = ${manualAddress}")
    def addressIsValid = true
    if (manualAddress) {
        def hexAddress = toHexAddress(manualAddress)
        if (!hexAddress) {
            addressIsValid = false;
        } else {
            try {
                getSetupXml(hexAddress)
            } catch (Throwable t) {
                log.error(t)
                addressIsValid = false
            }
        }
    }

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

        section('<h2>Manually add device</h2>') {
            paragraph(
                'Provide an address in the format 192.168.0.10:12345 to ' +
                'manually add a device. If a device is discovered, it will ' +
                'show up in the discovered devices list above.'
            )
            
            if (!addressIsValid) {
                paragraph(
                    'The address was invalid or the device was unreachable. ' +
                    'Please try again.'
                )
            }
            
            input(
                'manualAddress',
                'string',
                required: false,
                title: 'Address:port of a Wemo device',
            )
            
            href(
                name: "submitAddress",
                title: "Submit",
                page: "mainPage",
                description: "Click to check for a device at the above address"
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

private toHexAddress(ip) {
    try {
        def parts = manualAddress.split(":")
        if (parts.length != 2) {
            return null
        }

        def octets = parts[0].split("\\.")
        if (octets.length != 4) {
            return null
        }
        
        def intOctets = strArrToIntArr(octets)
        def hexIp = HexUtils.intArrayToHexString(intOctets)
        
        def intPort = parts[1].toInteger()
        def hexPort = HexUtils.integerToHexString(intPort, 2)
    
        return "${hexIp}:${hexPort}"
    } catch (Exception e) {
        log.error(e)
        return null
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

def childGetHostAddress(child) {
    debugLog("childGetHostAddress: getting address for ${child}")
    def hexIp = child.getDataValue('ip')
    def hexPort = child.getDataValue('port')
    debugLog("childGetHostAddress: hexIp = ${hexIp}")
    debugLog("childGetHostAddress: hexPort = ${hexPort}")
    try {
        return toDecimalAddress("${hexIp}:${hexPort}")
    } catch (Throwable t) {
        info.warn("Error parsing child address: $t");
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
def handleSetupXml(hexIp, body) {
    def device = body.device
    def deviceType = "${device.deviceType}"
    def friendlyName = "${device.friendlyName}"
    def mac = "${device.macAddress}"

    debugLog("handleSetupXml: handling xml for ${mac}")
    
    debugLog(
        "handleSetupXml: Handling setup.xml for ${deviceType} [${mac}]" +
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
        def discoveredDevice = getDiscoveredDevice(mac)

        if (discoveredDevice) {
            debugLog(
                "handleSetupXml: Updating existing device for ${mac}: " +
                "${discoveredDevice}"
            )
            discoveredDevice.name = friendlyName
            discoveredDevice.verified = true

            // Ensure the device is using the MAC from setup.xml, which may be
            // 1 off from the MAC used for the SSDP message.
            discoveredDevice.mac = mac

            // Ensure the device is using the current ip and port
            discoveredDevice.ip = hexIp.split(":")[0]
            discoveredDevice.port = hexIp.split(":")[1]

            // If there's an existing child device for this discovered device,
            // ensure its address and port are up-to-date
            def child = getChildDevice(mac)
            if (child != null) {
                debugLog("handleSetupXml: Updating IP address for ${child} [${mac}]")
                updateChildAddress(child, ip, port)
            }
        } else {
            // This should only occur for manual device discovery. Automatically
            // discovered devices will have been found by handleSsdpEvent and
            // added to the discovered devices list.
            debugLog("handleSetupXml: Adding ${mac} to list of known devices")
            def dev = [:]
            dev.name = friendlyName
            dev.deviceType = deviceType
            dev.mac = mac
            dev.ip = hexIp.split(":")[0]
            dev.port = hexIp.split(":")[1]
            dev.verified = true
            addDiscoveredDevice(mac, dev)
        }
    } else {
        log.info(
            "handleSetupXml: Ignoring device of unknown type ${deviceType}"
        )
    }
    
    debugLog("handleSetupXml: Discovered devices: ${getDiscoveredDevices()}")
}

def handleSsdpEvent(evt) {
    debugLog("handleSsdpEvent: Handling event ${evt}")
    def dev = parseDiscoveryMessage(evt.description)
    getSetupXml("${dev.ip}:${dev.port}")
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
    verifiedDevices.each { mac, device ->
        def knownDevice = knownDevices[mac]
        if (knownDevice != null) {
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
            def name = device.name ?: "WeMo device ${device.deviceType}"
            knownDevices[mac] = [
                name: name,
                mac: mac,
                ip: device.ip,
                port: device.port,
                deviceType: device.deviceType,
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

    debugLog("getSetupXml: requesting setup.xml for ${hostAddress}")
    httpGet([
        uri: "http://${hostAddress}/setup.xml",
        contentType: "text/xml"
    ]) { resp ->
        handleSetupXml(hexIpAddress, resp.data)
    }
}

private getDiscoveredDevices(onlyVerified = false) {
    debugLog(
        "getDiscoveredDevices: Getting discovered" +
        "${onlyVerified ? ' and verified' : ''} devices"
    )
    if (onlyVerified) {
        debugLog(
            "getDiscoveredDevices: Finding verified devices in " +
            "${state.discoveredDevices}"
        )
        return state.discoveredDevices.findAll {
            it.value?.verified == onlyVerified
        }
    }
    return state.discoveredDevices
}

private getDiscoveredDevice(mac) {
    debugLog("getDiscoveredDevice: Getting discovered device with ID ${mac}")
    return state.discoveredDevices[mac]
}

private addDiscoveredDevice(mac, device) {
    if (!mac) {
        debugLog("addDiscoveredDevice: Not adding ${device} which has no MAC")
    } else {
        debugLog("addDiscoveredDevice: Adding ${mac} -> ${device}")
        state.discoveredDevices[mac] = device
    }
}

private initDiscoveredDevices() {
    debugLog("initDiscoveredDevices: Initializing discovered devices")
    state.discoveredDevices = [:]
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
                def deviceType = selectedDevice.deviceType
                debugLog(
                    "initDevices: Creating WeMo device for ${selectedDevice}"
                )

                switch (deviceType) {
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
            case { it.startsWith('ssdpUSN:') }:
                part -= 'ssdpUSN:'
                valueString = part.split('::')[0].trim()
                if (valueString) {
                    device.UDN = valueString
                }
                break
            case { it.startsWith('ssdpTerm:') }:
                part -= 'ssdpTerm:'
                valueString = part.trim()
                if (valueString) {
                    device.deviceType = valueString
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
        decimalAddress = "<unknown>"
    }

    log.info(
        "Verifying that IP for ${child} is set to ${decimalAddress}"
    )

    def existingIp = child.getDataValue('ip')
    def existingPort = child.getDataValue('port')

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

def childUpdatePort(child, port) {
    debugLog("childUpdatePort: Updating child port to ${port}")
    if (port < 1 || port > 65535 ) {
        debugLog("Invalid TCP port specified - ${port}")
        return
    }
    
    def existingHexPort = child.getDataValue('port')
    def existingPort = existingHexPort != null
        ? HexUtils.hexStringToInt(existingHexPort)
        : null

    if (port && port != existingPort) {
        debugLog(
            "childUpdatePort: ${child} - Updating port from ${existingPort} " +
            "to ${port}"
        )
        def hexPort = HexUtils.integerToHexString(port.toInteger(), 2)
        child.updateDataValue('port', hexPort)
    }
}

def childUpdateIp(child, ip) {
    debugLog("childUpdateIp: Updating child IP to ${ip}")
    String[] parts = ip.split("\\.")

    if (parts.length != 4) {
        debugLog(
            "Invalid address specified - ${ip} (${parts.length} octets found)"
        )
        return
    }
    
    def existingHexIp = child.getDataValue('ip')
    def existingIp = existingHexIp != null ? hexToIp(existingHexIp) : null

    if (ip && ip != existingIp) {
        debugLog(
            "childUpdateIp: ${child} - Updating IP from ${existingIp} to ${ip}"
        )
        def intParts = strArrToIntArr(parts)
        def hexIp = HexUtils.intArrayToHexString(intParts)
        child.updateDataValue('ip', hexIp)
    }
}

private strArrToIntArr(strArr) {
    int[] intArr = new int[strArr.length];
    for (int i = 0; i < strArr.length; i++) {
        intArr[i]=Integer.parseInt(strArr[i], 10)
    }
    intArr
}
