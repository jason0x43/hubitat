/**
 * Scene
 *
 * Author:  Jason Cheatham <j.cheatham@gmail.com>
 * Date:    2018-03-24
 * Version: 1.0
 *
 * Based on Scene Machine by Todd Wackford
 * https://github.com/twack/smarthings-apps/blob/master/scene-machine.app.groovy
 *
 * This is a customized version of Scene Machine that automatically creates a
 * switch to trigger the scene and that can handle color temperature as well as
 * light level.
 */

definition(
    name: 'Scene',
    namespace: 'jason0x43',
    parent: 'jason0x43:Scene Manager',
    author: 'j.cheatham@gmail.com',
    description: 'Create a scene from a set of switches.',
    iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png'
) 
 
preferences {
    section('Select switches to control...') {
       input 'switches', 'capability.switch', multiple: true
       input 'useColorTemp', 'bool', title: 'Manage color temp?', defaultValue: false
       input 'shouldUpdate', 'bool', title: 'Update scene?', defaultValue: false
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    // Create a switch to activate the scene
    def child = createChildDevice(app.label)
    subscribe(child, 'switch.on', setScene)
    
    if (shouldUpdate) {
        updateScene()
    }
}

def uninstalled() {
    log.debug "Uninstalled with settings: ${settings}"

    def childId = getDeviceID()
    unsubscribe()
    deleteChildDevice(childId)
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    
    // Create a switch to activate the scene
    def child = createChildDevice(app.label)
    subscribe(child, 'switch.on', setScene)
    
    if (shouldUpdate) {
        updateScene()
    }
}

def setScene(evt) {
    log.debug('Setting scene')
    
    def i = 0
    for (myData in state.lastSwitchData) {
        def switchName  = myData.switchName
        def switchType  = myData.switchType
        def switchState = myData.switchState
        def level = ''
        def colorTemp = ''
        
        if (myData.dimmerValue != 'null') {
           level = myData.dimmerValue.toInteger()
        } else {
           level = 0
        }
        
        if (myData.colorTemp && myData.colorTemp != 'null') {
            colorTemp = myData.colorTemp.toInteger()
        } else {
            colorTemp = 0
        }
        
        log.info "switchName: $switchName"
        log.info "switchType: $switchType"
        log.info "switchState: $switchState"
        log.info "level: $level"
        log.info "colorTemp: $colorTemp"

        if (switchState == 'on') {
            switches[i].on()
            
            if (level > 0) {
                switches[i].setLevel(level)
            }
        
            if (useColorTemp && colorTemp > 0) {
                switches[i].setColorTemperature(colorTemp)
            }
        }
        
        if (switchState == 'off') {
            switches[i].off()
        }

        i++
        log.info 'Device setting is Done-------------------'
    }
}

private updateScene() {
    log.debug 'Updating scene'

    def count = 0
    for (s in switches) {
        log.debug "Refreshing ${s}"
        s.refresh()
        count++
    }
    
    // Wait for refresh data to arrive.
    pauseExecution(1000)
    
    state.lastSwitchData = [count]
    
    def i = 0
    for (mySwitch in switches) {
        def switchName  = mySwitch.device.toString()
        def switchType  = mySwitch.name.toString()
        def switchState = mySwitch.latestValue('switch').toString()

        // the latestValue calls below return null if the devices
        // don't have the necessary capabilities
        def level = mySwitch.latestValue('level').toString()
        def colorTemp = mySwitch.latestValue('colorTemperature').toString()
        
        state.lastSwitchData[i] = [
            switchName: switchName,
            switchType: switchType,
            switchState: switchState,
            dimmerValue: level,
            colorTemp: colorTemp
        ]

        log.debug "SwitchData: ${state.lastSwitchData[i]}"
        i++   
    }  
}

private createChildDevice(deviceLabel) {
    app.updateLabel(deviceLabel)
    def child = getChildDevice(getDeviceID())

    if (!child) {
        child = addChildDevice(
            'jason0x43',
            'Virtual Momentary Switch',
            getDeviceID(),
            null,
            [name: getDeviceID(), label: deviceLabel, completedSetup: true]
        )
        log.info "Created switch [${child}]"
    } else {
    	child.label = app.label
        child.name = app.label
        log.info "Switch renamed to [${app.label}]"
    }
    
    return child
}

private getDeviceID() {
    return "SBSW_${app.id}"
}