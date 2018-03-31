/**
 * Hue Dimmer Switch
 *
 * Based on the driver by Stephen McLaughlin
 *
 * Modified by Jason Cheatham for Hubitat compatibility, 2018-03-24
 */

metadata {
	definition (name: 'Hue Dimmer Switch', namespace: 'jason0x43', author: 'Jason Cheatham') {
		capability 'Configuration'
		capability 'Battery'
		capability 'Refresh'
		capability 'PushableButton'
		capability 'HoldableButton'
		capability 'Sensor'

		fingerprint(
			profileId: '0104',
			endpointId: '02',
			application:'02',
			outClusters: '0019',
			inClusters: '0000,0001,0003,000F,FC00',
			manufacturer: 'Philips',
			model: 'RWL020',
			deviceJoinName: 'Hue Dimmer Switch'
		)

		attribute 'lastAction', 'string'
	}
}

def parse(description) {
	log.debug 'Parsing ' + description

	def msg = zigbee.parse(description)

	// TODO: handle 'numberOfButtons' attribute

	if (description?.startsWith('catchall:')) {
		def map = parseCatchAllMessage(description)
		return createEvent(map)
	} else if (description?.startsWith('enroll request')) {
		def cmds = enrollResponse()
		return cmds?.collect { new hubitat.device.HubAction(it) }
	} else if (description?.startsWith('read attr -')) {
		return parseReportAttributeMessage(description).each {
			createEvent(it)
		}
	}
}

def refresh() {
	log.debug 'Refresh'

	def refreshCmds = []

	// Fetches battery from 0x02
	refreshCmds += "st rattr 0x${device.deviceNetworkId} 0x02 0x0001 0x0020"

	// motion, confirmed
	// configCmds += zigbee.configureReporting(0x406,0x0000, 0x18, 30, 600, null)

	// refreshCmds += zigbee.configureReporting(0x000F, 0x0055, 0x10, 30, 30, null)
	// refreshCmds += 'zdo bind 0xDAD6 0x01 0x02 0x000F {00178801103317AA} {}'
	// refreshCmds += 'delay 2000'
	// refreshCmds += 'st cr 0xDAD6 0x02 0x000F 0x0055 0x10 0x001E 0x001E {}'
	// refreshCmds += 'delay 2000'

	// refreshCmds += zigbee.configureReporting(0x000F, 0x006F, 0x18, 0x30, 0x30)
	// refreshCmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x02 0xFC00 {${device.zigbeeId}} {}"
	// refreshCmds += 'delay 2000'
	// refreshCmds += "st cr 0x${device.deviceNetworkId} 0x02 0xFC00 0x0000 0x18 0x001E 0x001E {}"
	// refreshCmds += 'delay 2000'
	// log.debug refreshCmds

	return refreshCmds
}

def configure() {
	log.debug 'Configuring'

	// def zigbeeId = swapEndianHex(device.hub.zigbeeId)
	// log.debug 'Configuring Reporting and Bindings.'
	def configCmds = []

	// Configure Button Count
	sendEvent(name: 'numberOfButtons', value: 4, displayed: false)

	// Monitor Buttons
	// TODO: This could be
	// zigbee.configureReporting(0xFC00, 0x0000, 0x18, 0x001e, 0x001e);
	// but no idea how to point it at a different endpoint
	configCmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x02 0xFC00 {${device.zigbeeId}} {}"
	configCmds += 'delay 2000'
	configCmds += "st cr 0x${device.deviceNetworkId} 0x02 0xFC00 0x0000 0x18 0x001E 0x001E {}"
	configCmds += 'delay 2000'

	// Monitor Battery
	// TODO: This could be zigbee.batteryConfig(); but no idea how to point it
	// at a different endpoint
	configCmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x02 0x0001 {${device.zigbeeId}} {}"
	configCmds += 'delay 2000'
	configCmds += "st cr 0x${device.deviceNetworkId} 0x02 0x0001 0x0020 0x20 0x001E 0x0258 {}"
	// configCmds += 'st cr 0x${device.deviceNetworkId} 0x02 0x0001 0x0020 0x20 0x001E 0x001e {}'
	configCmds += 'delay 2000'

	return configCmds + refresh()
}

