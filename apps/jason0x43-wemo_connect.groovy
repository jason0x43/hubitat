/**
 * WeMo Connect
 *
 * Author: Jason Cheatham
 * Last updated: 2019-06-10, 08:52:02-0400
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

def mainPage() {
    // Reset the refresh state if the last refresh was more than 60 seconds ago
    if (!state.refreshCount || !state.lastRefresh || (now() - state.lastRefresh) > 60000) {
        debugLog("mainPage: Resetting refresh count and discovered devices")
        state.refreshCount = 0
        state.devices = [:]
    }

    state.minDriverVersion = 2

    def refreshCount = state.refreshCount

    state.refreshCount = refreshCount + 1
    state.lastRefresh = now()

    if ((refreshCount % 5) == 0) {
        //ssdp request every 25 seconds
        discoverAllWemoTypes()
    } else {
        // setup.xml request every 5 seconds except on discoveries
        verifyDevices()
    }

    def devices = getKnownDevices()

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
                options: devices
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
    def hexIp = device.getDataValue('ip')
    def hexPort = device.getDataValue('port')
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
    debugLog('childSubscribe: clearing existing sid')
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
    child.updateDataValue('subscriptionId', null)

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
    child.updateDataValue('subscriptionId', null)

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

    debugLog(
        "handleSetupXml: Handling setup.xml for ${deviceType}" +
        " (friendly name is '${device.friendlyName}')"
    )

    if (
        deviceType.startsWith('urn:Belkin:device:controllee:1') ||
        deviceType.startsWith('urn:Belkin:device:insight:1') ||
        deviceType.startsWith('urn:Belkin:device:sensor') ||
        deviceType.startsWith('urn:Belkin:device:lightswitch') ||
        deviceType.startsWith('urn:Belkin:device:dimmer')
    ) {
        def devices = getWemoDevices()
        def wemoDevice = devices.find {
            it.key.contains("${device.UDN}")
        }

        if (wemoDevice) {
            wemoDevice.value << [
                name: "${device.friendlyName}",
                verified: true
            ]
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

    def devices = getWemoDevices()
    def device = devices[parsedEvent.ssdpUSN.toString()]

    if (!device) {
        debugLog("handleSsdpEvent: Adding ${parsedEvent.mac} to list of known devices")

        def id = parsedEvent.ssdpUSN.toString()
        state.devices << [(id): parsedEvent]
    } else if (device.ip != parsedEvent.ip || device.port != parsedEvent.port) {
        debugLog("handleSsdpEvent: Updating IP address for existing device ${device.mac}")

        device.ip = parsedEvent.ip
        device.port = parsedEvent.port

        def child = getChildDevice(device.mac)
        updateChildAddress(child, device.ip, device.port)
        debugLog("handleSsdpEvent: Updated IP for device ${child}")
    } else {
        debugLog("handleSsdpEvent: Device ${device.mac} is up to date")
    }
}

private convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

private convertHexToIP(hex) {
    [
        convertHexToInt(hex[0..1]),
        convertHexToInt(hex[2..3]),
        convertHexToInt(hex[4..5]),
        convertHexToInt(hex[6..7])
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
    existingDevices.each {
        def mac = it.deviceNetworkId
        def name = it.label ?: it.name
        map[mac] = [
            label: name,
            needsUpdate: it.getDriverVersion() < state.minDriverVersion,
            typeName: it.typeName
        ]
        debugLog("getKnownDevices: Added already-installed device ${mac}:${name}")
    }

    def devices = getWemoDevices().findAll { it?.value?.verified == true }
    devices.each {
        def mac = it.value.mac

        if (map.containsKey(mac)) {
            def name = it.value.name
            if (name != null && name != map[mac].label) {
                map[mac].label = "${name} (installed as ${map[mac].label})"
                debugLog("getKnownDevices: Updated name for ${mac} to ${name}")
            }
        } else {
            def name = "WeMo device ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
            map[mac] = [label: name]
            debugLog("getKnownDevices: Added discovered device ${mac}:${name}")
        }
    }

    debugLog("getKnownDevices: Known devices: ${map}")

    def deviceMap = [:]
    map.each {
        def data = it.value
        def mac = it.key
        def text = "${data.label} [${mac}"
        if (data.typeName) {
            text += ", ${data.typeName}] ${data.needsUpdate ? '&nbsp;&nbsp;<< Driver needs update >>' : ''}"
        } else {
            text += ']'
        }
        deviceMap[mac] = "<li>${text}</li>"
    }

    deviceMap
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

private getWemoDevices() {
    if (!state.devices) {
        state.devices = [:]
    }
    state.devices
}

private initDevices() {
    debugLog('initDevices: Initializing devices')

    def devices = getWemoDevices()

    selectedDevices.each { dni ->
        debugLog("initDevices: Looking for selected device ${dni} in known devices...")

        def selectedDevice = devices.find {
            it.value.mac == dni
        } ?: devices.find {
            "${it.value.ip}:${it.value.port}" == dni
        }

        def childDevice

        if (selectedDevice) {
            def selectedMac = selectedDevice.value.mac

            debugLog("initDevices: Found device; looking for existing child with dni ${selectedMac}")
            childDevice = getChildDevices()?.find {
                it.deviceNetworkId == selectedMac || 
                it.device.getDataValue('mac') == selectedMac
            }

            if (!childDevice) {
                def name
                def namespace = 'jason0x43'
                def deviceData = selectedDevice.value
                debugLog("initDevices: Creating WeMo device for ${deviceData}")

                switch (deviceData.ssdpTerm){
                    case ~/.*insight.*/: 
                        name = 'Wemo Insight Switch'
                        break

                    // The Light Switch and Switch use the same driver
                    case ~/.*lightswitch.*/: 
                    case ~/.*controllee.*/: 
                        name = 'Wemo Switch'
                        break

                    case ~/.*sensor.*/: 
                        name = 'Wemo Motion'
                        break

                    case ~/.*dimmer.*/: 
                        name = 'Wemo Dimmer'
                        break
                }

                if (name) {
                    childDevice = addChildDevice(
                        namespace,
                        name,
                        deviceData.mac,
                        deviceData.hub,
                        [ 
                            'label':  deviceData.name ?: 'Wemo Device',
                            'data': [
                                'mac': deviceData.mac,
                                'ip': deviceData.ip,
                                'port': deviceData.port
                            ]
                        ]
                    )
                    log.info(
                        "initDevices: Created ${childDevice.displayName} with id: " +
                        "${childDevice.id}, MAC: ${childDevice.deviceNetworkId}"
                    )
                } else {
                    log.warn("initDevices: No driver for ${selectedDevice.value.mac} (${name})")
                }
            } else {
                debugLog("initDevices: Device ${childDevice.displayName} with id $dni already exists")
            }

            debugLog('initDevices: Setting up device subscription...')
            childDevice.refresh()
        } else {
            log.warn("initDevices: Could not find device ${dni} in ${devices}")
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
    def parts = address.split(':')
    ip = parts[0]
    port = parts[1]
    "${convertHexToIP(ip)}:${convertHexToInt(port)}"
}

private updateChildAddress(child, ip, port) {
    log.info("Updating IP for ${child} to ${ip}:${port}")

    def existingIp = child.getDataValue('ip')
    def existingPort = child.getDataValue('port')

    if (ip && ip != existingIp) {
        debugLog("childSync: Updating IP from ${existingIp} to ${ip}")
        child.updateDataValue('ip', ip)
    }

    if (port && port != existingPort) {
        debugLog("childSync: Updating port from $existingPort to $port")
        child.updateDataValue('port', port)
    }

    childSubscribe(child)
}

private verifyDevices() {
    debugLog('verifyDevices: Verifying devices')
    def devices = getWemoDevices().findAll { it?.value?.verified != true }
    devices.each {
        // Note that the ip and port in device.value are hex
        getSetupXml("${it.value.ip}:${it.value.port}")
    }
}
