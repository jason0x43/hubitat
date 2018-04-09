/**
 * Manager app for Nest thermostat
 *
 * Author: Jason Cheatham
 * Date: 2018-04-08
 *
 * To use this app you first need to create an OAuth client on
 * https://developers.nest.com.  The properties should look like:
 *
 *   Client name: whatever you want
 *   Description: again, whatever you want
 *   Categories: "home automation" seems reasonable
 *   Users: "Individual"
 *   Support URL: whatever you want
 *   Default OAuth redirect URI: leave this blank
 *   Additional OAuth redirect URIs: leave these blank
 *   Permissions: Thermostat read/write -- Nest will make you type a
 *     description for the permission; something like "<your app name> needs to
 *     read and write the state of your Nest" will work.
 *
 * Once you have the OAuth client, install this app and enter the 'Client ID'
 * and 'Client Secret' values from your Nest OAuth client in the this app's
 * inputs, then follow the instructions this app shows you to finish setting it
 * up.
 */

definition(
	name: 'Nest Integration',
	namespace: 'jason0x43',
	author: 'jason0x43',
	singleInstance: true,
	description: 'Setup and manage a Nest thermostat',
	iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
	iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png'
)

preferences {
	page(name: 'mainPage')
	page(name: 'authStartPage')
	page(name: 'authFinishPage')
}

def mainPage() {
	def readyToInstall = state.initialized

	dynamicPage(
		name: 'mainPage',
		submitOnChange: true,
		install: readyToInstall
	) {
		def authState = isAuthorized() ? '[Connected]\n' : '[Not Connected]\n'

		section() {
			input(name: 'clientId', title: 'Client ID', type: 'text', submitOnChange: true)
			input(name: 'clientSecret', title: 'Client secret', type: 'text', submitOnChange: true)

			if (settings.clientId && settings.clientSecret && !isAuthorized()) {
				href(
					'authStartPage',
					title: 'Nest API authorization',
					description: "${authState}Tap to connect to Nest"
				)
			}

			if (isAuthorized()) {
				def thermostats = getThermostats()

				input(
					name: 'thermostats',
					title: 'Select which thermostats to use with Hubitat',
					type: 'enum',
					required: false,
					multiple: true,
					description: 'Tap to choose',
					options: thermostats,
					submitOnChange: true
				)

				paragraph 'NOTE:' +
					'\n\nThe temperature units (F or C) are determined by your ' +
					'location settings automatically. Please update your Hub settings ' +
					'to change the units used.' +
					"\n\nThe current value is ${getTemperatureScale()}."
			}
		}
	}
}

def authStartPage() {
	def params = [
		client_id: settings.clientId,
		state: 'STATE'
	]
	def uri = "https://home.nest.com/login/oauth2?${stringifyQuery(params)}"

	return dynamicPage(
		name: 'authStartPage',
		title: 'OAuth Initialization',
		nextPage: 'authFinishPage'
	) {
		section() {
			href(
				'authorizeApp',
				title: 'Authorize',
				description: 'Right click here and open the Nest authorization page in a new ' +
					'tab to obtain a PIN, then enter it below',
				style: 'external',
				url: uri
			)
			input(name: 'oauthPin', title: 'PIN', type: 'text')
		}
	}
}

