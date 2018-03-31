/**
 * Driver for Zigbee 3 button remote (Smartenit ZBWS3B)
 *
 * Original code by GilbertChan, modified by obycode to add 'numButtons'
 * Thanks to Seth Jansen @sjansen for original contributions
 */

metadata {
	definition(name: '3 Button Remote', namespace: 'jason0x43', author: 'Jason Cheatham') {
		capability 'PushableButton'
		capability 'Battery'
		capability 'Configuration'
		capability 'Refresh'

		attribute 'button2', 'enum', ['released', 'pressed']
		attribute 'button3', 'enum', ['released', 'pressed']
		attribute 'numButtons', 'string'

		fingerprint(
			endpointId: '03',
			profileId: '0104',
			deviceId: '0000',
			deviceVersion: '00',
			inClusters: '03 0000 0003 0007',
			outClusters: '01 0006'
		)
	}
}

def parse(description) {
	log.trace "Parse description ${description}"

	def button = null

    if (description.startsWith('catchall:')) {
        return parseCatchAllMessage(description)
    } else if (description.startsWith('read attr - ')) {
        return parseReportAttributeMessage(description)
    }
}

def refresh() {
    log.debug 'Refreshing...'
	return [
        // Request battery voltage from cluster 0x20; should be the
        // measured battery voltage in 100mv increments
        "st rattr 0x${device.deviceNetworkId} 0x01 0x0001 0x0020"
        //zigbee.readAttribute(0x0001, 0x0020)
    ]
}

def configure() {
	log.info 'Configuring...'

	// Set the number of buttons to 3
	updateState('numButtons', '3')

	def configCmds = [
        // Switch control
		"zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}",
	   	'delay 500',
		"zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0006 {${device.zigbeeId}} {}",
	   	'delay 500',
		"zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0006 {${device.zigbeeId}} {}",
	   	'delay 1500',
	] + 
    zigbee.configureReporting(0x0001, 0x0020, 0x20, 30, 21600, 0x01)
    //zigbee.batteryConfig()
    //+ refresh()

	return configCmds
}

/**
 * Store mode and settings
 */
def updateState(name, value) {
	state[name] = value
	device.updateDataValue(name, value)
}

private parseCatchAllMessage(description) {
   	def cluster = zigbee.parseDescriptionAsMap(description)
    if (cluster.profileId == '0104' && cluster.clusterInt == 6) {
        return [getButtonEvent(cluster.sourceEndpoint.toInteger())]
    }
}

private getButtonEvent(button) {
    def event = createEvent(
        name: 'pushed',
        value: button,
        descriptionText: "${device.displayName} button ${button} was pushed",
        isStateChange: true
    )
    log.debug event.descriptionText
    return event
}

private parseReportAttributeMessage(description) {
  	def cluster = zigbee.parseDescriptionAsMap(description)
	// Battery voltage is cluster 0x0001, attribute 0x20
    if (cluster.clusterInt == 1 && cluster.attrInt == 32) {
		return [getBatteryEvent(Integer.parseInt(cluster.value, 16))]
	}
}

private getBatteryEvent(rawValue) {
	log.trace "Battery rawValue = ${rawValue}"

	def event = [
		name: 'battery',
		value: '--',
		translatable: true
	]

    // 0 and 0xff are invalid
	if (rawValue == 0 || rawValue == 255) {
        return createEvent(event)
    }
    
    // Raw value is in 100mV units
    def volts = rawValue / 10

    // Assumes sensor's working floor is 2.1V
    def minVolts = 2.1
    def maxVolts = 3.0
    def pct = (volts - minVolts) / (maxVolts - minVolts)
    def roundedPct = Math.round(pct * 100)
    if (roundedPct <= 0) {
        roundedPct = 1
    }
    
    event.value = Math.min(100, roundedPct)
    event.descriptionText = "${device.displayName} battery is at ${event.value}%"

    log.debug event.descriptionText
	return createEvent(event)
}