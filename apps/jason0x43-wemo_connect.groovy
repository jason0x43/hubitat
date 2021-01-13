/**
 * WeMo Connect
 *
 * Author: Jason Cheatham
 * Last updated: 2021-01-12, 21:18:03-0500
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
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Partner/wemo@2x.png'
)

preferences {
    page(name: 'mainPage')
}

import hubitat.helper.HexUtils

def mainPage() {
    // Reset the refresh state if the last refresh was more than 60 seconds ago
    if (!state.refreshCount || !state.lastRefresh || (now() - state.lastRefresh) > 60000) {
        debugLog("mainPage: Resetting refresh count and discovered devices")
        state.refreshCount = 0
        state.discoveredDevices = [:]
    }

    state.minDriverVersion = 2

    def refreshCount = state.refreshCount

    state.refreshCount = refreshCount + 1
    state.lastRefresh = now()

    // ssdp request every 30 seconds
    discoverAllWemoTypes()

    def devices = getKnownDevices()
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
                'Device discovery messages are being broadcast every 30 seconds. ' +
                'Any devices on the local network should show up within a minute or two.'
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
                title: 'How often should WeMo devices be refreshed? (minutes, default is 5, max is 59)',
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
    log.debug('Installed')
    initialize()
}

def updated() {
    log.debug('Updated')
    initialize()
}

def initialize() {
    log.debug 'Initializing'
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
    debugLog("childGetHostAddress: getting address for ${child}")
    def hexIp = device.getDataValue('ip')
    def hexPort = device.getDataValue('port')
    debugLog("childGetHostAddress: hexIp = ${hexIp}")
    debugLog("childGetHostAddress: hexPort = ${hexPort}")
    toDecimalAddress("${hexIp}:${hexPort}")
}

def childGetBinaryState(child) {
    log.info("Getting state for ${child}")
    debugLog("childGetBinaryState: sending request to ${childGetHostAddress(child)}")
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
        debugLog("childResubscribe: No existing subscription for ${child} -- subscribing")
        return childSubscribe(child)
    }

    debugLog("childResubscribe: renewing ${child} subscription to ${sid}")

    // Clear the existing SID -- it should be set if the resubscribe succeeds
    debugLog('childReubscribe: clearing existing sid')
    child.updateDataValue('subscriptionId', null)

    debugLog("childResubscribe: sending request to ${childGetHostAddress(child)}")
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

    debugLog("childSetBinaryState: sending binary state request to ${childGetHostAddress(child)}")
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

    debugLog("childSubscribe: sending subscribe request to ${childGetHostAddress(child)}")
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
        debugLog("childSubscribeIfNecessary: no active subscription -- subscribing")
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
    def tzOffset = (offsetHours < 0 ? '-' : '') + String.format('%02d.00', Math.abs(offsetHours))
    def isDst = tz.inDaylightTime(now)
    def hasDst = tz.observesDaylightTime()

    debugLog("childSyncTime: sending sync request to ${childGetHostAddress(child)}")
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

    debugLog("childUnsubscribe: sending unsubscribe request to ${childGetHostAddress(child)}")
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
        debugLog("parse: updating subscriptionId for ${child} to ${sid}")
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
            log.error "/setup.xml returned a wemo device that didn't exist"
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
            debugLog("handleSsdpEvent: Updating IP address for ${child} [${device.mac}]")
            updateChildAddress(child, device.ip, device.port)
        }
    } else {
        debugLog("handleSsdpEvent: Adding ${parsedEvent.mac} to list of known devices")
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
    log.debug "Converting ${hex} to int..."
    Integer.parseInt(hex,16)
}

private hexToIp(hex) {
    log.debug "Converting ${hex} to IP..."
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

    def map = [:]

    def existingDevices = getChildDevices()
    existingDevices.each { device ->
        def mac = device.deviceNetworkId
        def name = device.label ?: device.name
        map[mac] = [
            mac: mac,
            name: name,
            ip: device.getDataValue('ip'),
            port: device.getDataValue('port'),
            typeName: device.typeName,
            needsUpdate: device.getDriverVersion() < state.minDriverVersion
        ]
        debugLog("getKnownDevices: Added already-installed device ${mac}:${name}")
    }

    def verifiedDevices = getDiscoveredDevices(true)
    verifiedDevices.each { key, device ->
        def mac = device.mac

        if (map.containsKey(mac)) {
            def name = device.name
            if (name != null && name != map[mac].name) {
                map[mac].name = "${name} (installed as ${map[mac].name})"
                debugLog("getKnownDevices: Updated name for ${mac} to ${name}")
            }
        } else {
            def name = device.name ?: "WeMo device ${device.ssdpUSN.split(':')[1][-3..-1]}"
            map[mac] = [
                mac: mac,
                name: name,
                ip: device.ip,
                port: device.port,
                // The ssdpTerm and hub will be used if a new child device is
                // created
                ssdpTerm: device.ssdpTerm,
                hub: device.hub
            ]
            debugLog("getKnownDevices: Added discovered device ${map[mac]}")
        }
    }

    debugLog("getKnownDevices: Known devices: ${map}")

    map.each { mac, data ->
        def address = "${hexToIp(data.ip)}:${hexToInt(data.port)}"
        def text = "${data.name} [MAC: ${mac}, IP: ${address}"
        if (data.typeName) {
            text += ", Driver: ${data.typeName}] ${data.needsUpdate ? '&nbsp;&nbsp;<< Driver needs update >>' : ''}"
        } else {
            text += ']'
        }
        map[mac].label = "<li>${text}</li>"
    }

    map
}

private getSetupXml(hexIpAddress) {
    def hostAddress = toDecimalAddress(hexIpAddress)

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

private getDiscoveredDevices(isVerified = null) {
    if (!state.discoveredDevices) {
        state.discoveredDevices = [:]
    }

    if (verified != null) {
        return state.discoveredDevices.findAll { it.value?.verified == isVerified }
    }
    return state.discoveredDevices
}

private getDiscoveredDevice(usn) {
    getDiscoveredDevices()[usn]
}

private initDevices() {
    debugLog('initDevices: Initializing devices')

    def knownDevices = getKnownDevices()

    selectedDevices.each { dni ->
        debugLog("initDevices: Looking for selected device ${dni} in known devices...")

        def selectedDevice = knownDevices[dni]

        if (selectedDevice) {
            debugLog("initDevices: Found device; looking for existing child with dni ${dni}")
            def child = getChildDevice(dni)

            if (!child) {
                def driverName
                def namespace = 'jason0x43'
                debugLog("initDevices: Creating WeMo device for ${selectedDevice}")

                switch (selectedDevice.ssdpTerm){
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
                debugLog("initDevices: Device ${child} with id $dni already exists")
            }

            if (child) {
                debugLog('initDevices: Setting up device subscription...')
                child.refresh()
            }
        } else {
            log.warn("initDevices: Could not find device ${dni} in ${knownDevices}")
        }
    }
}

private isSubscriptionHeader(header) {
    if (header == null) {
        return false;
    }
    header.contains("SID: uuid:") && header.contains('TIMEOUT:');
}

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
                device.devicetype = valueString
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
                valueString = part.trim()
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
    debugLog("updateChildAddress: Updating address of ${child} to ${ip}:${port}")
    def address = "${ip}:${port}"
    log.info("Verifying that IP for ${child} is set to ${toDecimalAddress(address)}")

    def existingIp = child.getDataValue('ip')
    def existingPort = child.getDataValue('port')

    if (ip && ip != existingIp) {
        debugLog("childSync: Updating IP from ${hexToIp(existingIp)} to ${hexToIp(ip)}")
        child.updateDataValue('ip', ip)
    }

    if (port && port != existingPort) {
        debugLog("childSync: Updating port from ${hexToInt(existingPort)} to ${hexToInt(port)}")
        child.updateDataValue('port', port)
    }

    childSubscribe(child)
}

def childUpdatePort(child, port) {
    debugLog("childUpdatePort: Updating child port to ${port}")
    if (port < 1 || port > 65535 ) {
        debugLog("Invalid TCP port specified - ${port}")
        -1
    } else {
        def hPort = HexUtils.integerToHexString(port)
        def existingPort = child.getDataValue('port')

        if (port && port != h2i(existingPort)) {
            debugLog("childUpdate: ${child} - Updating port from ${Integer.parseInt(existingPort, 16)} to ${port}")
            child.updateDataValue('port', hPort)
        }
    }
}

def childUpdateIP(child, ip) {
    String[] splitIp = ip.split("\\.")
    debugLog("childUpdateIP: Updating child IP to ${ip}")

    if (splitIp.length !=4) {
        debugLog("Invalid IP address specified - ${ip} (${splitIp.length} octets found)")
        -1
    } else {
        def intArrIp = strArrToIntArr(splitIp)
        def hexIp = HexUtils.intArrayToHexString(intArrIp)

        def existingIp = child.getDataValue('ip')

        if (ip && ip != hexToIp(existingIp)) {
            debugLog("childUpdate: ${child} - Updating IP from ${hexToIp(existingIp)} to ${ip}")
            child.updateDataValue('ip', hexIp)
        }
    }
}

private strArrToIntArr(strArr) {
    int[] intArr = new int[strArr.length];
    for (int i = 0; i < strArr.length; i++) {
        intArr[i]=Integer.parseInt(strArr[i], 10)
    }
    intArr
}