def authFinishPage() {
	log.debug 'Finishing login'

	def status;
	def installable;

	try {
		httpPostJson(
			uri: "https://api.home.nest.com/oauth2/access_token",
			body: [
				grant_type: 'authorization_code',
				code: settings.oauthPin,
				client_id: settings.clientId,
				client_secret: settings.clientSecret
			]
		) { resp ->
			log.debug "oauthData: ${resp.data}"
			state.accessToken = resp.data['access_token']
			state.accessTokenExpires = now() + resp.data['expires_in']
		}

		status = 'Nest was successfully authorized'
		installable = true
	} catch (error) {
		status = 'There was a problem authorizing your Nest'
		installable = false
	}

	return dynamicPage(
		name: 'authFinishPage',
		nextPage: 'mainPage',
		install: installable,
		uninstall: true
	) {
		section() {
			paragraph(status)
		}
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updating with settings: ${settings}"
	initialize()
}

def initialize() {
	log.debug 'Initializing'

	state.initialized = true
	state.reAttemptInterval = 15

	settings.thermostats.collect { id ->
		def dni = "${app.id}.${id}"
		def d = getChildDevice(dni)
		def label = "Nest: ${state.thermostatNames[id]}"

		if (!d) {
			log.trace 'Adding device for thermostat ' + dni
			addChildDevice(
				'jason0x43',
				'Nest Thermostat',
				dni,
				null,
				[
					label: label,
					completedSetup: true,
					data: [
						nestId: id
					]
				]
			)
		} else {
			log.trace "Updating thermostat ${dni} with label ${label} and id ${id}"
			d.label = label
			d.updateDataValue('nestId', id)
		}
	}
}

/**
 * Called by child apps to get data from Nest
 */
def nestGet(path) {
	def responseData

	log.trace "Getting ${path} from Nest"

	httpGet(
		uri: 'https://developer-api.nest.com',
		path: path,
		headers: [
			Authorization: "Bearer ${state.accessToken}"
		]
	) { resp ->
		responseData = resp.data
	}

	return responseData
}

/**
 * Called by child apps to set data to Nest
 */
def nestPut(path, data) {
	log.trace "Putting ${data} to ${path}"
	def responseData
	def json = new groovy.json.JsonBuilder(data).toString()

	httpPutJson(
		uri: 'https://developer-api.nest.com',
		path: path,
		body: json,
		headers: [
			Authorization: "Bearer ${state.accessToken}"
		]
	) { resp ->
		if (resp.status == 307) {
			httpPutJson(
				uri: resp.headers.Location,
				body: json,
				headers: [
					Authorization: "Bearer ${state.accessToken}"
				]
			) { rsp ->
				responseData = rsp.data
			}
		} else {
			responseData = resp.data
		}
	}

	return responseData
}

private getThermostats() {
	log.debug 'Getting list of Nest thermostats'

	def deviceListParams = [
		uri: 'https://developer-api.nest.com',
		path: '/',
		headers: [
			Authorization: "Bearer ${state.accessToken}"
		]
	]
	def names = [:]

	try {
		def data = nestGet('/')
		log.debug "Got Nest response: ${data}"

		def devices = data.devices.thermostats;
		state.thermostatData = devices;
		state.numThermostats = devices.size()

		log.trace "Found ${state.numThermostats} thermostats"

		devices.each { id, therm ->
			names[id] = therm.name
		}
	} catch(Exception e) {
		log.error 'Error getting nests: ' + e
	}

	state.thermostatNames = names
	return names.sort { it.value }
}

private isAuthorized() {
	log.debug "Is authorized? token=${state.accessToken}, expiresIn=${state.accessTokenExpires}"
	return state.accessToken != null &&
		state.accessTokenExpires != null &&
		state.accessTokenExpires - now() > 0
}

private disconnect() {
	log.debug 'Disconnected from Nest. User must reauthorize.'

	state.connected = 'lost'
	state.accessToken = null

	// Notify each child that we lost so it gets logged
	// def d = getChildDevices()
	// d?.each { oneChild ->
	// }

	// unschedule('pollScheduled')
	// unschedule('scheduleWatchdog')
	// runEvery3Hours('notifyApiLost')
}

private isConnected() {
	if (state.connected == null) {
		state.connected = 'warn'
	}
	return state.connected?.toString() ?: 'lost'
}

private connected() {
	state.connected = 'full'
	// unschedule("notifyApiLost")
	state.reAttemptPoll = 0
}

private stringifyQuery(params) {
	return params.collect { k, v ->
		"${k}=${URLEncoder.encode(v.toString())}"
	}.sort().join("&")
}
