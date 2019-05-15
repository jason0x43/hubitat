/**
 * WeMo Connect
 *
 * Author: Jason Cheatham
 * Last updated: 2019-05-15, 08:37:21-0400
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
    def refreshCount = !state.refreshCount ? 0 : state.refreshCount as int
    def refreshInterval = 5

    state.refreshCount = refreshCount + 1

    //ssdp request every 25 seconds
    if ((refreshCount % 5) == 0) {
        discoverAllWemoTypes()
    }

    //setup.xml request every 5 seconds except on discoveries
    if (((refreshCount % 1) == 0) && ((refreshCount % 5) != 0)) {
        verifyDevices()
    }

    def devicesDiscovered = getDiscoveredDevices()

    dynamicPage(
        name: 'mainPage',
        title: 'Device discovery started!',
        install: true,
        uninstall: true,
        refreshInterval: 30
    ) {
        section('') {
            input(
                'selectedDevices',
                'enum',
                required: false,
                title: "Select Wemo Devices \n(${devicesDiscovered.size() ?: 0} found)",
                multiple: true,
                options: devicesDiscovered
            )
        }

        section('Options') {
            input(
                'interval',
                'number',
                title: 'How often should WeMo devices be refreshed? (minutes)',
                defaultValue: 5
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
}

def childGetHostAddress(device) {
     def ip = device.getDataValue('ip')
     def port = device.getDataValue('port')

     if (!ip || !port) {
         def parts = device.deviceNetworkId.split(':')
         if (parts.length == 2) {
             ip = parts[0]
             port = parts[1]
         } else {
             log.warn "Can't figure out ip and port for device: ${device.id}"
         }
     }

     "${convertHexToIP(ip)}:${convertHexToInt(port)}"
}

def getHostAddress(dni) {
    def parts = dni.split(':')
    if (parts.length == 2) {
        ip = parts[0]
        port = parts[1]
    } else {
        log.warn "Can't figure out ip and port for device: ${dni}"
    }

    "${convertHexToIP(ip)}:${convertHexToInt(port)}"
}

def getSubscriptionData(message) {
    def headerString = message.header

    if (isSubscriptionHeader(headerString)) {
        return [
            sid: getSubscriptionId(headerString),
            timeout: getSubscriptionTimeout() - 30
        ]
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
    return 60 * (settings.interval?:5)
}

def getTime() {
    // This is essentially System.currentTimeMillis()/1000, but System is
    // disallowed by the sandbox.
    ((new GregorianCalendar().time.time / 1000l).toInteger()).toString()
}

def handleSetupXml(response) {
    def body = response.xml
    def device = body.device
    log.trace 'Handling friendly name: ' + device.friendlyName

    if (
        device.deviceType?.text().startsWith(
            'urn:Belkin:device:controllee:1'
        ) ||  device.deviceType?.text().startsWith(
            'urn:Belkin:device:insight:1'
        ) || device.deviceType?.text().startsWith(
            'urn:Belkin:device:sensor'
        ) || device.deviceType?.text().startsWith(
            'urn:Belkin:device:lightswitch'
        ) || device.deviceType?.text().startsWith(
            'urn:Belkin:device:dimmer'
        )
    ) {
        def devices = getWemoDevices()
        def wemoDevice = devices.find {
            it?.key?.contains(body?.device?.UDN?.text())
        }

        if (wemoDevice) {
            wemoDevice.value << [
                name: body?.device?.friendlyName?.text(),
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
    parsedEvent << ['hub':hub]
    log.trace 'Parsed discovery message: ' + parsedEvent.ssdpTerm

    log.trace('Getting devices for event');
    def devices = getWemoDevices()
    def device = devices[parsedEvent.ssdpUSN.toString()]

    if (!device) {
        // if it doesn't already exist
        devices << [(parsedEvent.ssdpUSN.toString()): parsedEvent]
    } else {
        // just update the values
        def deviceChangedValues = false

        if (device.ip != parsedEvent.ip || device.port != parsedEvent.port) {
            device.ip = parsedEvent.ip
            device.port = parsedEvent.port
            deviceChangedValues = true
        }

        if (deviceChangedValues) {
            def children = getChildDevices()
            children.each {
                if (it.getDeviceDataByName('mac') == parsedEvent.mac) {
                    log.debug "updating ip and port for device ${it} with " +
                        "mac ${parsedEvent.mac}"
                    it.sync(parsedEvent.ip, parsedEvent.port)
                }
            }
        }
    }
}

def isSubscriptionHeader(header) {
    if (header == null) {
        return false;
    }
    return header.contains("SID: uuid:") && header.contains('TIMEOUT:');
}

def childGetBinaryState(child) {
    return new hubitat.device.HubSoapAction(
        path: '/upnp/control/basicevent1',
        urn: 'urn:Belkin:service:basicevent:1',
        action: 'GetBinaryState',
        headers: [
            HOST: childGetHostAddress(child)

        ]
    )
}

def childRefresh(child) {
    log.debug "childRefresh(${child})"
    [
        childSubscribe(child),
        childTimeSyncResponse(child),
        child.poll()
    ]
}

def childResubscribe(child) {
    log.debug "childResubscribe(${child})"

    def sid = child.getDataValue('subscriptionId')

    if (sid == null) {
        log.trace 'No existing subscription -- subscribing'
        childSubscribe(child)
    } else {
        // Clear out any current subscription ID; will be reset when the
        // subscription completes
        log.trace 'Clearing existing sid'
        child.updateDataValue('subscriptionId', null)

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
}

def childSetBinaryState(child, state, brightness = null) {
    def body = [ BinaryState: "$state" ]

    if (brightness != null) {
        body.Brightness = "$brightness"
    }

    log.trace "Setting binary state to ${body}"

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
    log.debug "childSubscribe(${child})"

    // Clear out any current subscription ID; will be reset when the
    // subscription completes
    log.trace 'Clearing existing sid'
    child.updateDataValue('subscriptionId', null)

    def dni = child.deviceNetworkId

    new hubitat.device.HubAction([
        method: 'SUBSCRIBE',
        path: '/upnp/event/basicevent1',
        headers: [
            HOST: childGetHostAddress(child),
            CALLBACK: "<http://${getCallbackAddress()}/>",
            NT: 'upnp:event',
            TIMEOUT: "Second-${getSubscriptionTimeout()}"
        ]
    ], dni)
}

def childSubscribeIfNecessary(child) {
    log.trace "childSubscribeIfNecessary(${child})"
    def sid = child.getDataValue('subscriptionId')
    if (sid == null) {
        childSubscribe(child)
    }
}

def childSync(child, ip, port) {
    log.trace "childSync(${child}, ${ip}:${port})"

    def existingIp = child.getDataValue('ip')
    def existingPort = child.getDataValue('port')

    if (ip && ip != existingIp) {
        log.trace "Updating IP from ${existingIp} to ${ip}"
        child.updateDataValue('ip', ip)
    }

    if (port && port != existingPort) {
        log.trace "Updating port from $existingPort to $port"
        child.updateDataValue('port', port)
    }

    childSubscribe(child)
}

def childTimeSyncResponse(child) {
    log.debug "childTimeSyncResponse(${child})"

    new hubitat.device.HubSoapAction(
        path: '/upnp/control/timesync1',
        url: 'urn:Belkin:service:timesync:1',
        action: 'TimeSync',
        body: [
            //TODO: Use UTC Timezone
            UTC: getTime(),
            TimeZone: '-05.00',
            dst: 1,
            DstSupported: 1
        ],
        headers: [
            HOST: childGetHostAddress(child)
        ]
    )
}

def childUnsubscribe(child) {
    log.debug "childUnsubscribe(${child})"

    def sid = child.getDataValue('subscriptionId')

    // Clear out the current subscription ID
    log.trace 'Clearing existing sid'
    child.updateDataValue('subscriptionId', null)

    new hubitat.device.HubAction([
        method: 'UNSUBSCRIBE',
        path: '/upnp/event/basicevent1',
        headers: [
            HOST: getHostAddress(child),
            SID: "uuid:${sid}"
        ]
    ], child.deviceNetworkId)
}

private getCallbackAddress() {
    def hub = location.hubs[0];
    def localIp = hub.getDataValue('localIP')
    def localPort = hub.getDataValue('localSrvPortTCP')
    "${localIp}:${localPort}"
}

private initDevices() {
    log.trace 'Initializing devices'

    def devices = getWemoDevices()

    selectedDevices.each { dni ->
        def selectedDevice = devices.find {
            it.value.mac == dni
        } ?: devices.find {
            "${it.value.ip}:${it.value.port}" == dni
        }
        def createdDevice

        if (selectedDevice) {
            createdDevice = getChildDevices()?.find {
                it.dni == selectedDevice.value.mac || 
                it.device.getDataValue('mac') == selectedDevice.value.mac
            }
        }

        if (!createdDevice) {
            log.debug "Creating WeMo with dni: ${selectedDevice.value.mac}"
            def name
            def namespace = 'jason0x43'
            def haveDriver = false

            switch (selectedDevice.value.ssdpTerm){
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
                def deviceData = selectedDevice.value
                createdDevice = addChildDevice(
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
                log.debug "Created ${createdDevice.displayName} with id: " +
                    "${createdDevice.id}, dni: ${createdDevice.deviceNetworkId}"
            } else {
                log.trace "No driver for ${selectedDevice.value.mac} (${name})"
            }
        } else {
            log.trace "Device ${createdDevice.displayName} with id $dni already exists"
        }

        log.trace 'Setting up device subscription...'
        createdDevice.refresh()
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

private discoverAllWemoTypes() {
    log.trace 'discoverAllWemoTypes'

    def targets = [
        'urn:Belkin:device:insight:1',
        'urn:Belkin:device:controllee:1',
        'urn:Belkin:device:sensor:1',
        'urn:Belkin:device:lightswitch:1',
        'urn:Belkin:device:dimmer:1'
    ]

    if (!state.subscribe) {
        targets.each { target ->
            subscribe(location, "ssdpTerm.${target}", handleSsdpEvent)
            log.trace 'subscribed to ' + target
        }
        state.subscribe = true
    }

    sendHubCommand(
        new hubitat.device.HubAction(
            "lan discovery ${targets.join('/')}",
            hubitat.device.Protocol.LAN
        )
    )
}

private getDiscoveredDevices() {
    log.trace 'Getting discovered devices'
    def devices = getWemoDevices().findAll { it?.value?.verified == true }
    def map = [:]
    devices.each {
        def value = it.value.name ?: "WeMo device ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
        def key = it.value.mac
        map[key] = value
    }
    log.trace 'Found devices: ' + map
    return map
}

private getFriendlyName(deviceNetworkId) {
    def hostAddress = getHostAddress(deviceNetworkId)
    log.trace "Getting friendly name for ${deviceNetworkId} (${hostAddress})"
    sendHubCommand(
        new hubitat.device.HubAction(
            [
                method: 'GET',
                path: '/setup.xml',
                headers: [
                    HOST: hostAddress
                ],
            ],
            null,
            [
                callback: handleSetupXml
            ]
        )
    )
}

private getWemoDevices() {
    log.trace 'Getting WeMo devices'
    if (!state.devices) {
        state.devices = [:]
    }
    return state.devices
}

private parseDiscoveryMessage(description) {
    def device = [:]
    def parts = description.split(',')

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

private verifyDevices() {
    log.trace 'Verifying devices'
    def devices = getWemoDevices().findAll { it?.value?.verified != true }
    devices.each {
        getFriendlyName("${it.value.ip}:${it.value.port}")
    }
}
