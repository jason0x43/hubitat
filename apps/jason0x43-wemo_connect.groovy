/**
 * WeMo Connect
 *
 * Author: Jason Cheatham
 * Date: 2018-03-31
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
 *	 http://www.apache.org/licenses/LICENSE-2.0
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
    
	description: 'Allows you to integrate your WeMo Switch and Wemo Motion sensor with Hubitat.',
	singleInstance: true,
	iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Partner/wemo.png',
	iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Partner/wemo@2x.png'
)

preferences {
	page(name: 'mainPage')
	page(name: 'wemoWatchdog')
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

	def devicesDiscovered = devicesDiscovered()

	dynamicPage(
		name: 'mainPage',
		title: 'Device discovery started!',
		install: true,
		uninstall: true,
		refreshInterval: 5
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
			href(
				name: 'watchdog',
				title: 'Watchdog Settings',
				required: false,
				page: 'wemoWatchdog',
				description: 'Tap to config the Watchdog Timer'
			)
			input(
				'interval',
				'number',
				title: 'Set refresh minutes',
				defaultValue: 5
			)
		}
	}
}

def wemoWatchdog() {
	dynamicPage(name: 'wemoWatchdog') {
		def anythingSet = anythingSet()
		if (anythingSet) {
			section('Verify Timer When') {
				ifSet(
					'motion',
					'capability.motionSensor',
					title: 'Motion Here',
					required: false,
					multiple: true
				)
				ifSet(
					'contact',
					'capability.contactSensor',
					title: 'Contact Opens',
					required: false,
					multiple: true
				)
				ifSet(
					'contactClosed',
					'capability.contactSensor',
					title: 'Contact Closes',
					required: false,
					multiple: true
				)
				ifSet(
					'acceleration',
					'capability.accelerationSensor',
					title: 'Acceleration Detected',
					required: false,
					multiple: true
				)
				ifSet(
					'mySwitch',
					'capability.switch',
					title: 'Switch Turned On',
					required: false,
					multiple: true
				)
				ifSet(
					'mySwitchOff',
					'capability.switch',
					title: 'Switch Turned Off',
					required: false,
					multiple: true
				)
				ifSet(
					'arrivalPresence',
					'capability.presenceSensor',
					title: 'Arrival Of',
					required: false,
					multiple: true
				)
				ifSet(
					'departurePresence',
					'capability.presenceSensor',
					title: 'Departure Of',
					required: false,
					multiple: true
				)
				ifSet(
					'smoke',
					'capability.smokeDetector',
					title: 'Smoke Detected',
					required: false,
					multiple: true
				)
				ifSet(
					'water',
					'capability.waterSensor',
					title: 'Water Sensor Wet',
					required: false,
					multiple: true
				)
				ifSet(
					'temperature',
					'capability.temperatureMeasurement',
					title: 'Temperature',
					required: false,
					multiple: true
				)
				ifSet(
					'powerMeter',
					'capability.powerMeter',
					title: 'Power Meter',
					required: false,
					multiple: true
				)
				ifSet(
					'energyMeter',
					'capability.energyMeter',
					title: 'Energy',
					required: false,
					multiple: true
				)
				ifSet(
					'signalStrength',
					'capability.signalStrength',
					title: 'Signal Strength',
					required: false,
					multiple: true
				)
				// remove from production
				ifSet(
					'button1',
					'capability.button',
					title: 'Button Press',
					required:false,
					multiple:true
				)
			}
		}

		def hideable = anythingSet || (app.installationState == 'COMPLETE' && anythingSet)
		def sectionTitle = anythingSet ? 'Select additional triggers' : 'Verify Timer When...'

		section(sectionTitle, hideable: hideable, hidden: true) {
			ifUnset(
				'motion',
				'capability.motionSensor',
				title: 'Motion Here',
				required: false,
				multiple: true
			)
			ifUnset(
				'contact',
				'capability.contactSensor',
				title: 'Contact Opens',
				required: false,
				multiple: true
			)
			ifUnset(
				'contactClosed',
				'capability.contactSensor',
				title: 'Contact Closes',
				required: false,
				multiple: true
			)
			ifUnset(
				'acceleration',
				'capability.accelerationSensor',
				title: 'Acceleration Detected',
				required: false,
				multiple: true
			)
			ifUnset(
				'mySwitch',
				'capability.switch',
				title: 'Switch Turned On',
				required: false,
				multiple: true
			)
			ifUnset(
				'mySwitchOff',
				'capability.switch',
				title: 'Switch Turned Off',
				required: false,
				multiple: true
			)
			ifUnset(
				'arrivalPresence',
				'capability.presenceSensor',
				title: 'Arrival Of',
				required: false,
				multiple: true
			)
			ifUnset(
				'departurePresence',
				'capability.presenceSensor',
				title: 'Departure Of',
				required: false,
				multiple: true
			)
			ifUnset(
				'smoke',
				'capability.smokeDetector',
				title: 'Smoke Detected',
				required: false,
				multiple: true
			)
			ifUnset(
				'water',
				'capability.waterSensor',
				title: 'Water Sensor Wet',
				required: false,
				multiple: true
			)
			ifUnset(
				'temperature',
				'capability.temperatureMeasurement',
				title: 'Temperature',
				required: false,
				multiple: true
			)
			ifUnset(
				'signalStrength',
				'capability.signalStrength',
				title: 'Signal Strength',
				required: false,
				multiple: true
			)
			ifUnset(
				'powerMeter',
				'capability.powerMeter',
				title: 'Power Meter',
				required: false,
				multiple: true
			)
			ifUnset(
				'energyMeter',
				'capability.energyMeter',
				title: 'Energy Meter',
				required: false,
				multiple: true
			)
			//remove from production
			ifUnset(
				'button1',
				'capability.button',
				title: 'Button Press',
				required:false,
				multiple:true
			)
		}
	}
}

def devicesDiscovered() {
	log.debug 'Getting discovered devices'
	def devices = getWemoDevices().findAll { it?.value?.verified == true }
	log.debug 'Found devices: ' + devices
	def map = [:]
	devices.each {
		def value = it.value.name ?: "WeMo device ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
		def key = it.value.mac
		map[key] = value
	}
	return map
}

def getWemoDevices() {
	log.debug('Getting wemo devices')
	if (!state.devices) {
		state.devices = [:]
	}
	return state.devices
}

def installed() {
	log.debug('Installed')
}

def updated() {
	log.debug('Updated')
	unsubscribe()
	state.subscribe = false

	if (selectedDevices) {
		addDevices()
	}

	subscribeToEvents()
	scheduleActions()
	scheduledActionsHandler()
}

def subscribeToEvents() {
	log.debug('Subscribing to events')
	subscribe(contact, 'contact.open', eventHandler)
	subscribe(contactClosed, 'contact.closed', eventHandler)
	subscribe(acceleration, 'acceleration.active', eventHandler)
	subscribe(motion, 'motion.active', eventHandler)
	subscribe(mySwitch, 'switch.on', eventHandler)
	subscribe(mySwitchOff, 'switch.off', eventHandler)
	subscribe(arrivalPresence, 'presence.present', eventHandler)
	subscribe(departurePresence, 'presence.not present', eventHandler)
	subscribe(smoke, 'smoke.detected', eventHandler)
	subscribe(smoke, 'smoke.tested', eventHandler)
	subscribe(smoke, 'carbonMonoxide.detected', eventHandler)
	subscribe(water, 'water.wet', eventHandler)
	subscribe(temperature, 'temperature', eventHandler)
	subscribe(powerMeter, 'power', eventHandler)
	subscribe(energyMeter, 'energy', eventHandler)
	subscribe(signalStrength, 'lqi', eventHandler)
	subscribe(signalStrength, 'rssi', eventHandler)
	subscribe(button1, 'button.pushed', eventHandler)
}

def eventHandler(evt) {
	takeAction(evt)
}

def scheduledActionsHandler() {
	log.trace 'Scheduling actions handler'
	state.actionTime = new Date().time
	refreshDevices()
	discoverAllWemoTypes()
}

def resubscribe() {
	log.debug 'Resubscribing to events'
	refresh()
}

def refreshDevices() {
	log.debug 'Refreshing devices'

	def devices = getAllChildDevices()
	devices.each { d ->
		log.debug "Calling refresh() on device: ${d.id}"
		d.refresh()
	}
}

def subscribeToDevices() {
	log.debug 'Subscribing to devices'

	def devices = getAllChildDevices()
	devices.each { d ->
		d.subscribe()
	}
}

def addDevices() {
	log.trace 'Adding devices'

	def devices = getWemoDevices()

	selectedDevices.each { dni ->
		def selectedDevice = devices.find {
			it.value.mac == dni
		} ?: devices.find {
			"${it.value.ip}:${it.value.port}" == dni
		}
		def d

		if (selectedDevice) {
			d = getChildDevices()?.find {
				it.dni == selectedDevice.value.mac || 
				it.device.getDataValue('mac') == selectedDevice.value.mac
			}
		}

		if (!d) {
			log.debug "Creating WeMo with dni: ${selectedDevice.value.mac}"
			def name
			def namespace = 'jason0x43'
			def haveDriver = false

			switch (selectedDevice.value.ssdpTerm){
				case ~/.*lightswitch.*/: 
					name = 'Wemo Light Switch'
					break
				case ~/.*sensor.*/: 
					name = 'Wemo Motion'
					haveDriver = true
					break
				case ~/.*controllee.*/: 
					name = 'Wemo Switch'
					haveDriver = true
					break
				case ~/.*insight.*/: 
					name = 'WeMo Insight Switch'
					break
			}

			if (haveDriver) {
				d = addChildDevice(
					namespace,
					name,
					selectedDevice.value.mac,
					selectedDevice.value.hub,
					[ 
						'label':  selectedDevice?.value?.name ?: 'Wemo Device',
						'data': [
							'mac': selectedDevice.value.mac,
							'ip': selectedDevice.value.ip,
							'port': selectedDevice.value.port
						]
					]
				)
				log.debug "Created ${d.displayName} with id: ${d.id}, dni: ${d.deviceNetworkId}"
			} else {
				log.debug "No driver for ${selectedDevice.value.mac} (${name})"
			}
		} else {
			log.debug "found ${d.displayName} with id $dni already exists"
		}
	}
}

