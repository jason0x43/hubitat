/**
 * A simple Nest thermostat driver
 *
 * Author: Jason Cheatham
 * Date: 2018-04-08
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

		attribute 'sunblockEnabled', 'boolean'
		attribute 'sunblockActive', 'boolean'
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
	sendEvent(name: 'thermostatMode', value: 'heat-cool')
}

def eco() {
	log.debug 'eco()'
	nestPut([hvac_mode: 'eco'])
	sendEvent(name: 'thermostatMode', value: 'eco')
}

def sunblockOn() {
	log.debug 'sunblockOn()'
	nestPut([sunlight_correction_enabled: true])
	sendEvent(name: 'sunblockEnabled', value: true)
}

def sunblockOff() {
	log.debug 'sunblockOff()'
	nestPut([sunlight_correction_enabled: false])
	sendEvent(name: 'sunblockEnabled', value: false)
}

def cool() {
	log.debug 'cool()'
	nestPut([hvac_mode: 'cool'])
	sendEvent(name: 'thermostatMode', value: 'cool')
}

def emergencyHeat() {
	log.debug 'emergencyHeat is not implemented'
}

def fanAuto() {
	log.debug 'fanAuto()'
	nestPut([hvac_mode: 'heat'])
	sendEvent(name: 'thermostatMode', value: 'heat')
}

def fanCirculate() {
	log.debug 'fanCirculate()'
	nestPut([
		fan_timer_duration: 15,
		fan_timer_active: true
	])
	sendEvent(name: 'thermostatFanMode', value: 'circulate')
}

def fanOn() {
	log.debug 'Nest only supports "auto" and "circulate"'
}

def heat() {
	log.debug 'heat()'
	nestPut([hvac_mode: 'heat'])
	sendEvent(name: 'thermostatMode', value: 'heat')
}

def off() {
	log.debug 'off()'
	nestPut([hvac_mode: 'off'])
	sendEvent(name: 'thermostatMode', value: 'off')
}

def setCoolingSetpoint(target) {
	log.debug "setCoolingSetpoint(${target})"
	setTargetTemp(target, 'cool')
}

def setHeatingSetpoint(target) {
	log.debug "setHeatingSetpoint(${target})"
	setTargetTemp(target, 'heat')
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

def refresh() {
	log.debug 'Refreshing'
	def id = getDataValue('nestId')
	def data = parent.nestGet("/devices/thermostats/${id}")
	updateState(data)
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
	nestPut([target_temperature_f: value])
	sendEvent(name: "${heatOrCool}ingSetpoint", value: value)
}

private updateState(data) {
	log.trace 'Updating state with ' + data
	sendEvent(name: 'thermostatMode', value: data.hvac_mode)
	sendEvent(name: 'humidity', value: data.humidity)
	sendEvent(
		name: 'thermostatFanMode',
		value: data.fan_timer_active ? 'circulate' : 'auto'
	)

	sendEvent(name: 'sunblockEnabled', value: data.sunlight_correction_enabled)
	sendEvent(name: 'sunblockActive', value: data.sunlight_correction_active)

	if (data.hvac_mode == 'heat') {
		sendEvent(name: 'heatingSetpoint', value: data.target_temperature_f)
	} else if (data.hvac_mode == 'cool') {
		sendEvent(name: 'coolingSetpoint', value: data.target_temperature_f)
	}
}
