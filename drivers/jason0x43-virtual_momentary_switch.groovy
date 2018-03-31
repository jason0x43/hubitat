/**
 * Virtual Momentary Switch
 *
 * Author: Jason Cheatham <j.cheatham@gmail.com>
 * Date: 2018-03-24
 */

metadata {
	definition (name: 'Virtual Momentary Switch', namespace: 'jason0x43', author: 'jason0x43') {
		capability 'Actuator'
		capability 'Switch'
		capability 'Sensor'
	}

    preferences {	
		input(
            name: 'delayNum',
            type: 'number',
            title: 'Delay before switching off (default is 3s)',
            required: true,
            defaultValue: 3
        )	
	}
}

def parse(String description) {
}

def on() {
	sendEvent(name: "switch", value: "on", isStateChange: true, displayed: false)
	runIn(delayNum ?: 3, off, [overwrite: false])
}

def off() {
	sendEvent(name: "switch", value: "off", isStateChange: true, displayed: false)
}