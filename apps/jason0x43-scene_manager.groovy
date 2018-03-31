/**
 * Scene Manager
 *
 * Author:  Jason Cheatham <j.cheatham@gmail.com>
 * Date:    2018-03-24
 * Version: 1.0
 *
 * Manage Scenes
 */

definition(
    name: "Scene Manager",
    namespace: "jason0x43",
    author: "Jason Cheatham",
    description: "Create scenes",
    singleInstance: true,
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-BigButtonsAndSwitches@2x.png"
)

preferences {
    page(
        name: 'mainPage',
        title: 'Installed Scenes',
        install: true,
        uninstall: true,
        submitOnChange: true
    ) {
        section {
            app(
                name: 'scenes',
                appName: 'Scene',
                namespace: 'jason0x43',
                title: 'New Scene',
                multiple: true
            )
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    log.debug "There are ${childApps.size()} child smartapps"
    childApps.each { child ->
        log.debug("child app: ${child.label}")
    }
}