def initialize() {
	log.debug 'Initializing'

	// remove location subscription afterwards
	 unsubscribe()
	 state.subscribe = false

	if (selectedDevices) {
		addDevices()
	}
}

def handleSsdpEvent(evt) {
	def description = evt.description
	def hub = evt?.hubId

	// log.trace('incoming description: ' + description);

	def parsedEvent = parseDiscoveryMessage(description)
	parsedEvent << ['hub':hub]
	log.trace 'Parsed discovery message: ' + parsedEvent.ssdpTerm

	log.trace('getting devices for event');
	def devices = getWemoDevices()
	def device = devices[parsedEvent.ssdpUSN.toString()]

	if (!device) {
		// if it doesn't already exist
		devices << [(parsedEvent.ssdpUSN.toString()): parsedEvent]
	} else {
		// just update the values
		boolean deviceChangedValues = false

		if (device.ip != parsedEvent.ip || device.port != parsedEvent.port) {
			device.ip = parsedEvent.ip
			device.port = parsedEvent.port
			deviceChangedValues = true
		}

		if (deviceChangedValues) {
			def children = getChildDevices()
			children.each {
				if (it.getDeviceDataByName('mac') == parsedEvent.mac) {
					log.debug "updating ip and port, and resubscribing, for device ${it} with mac ${parsedEvent.mac}"
					it.sync(parsedEvent.ip, parsedEvent.port)
				}
			}
		}
	}
}

