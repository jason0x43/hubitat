/**
 * Manager app for Nest thermostat
 *
 * Author: Jason Cheatham
 * Last updated: 2018-12-03, 08:55:29-0500
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
 *   Permissions:
 *     - Thermostat read/write -- Nest will make you type a
 *       description for the permission; something like "<your app name> needs to
 *       read and write the state of your Nest" will work.
 *     - Other Permissions - Away read/write (same caveat as above)
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
    page(name: 'deauthorizePage')
}

def mainPage() {
    def readyToInstall = state.initialized

    dynamicPage(
        name: 'mainPage',
        submitOnChange: true,
        install: readyToInstall,
        uninstall: true
    ) {
        def authState = isAuthorized() ? '[Connected]\n' : '[Not Connected]\n'

        section() {
            paragraph(
                '<h2>Setup your Nest</h2> ' +
                '<p style="line-height:19px;margin-bottom:0">First, you\'ll need to <a ' +
                'href="https://developers.nest.com">create ' +
                'an oauth client with Nest</a>. The properties should look ' +
                'like:</p> ' +
                '<ul style="margin-bottom:1em">' +
                '<li><b>Client name:</b> whatever you want</li>' +
                '<li><b>Description:</b> again, whatever you want</li>' +
                '<li><b>Categories:</b> "home automation" seems reasonable</li>' +
                '<li><b>Users:</b> "Individual"</li>' +
                '<li><b>Support URL:</b> whatever you want</li>' +
                '<li><b>Default OAuth redirect URI:</b> leave this blank</li>' +
                '<li><b>Additional OAuth redirect URIs:</b> leave these blank</li>' +
                '<li><b>Permissions</b>' +
                '<ul>' +
                '<li><b>Thermostat read/write:</b> Nest will make you type a ' +
                'description for the permission; something like "&lt;your app ' +
                'name&gt; needs to read and write the state of your Nest" will ' +
                'work.</li>' +
                '<li><b>Other Permissions:</b> Away read/write (same caveat as ' +
                'above)</li>' +
                '</ul>' +
                '</li>' +
                '</ul>' +
                '<p style="line-height:19px">Once you have the OAuth client, ' +
                'enter the "Client ID" and "Client ' +
                'Secret" values from your Nest OAuth client in the this app\'s' +
                'inputs, then follow the instructions this app shows you to ' +
                'finish setting it up.</p>'
            )

            input(
                name: 'clientId',
                title: 'Client ID',
                type: 'text',
                submitOnChange: true
            )
            input(
                name: 'clientSecret',
                title: 'Client secret',
                type: 'text',
                submitOnChange: true
            )

            if (settings.clientId && settings.clientSecret && !isAuthorized()) {
                href(
                    'authStartPage',
                    title: 'Nest API authorization',
                    description: "${authState}Tap to connect to Nest"
                )
            }

            if (isAuthorized()) {
                def structures = getStructures()

                input(
                    name: 'structure',
                    title: 'Select which structure to manage',
                    type: 'enum',
                    required: false,
                    multiple: false,
                    description: 'Tap to choose',
                    options: structures,
                    submitOnChange: true
                )
            }

            if (isAuthorized() && settings.structure) {
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

                input(
                    name: 'watchMode',
                    title: 'Use Hubitat mode for home/away?',
                    type: 'bool',
                    required: false,
                    submitOnChange: true
                )
            }

            if (isAuthorized()) {
                href(
                    'deauthorizePage',
                    title: 'Log out',
                    description: "Log out of Nest. You'll need to login again " +
                        'to control your Nest devices.'
                )
            }
        }
    }
}

def deauthorizePage() {
    disconnect()

    return dynamicPage(
        name: 'deauthorizePage',
        title: 'Log out',
        nextPage: 'mainPage'
    ) {
        section() {
            paragraph 'You have successfully logged out.'
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
        httpPost(
            uri: "https://api.home.nest.com/oauth2/access_token",
            body: [
                grant_type: 'authorization_code',
                code: settings.oauthPin,
                client_id: settings.clientId,
                client_secret: settings.clientSecret
            ]
        ) { resp ->
            log.debug "oauthData: ${resp.data}"
            state.accessToken = resp.data.access_token
        }

        status = 'Nest was successfully authorized'
        installable = true
    } catch (error) {
        log.error error
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
            // log.trace 'Adding device for thermostat ' + dni
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
            // log.trace "Updating thermostat ${dni} with label ${label} and id ${id}"
            d.label = label
            d.updateDataValue('nestId', id)
            d
        }
    }

    unsubscribe()

    if (settings.watchMode) {
        // log.trace 'Subscribing to mode changes'
        subscribe(location, 'mode', handleModeChange)
        // Update the away state based on the current mode
        handleModeChange([value: location.mode])
    }
}

def handleModeChange(event) {
    // log.trace "Handling mode change to ${event.value}"
    def mode = event.value
    def away = mode == 'Away'
    if (away != isAway()) {
        // log.trace "Saw mode change to '${event.value}', updating Nest presence"
        setAway(away)
        settings.thermostats.each { id ->
            // log.trace "Refreshing thermostat ${id}"
            def dni = "${app.id}.${id}"
            def d = getChildDevice(dni)
            d.refresh(away)
        }
    }
}

def isAway() {
    def data = nestGet("/structures/${settings.structure}")
    return data.away == 'away'
}

def setAway(isAway) {
    if (isAway != true && isAway != false) {
        log.error "Invalid away value ${isAway}"
        return
    }
    nestPut("/structures/${settings.structure}", [away: isAway ? 'away' : 'home'])
}

/**
 * Called by child apps to get data from Nest
 */
