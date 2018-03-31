/**
 * Pushover Driver
 *
 * Inspired by original work for SmartThings by: Zachary Priddy,
 * https://zpriddy.com, me@zpriddy.com
 */

preferences {
	input('apiKey', 'text', title: 'API Key:', description: 'Pushover API Key')
	input('userKey', 'text', title: 'User Key:', description: 'Pushover User Key')

	if (validate()) {
		input(
			'deviceName',
			'enum',
			title: 'Device Name (Blank = All Devices):',
			description: '',
			multiple: true,
			required: false,
			options: validate('deviceList')
		)
		input(
			'priority',
			'enum',
			title: 'Default Message Priority (Blank = NORMAL):',
			description: '',
			defaultValue: '0',
			options:[['-1':'LOW'],
			['0':'NORMAL'],
			['1':'HIGH']]
		)
		input(
			'sound',
			'enum',
			title: 'Notification Sound (Blank = App Default):',
			description: '',
			options: getSoundOptions()
		)
		input(
			'url',
			'text',
			title: 'Supplementary URL:',
			description: ''
		)
		input(
			'urlTitle',
			'text',
			title: 'URL Title:',
			description: ''
		)
	}
}

metadata {
	definition(name: 'Pushover', namespace: 'jason0x43', author: 'Jason Cheatham') {
		capability 'Notification'
		capability 'Actuator'
		capability 'Speech Synthesis'
	}
}

def installed() {
}

def updated() {
}

def validate(type) {
	if (type == 'deviceList') {
		log.debug 'Generating Device List...'
	} else {
		log.debug 'Validating Keys...'
	}

	def validated = false
	def params = [
		uri: 'https://api.pushover.net/1/users/validate.json',
		body: [
			token: apiKey,
			user: userKey,
			device: ''
		]
	]

	if (apiKey =~ /[A-Za-z0-9]{30}/ && userKey =~ /[A-Za-z0-9]{30}/) {
		try {
			httpPost(params) { response ->
				if (response.status != 200) {
					handleError(response)
				} else {
					if (type == 'deviceList') {
						log.debug 'Device list generated'
						deviceOptions = response.data.devices
					} else {
						log.debug 'Keys validated'
						validated = true
					}
				}
			}
		} catch (Exception e) {
			log.error "An invalid key was probably entered. ${e}"
		}
	} else {
		// Do not sendPush() here, the user may have intentionally set up bad keys for testing.
		log.error "API key '${apiKey}' or User key '${userKey}' is not properly formatted!"
	}

	return type == 'deviceList' ? deviceOptions : validated
}

def getSoundOptions() {
	log.debug 'Generating Notification List...'

	def myOptions = []
	httpGet(
		uri: "https://api.pushover.net/1/sounds.json?token=${apiKey}"
	) { response ->
		if (response.status != 200) {
			handleError(response)
		} else {
			log.debug 'Notification List Generated'
			mySounds = response.data.sounds
			mySounds.each { eachSound ->
				myOptions << [(eachSound.key): eachSound.value]
			}
		}
	}

	return myOptions
}

def speak(message) {
	deviceNotification(message)
}

def deviceNotification(message) {
	if (message.startsWith('[L]')) {
		customPriority = '-1'
		message = message.drop(3)
	}

	if (message.startsWith('[N]')) {
		customPriority = '0'
		message = message.drop(3)
	}

	if (message.startsWith('[H]')) {
		customPriority = '1'
		message = message.drop(3)
	}

	if (customPriority) {
		priority = customPriority
	}

	if (deviceName) {
		log.debug "Sending Message: ${message} Priority: ${priority} to Device: ${deviceName}"
	} else {
		log.debug "Sending Message: [${message}] Priority: [${priority}] to [All Devices]"
	}

	// Prepare the package to be sent
	def params = [
		uri: 'https://api.pushover.net/1/messages.json',
		body: [
			token: apiKey,
			user: userKey,
			message: message,
			priority: priority,
			sound: sound,
			url: url,
			device: deviceName,
			url_title: urlTitle
		]
	]

	if (apiKey =~ /[A-Za-z0-9]{30}/ && userKey =~ /[A-Za-z0-9]{30}/) {
		httpPost(params) { response ->
			if (response.status != 200) {
				handleError(response)
			} else {
				log.debug 'Message Received by Pushover server'
			}
		}
	} else {
		// Do not sendPush() here, the user may have intentionally set up bad keys for testing.
		log.error "API key '${apiKey}' or User key '${userKey}' is not properly formatted!"
	}
}

def handleError(response) {
	sendPush(
		"ERROR: Pushover received HTTP error ${response.status}. Check your keys!"
	)
	log.error "Received HTTP error ${response.status}. Check your keys!"
}