def configureHealthCheck() {
	def hcIntervalMinutes = 12
	refresh()
	sendEvent(
		name: 'checkInterval',
		value: hcIntervalMinutes * 60,
		displayed: false,
		data: [protocol: 'zigbee', hubHardwareId: device.hub.hardwareID]
	)
}

def updated() {
	log.debug 'Updated'
	configureHealthCheck()
}

private parseReportAttributeMessage(description) {
	log.trace 'Parsing report attribute message ' + description

	def descMap = (description - 'read attr - ').split(',').inject([:]) { map, param ->
		def nameAndValue = param.split(':')
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}

	def result = []

	// Battery
	if (descMap.cluster == '0001' && descMap.attrId == '0020') {
		result << getBatteryResult(Integer.parseInt(descMap.value, 16))
	}

	return result
}

private shouldProcessMessage(cluster) {
	log.debug 'Checking if should process message'

	// 0x0B is default response indicating message got through
	def ignoredMessage = cluster.profileId != 0x0104 ||
	cluster.command == 0x0B ||
	(cluster.data.size() > 0 && cluster.data.first() == 0x3e)

	return !ignoredMessage
}

private getBatteryResult(rawValue) {
	// TODO: needs calibration
	log.debug "Battery rawValue = ${rawValue}"

	def result = [
		name: 'battery',
		value: '--',
		translatable: true
	]

	def volts = rawValue / 10

	if (!(rawValue == 0 || rawValue == 255)) {
		if (volts > 3.5) {
			result.descriptionText =
				"{{ device.displayName }} battery has too much power: (> 3.5) volts."
		} else if (device.getDataValue('manufacturer') == 'SmartThings') {
			// For the batteryMap to work the key needs to be an int
			volts = rawValue
			def batteryMap = [
				28:100,
				27:100,
				26:100,
				25:90,
				24:90,
				23:70,
				22:70,
				21:50,
				20:50,
				19:30,
				18:30,
				17:15,
				16:1,
				15:0
			]
			def minVolts = 15
			def maxVolts = 28

			if (volts < minVolts) {
				volts = minVolts
			} else if (volts > maxVolts) {
				volts = maxVolts
			}

			def pct = batteryMap[volts]
			if (pct != null) {
				result.value = pct
				result.descriptionText =
					"${device.displayName} battery was ${value}%"
			}
		} else {
			def minVolts = 2.1
			def maxVolts = 3.0
			def pct = (volts - minVolts) / (maxVolts - minVolts)
			def roundedPct = Math.round(pct * 100)
			if (roundedPct <= 0) {
				roundedPct = 1
			}
			result.value = Math.min(100, roundedPct)
			result.descriptionText =
				"${device.displayName} battery was ${value}%"
		}
	}

	return result
}

private getButtonResult(rawValue) {
	log.trace 'Getting button for ' + rawValue

	def result = [
		name: 'button',
		value: '--',
		translatable: true
	]
	def button = rawValue[0]
	def buttonState = rawValue[4]
	def buttonHoldTime = rawValue[6]

	// This is the state in the HUE api
	def hueStatus = (button as String) + '00' + (buttonState as String)

	log.error "Button: ${button}, Hue code: ${hueStatus}, hold hime: ${buttonHoldTime}"
	result.data = ['buttonNumber': button]
	result.value = 'pushed'

	if (buttonState == 2) {
		result = createEvent(
			name: 'pushed',
			value: button,
			descriptionText: '${device.displayName} button ${button} was pushed',
			isStateChange: true
		)
		sendEvent(name: 'lastAction', value: button + ' pushed')
	} else if (buttonState == 3) {
		result = createEvent(
			name: 'held',
			value: button,
			descriptionText: "${device.displayName} button ${button} was held",
			isStateChange: true
		)
		sendEvent(name: 'lastAction', value: button + ' held')
	}

	return result
}

private parseCatchAllMessage(description) {
	log.trace 'Parsing catchall message'

	def resultMap = [:]
	def cluster = zigbee.parse(description)

	if (shouldProcessMessage(cluster)) {
		switch (cluster.clusterId) {
			case 0x0001:
				// 0x07 - configure reporting
				if (cluster.command != 0x07) {
					resultMap = getBatteryResult(cluster.data.last())
				}
				break

			case 0xFC00:
				if (cluster.command == 0x00) {
					resultMap = getButtonResult(cluster.data)
				}
				break
		}
	}

	return resultMap
}