def nestGet(path) {
    def responseData

    // log.trace "Getting ${path} from Nest"

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
    def responseData
    def json = new groovy.json.JsonBuilder(data).toString()
    def token = state.accessToken

    // log.trace "Putting ${json} to ${path}"

    httpPutJson(
        uri: 'https://developer-api.nest.com',
        path: path,
        body: json,
        headers: [
            Authorization: "Bearer ${token}"
        ]
    ) { resp ->
        if (resp.status == 307) {
            def location = resp.headers.Location
            // log.trace "Redirected to ${location}"
            httpPutJson(
                uri: location,
                body: json,
                headers: [
                    Authorization: "Bearer ${token}"
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

private getStructures() {
    log.debug 'Getting list of Nest structures'

    def names = [:]

    try {
        def data = nestGet('/')
        // log.trace "Got Nest response: ${data}"

        def structures = data.structures;
        state.structureData = structures;
        state.numStructures = structures.size()

        // log.trace "Found ${state.numStructures} structures"

        structures.each { id, st ->
            names[id] = st.name
        }
    } catch(Exception e) {
        log.error 'Error getting nests: ' + e
    }

    state.structureNames = names
    return names.sort { it.value }
}

private getThermostats() {
    log.debug 'Getting list of Nest thermostats'

    def names = [:]

    try {
        def data = nestGet('/')
        // log.trace "Got Nest response: ${data}"

        def devices = data.devices.thermostats;
        state.thermostatData = devices;
        state.numThermostats = devices.size()

        // log.trace "Found ${state.numThermostats} thermostats"
        def structureId = settings.structure

        devices.findAll { id, therm ->
            therm.structure_id == structureId
        }.each  { id, therm ->
            names[id] = therm.name
        }
    } catch(Exception e) {
        log.error 'Error getting nests: ' + e
    }

    state.thermostatNames = names
    return names.sort { it.value }
}

private isAuthorized() {
    // log.trace "Is authorized? token=${state.accessToken}"
    if (state.accessToken == null) {
        // log.trace "No token"
        return false
    }

    return true
}

private disconnect() {
    log.debug 'Disconnected from Nest. User must reauthorize.'

    state.connected = 'lost'
    state.accessToken = null
}

private isConnected() {
    if (state.connected == null) {
        state.connected = 'warn'
    }
    return state.connected?.toString() ?: 'lost'
}

private connected() {
    state.connected = 'full'
    state.reAttemptPoll = 0
}

private stringifyQuery(params) {
    return params.collect { k, v ->
        "${k}=${URLEncoder.encode(v.toString())}"
    }.sort().join("&")
}
