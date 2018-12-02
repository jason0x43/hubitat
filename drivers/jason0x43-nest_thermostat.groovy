/**
 * A simple Nest thermostat driver
 *
 * Author: Jason Cheatham
 * Last updated: 2018-12-01, 22:45:07-0500
 */

metadata {
	definition(name: 'Nest Thermostat', namespace: 'jason0x43', author: 'jason0x43') {
		capability 'Relative Humidity Measurement'
		capability 'Thermostat'
		capability 'Temperature Measurement'
		capability 'Sensor'
		capability 'Refresh'

		command 'eco'
		command 'sunblockOn'
		command 'sunblockOff'
		command 'away'
		command 'home'
		command 'setScale', ['enum']

		attribute 'away', 'boolean'
		attribute 'sunblockEnabled', 'boolean'
		attribute 'sunblockActive', 'boolean'
		attribute 'scale', 'enum', ['f', 'c']
	}

	preferences {	
		input(
			name: 'pollInterval',
			type: 'enum',
			title: 'Update interval (in minutes)',
			options: ['5', '10', '30'],
			required: true
		)	
	}
}

def auto() {
	log.debug 'auto()'
	nestPut([hvac_mode: 'heat-cool'])
	refresh()
}

def away() {
	log.debug 'away()'
	parent.setAway(true)
	refresh(true)
}

def cool() {
	log.debug 'cool()'
	nestPut([hvac_mode: 'cool'])
	refresh()
}

def eco() {
	log.debug 'eco()'
	nestPut([hvac_mode: 'eco'])
	refresh()
}

def emergencyHeat() {
	log.debug 'emergencyHeat is not implemented'
}

def fanAuto() {
	log.debug 'fanAuto()'
	nestPut([hvac_mode: 'heat'])
	refresh()
}

def fanCirculate() {
	log.debug 'fanCirculate()'
	nestPut([
		fan_timer_duration: 15,
		fan_timer_active: true
	])
	refresh()
}

def fanOn() {
	log.debug 'Nest only supports "auto" and "circulate"'
}

def heat() {
	log.debug 'heat()'
	nestPut([hvac_mode: 'heat'])
	refresh()
}

def home() {
	log.debug 'home()'
	parent.setAway(false)
	refresh(false)
}

def off() {
	log.debug 'off()'
	nestPut([hvac_mode: 'off'])
	refresh()
}

def setCoolingSetpoint(target) {
	log.debug "setCoolingSetpoint(${target})"
	setTargetTemp(target, 'cool')
}

def setHeatingSetpoint(target) {
	log.debug "setHeatingSetpoint(${target})"
	setTargetTemp(target, 'heat')
}

def setScale(scale) {
	log.debug "setScale(${scale})"
	scale = scale ? scale.toLowerCase() : null
	if (scale != 'f' && scale != 'c') {
		log.error "Invalid scale ${scale}"
		return
	}

	nestPut([temperature_scale: scale])
	refresh()
}

def setSchedule(schedule) {
	log.debug "setSchedule(${schedule})"
}

def setThermostatFanMode(mode) {
	log.debug "setThermostatFanMode(${mode})"
}

def setThermostatMode(mode) {
	log.debug "setThermostatMode(${mode})"
}

def sunblockOff() {
	log.debug 'sunblockOff()'
	nestPut([sunlight_correction_enabled: false])
	refresh()
}

def sunblockOn() {
	log.debug 'sunblockOn()'
	nestPut([sunlight_correction_enabled: true])
	refresh()
}

def updated() {
	log.debug 'Updated'

	log.trace('Unscheduling poll timer')
	unschedule()

	if (pollInterval == '5') {
		log.trace "Polling every 5 minutes"
		runEvery5Minutes(refresh)
	} else if (pollInterval == '10') {
		log.trace "Polling every 10 minutes"
		runEvery10Minutes(refresh)
	} else if (pollInterval == '30') {
		log.trace "Polling every 30 minutes"
		runEvery30Minutes(refresh)
	}
}