private takeAction(evt) {
	log.trace 'Taking action for ' + evt
	def eventTime = new Date().time
	if (eventTime > (
		60000 + Math.max(
			(settings.interval ? settings.interval.toInteger():0),3
		) * 1000 * 60 + (state.actionTime?:0)
	)) {
		scheduledActionsHandler()
	}
}

private def parseXmlBody(body) {
	log.trace 'Parsing xml body: ' + body
	def decodedBytes = body.decodeBase64()
	def bodyString
	try {
		bodyString = new String(decodedBytes)
	} catch (Exception e) {
		// Keep this log for debugging StringIndexOutOfBoundsException issue
		throw e
	}
	return new XmlSlurper().parseText(bodyString)
}

private def parseDiscoveryMessage(description) {
	// log.trace 'Parsing discovery message ' + description

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

def pollChildren() {
	log.trace 'Polling children'
	def devices = getAllChildDevices()
	devices.each { d ->
		//only poll switches?
		d.poll()
	}
}

def delayPoll() {
	log.trace 'Scheduling poll'
	runIn(5, 'pollChildren')
}

def handleLocationEvent(event) {
	log.debug "Got location event: " + event.description
}

def handleSetupXml(response) {
	def body = response.xml
	log.trace 'handling friendly name: ' + body.device.friendlyName

	if (
		body?.device?.deviceType?.text().startsWith(
			'urn:Belkin:device:controllee:1'
		) ||  body?.device?.deviceType?.text().startsWith(
			'urn:Belkin:device:insight:1'
		) || body?.device?.deviceType?.text().startsWith(
			'urn:Belkin:device:sensor'
		) || body?.device?.deviceType?.text().startsWith(
			'urn:Belkin:device:lightswitch'
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

private scheduleActions() {
	log.trace 'Scheduling actions'
	def minutes = Math.max(settings.interval.toInteger(),1)
	def cron = "0 0/${minutes} * * * ?"
	schedule(cron, scheduledActionsHandler)
}

private discoverAllWemoTypes() {
	log.trace 'discoverAllWemoTypes'

	def targets = [
		'urn:Belkin:device:insight:1',
		'urn:Belkin:device:controllee:1',
		'urn:Belkin:device:sensor:1',
		'urn:Belkin:device:lightswitch:1'
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

private getFriendlyName(deviceNetworkId) {
	def hostAddress = getHostAddress(deviceNetworkId)
	log.trace "GET /setup.xml HTTP/1.1\r\nHOST: ${hostAddress} ${deviceNetworkId}"
	sendHubCommand(
		new hubitat.device.HubAction(
			"""GET /setup.xml HTTP/1.1\r\nHOST: ${hostAddress}\r\n\r\n""",
			hubitat.device.Protocol.LAN,
			deviceNetworkId,
			[callback: handleSetupXml]
		)
	)
}

private anythingSet() {
	for (name in [
		'motion',
		'contact',
		'contactClosed',
		'acceleration',
		'mySwitch',
		'mySwitchOff',
		'arrivalPresence',
		'departurePresence',
		'smoke',
		'water',
		'temperature',
		'signalStrength',
		'powerMeter',
		'energyMeter',
		'button1',
		'timeOfDay',
		'triggerModes',
		'timeOfDay'
	]) {
		if (settings[name]) {
			return true
		}
	}

	return false
}

private getHostAddress(d) {
	def parts = d.split(':')
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	"${ip}:${port}"
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

private verifyDevices() {
	log.trace 'Verifying devices'
	def devices = getWemoDevices().findAll { it?.value?.verified != true }
	devices.each {
		getFriendlyName("${it.value.ip}:${it.value.port}")
	}
}

private ifUnset(options, name, capability) {
	if (!settings[name]) {
		input(options, name, capability)
	}
}

private ifSet(options, name, capability) {
	if (settings[name]) {
		input(options, name, capability)
	}
}