def parse(description) {
	log.debug 'Received event: ' + description
}

def refresh(isAway) {
	log.debug 'Refreshing'
	if (isAway != null) {
		updateState([away: isAway, triesLeft: 3])
	} else {
		updateState()
	}
}

private nestPut(data) {
	def id = getDataValue('nestId')
	parent.nestPut("/devices/thermostats/${id}", data)
}

private setTargetTemp(temp, heatOrCool) {
	def id = getDataValue('nestId')

	def mode = device.currentValue('thermostatMode')
	if (mode != heatOrCool && mode != 'heat-cool') {
		log.debug "Not ${heatOrCool}ing"
		return 
	}

	def value = temp.toInteger()
	def scale = device.currentValue('scale')

	if (mode == 'heat-cool') {
		if (heatOrCool == 'cool') {
			nestPut(["target_temperature_high_${scale}": value])
		} else {
			nestPut(["target_temperature_low_${scale}": value])
		}
	} else {
		nestPut(["target_temperature_${scale}": value])
	}

	refresh()
}

private updateState(args) {
	def id = getDataValue('nestId')
	def data = parent.nestGet("/devices/thermostats/${id}")

	// If the thermostat mode doesn't agree with the 'away' state, wait a few
	// seconds and update again
	if (
		args &&
		(
			args.away == true && data.hvac_mode != 'eco' ||
			args.away == false && data.hvac_mode == 'eco'
		) &&
		args.triesLeft > 0
	) {
		log.trace "Device hasn't updated for away yet, retrying"
		runIn(3, 'updateState', [data: [
			away: args.away,
			triesLeft: args.triesLeft - 1
		]])
		return
	}

	def scale = data.temperature_scale.toLowerCase()
	def away = parent.isAway()

	log.trace "data: ${data}"

	sendEvent(name: 'away', value: away)
	sendEvent(name: 'thermostatMode', value: data.hvac_mode)
	sendEvent(name: 'humidity', value: data.humidity)
	sendEvent(
		name: 'thermostatFanMode',
		value: data.fan_timer_active ? 'circulate' : 'auto'
	)
	sendEvent(name: 'scale', value: scale)
	sendEvent(name: 'sunblockEnabled', value: data.sunlight_correction_enabled)
	sendEvent(name: 'sunblockActive', value: data.sunlight_correction_active)
	sendEvent(name: 'temperature', value: data["ambient_temperature_${scale}"])
	sendEvent(name: 'temperatureUnit', value: data.temperature_scale)
	sendEvent(name: 'nestPresence', value: away ? 'away' : 'home')
	sendEvent(name: 'hasLeaf', value: data.has_leaf)

	def state = data.hvac_state == 'off' ? 'idle' : data.hvac_state
	sendEvent(name: 'thermostatOperatingState', value: state)

	log.trace "thermostatMode: ${data.hvac_mode}"

	if (data.hvac_mode == 'heat') {
		log.trace 'setting heating setpoint to ' + data["target_temperature_${scale}"]
		sendEvent(name: 'heatingSetpoint', value: data["target_temperature_${scale}"])
	} else if (data.hvac_mode == 'cool') {
		log.trace 'setting cooling setpoint to ' + data["target_temperature_${scale}"]
		sendEvent(name: 'coolingSetpoint', value: data["target_temperature_${scale}"])
	} else if (data.hvac_mode == 'eco') {
		sendEvent(name: 'heatingSetpoint', value: data["eco_temperature_low_${scale}"])
		sendEvent(name: 'coolingSetpoint', value: data["eco_temperature_high_${scale}"])
	} else if (data.hvac_mode == 'heat-cool') {
		sendEvent(name: 'heatingSetpoint', value: data["target_temperature_low_${scale}"])
		sendEvent(name: 'coolingSetpoint', value: data["target_temperature_high_${scale}"])
	}
}
