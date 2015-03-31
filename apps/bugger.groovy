definition(
    name: "bug reporter",
    namespace: "625alex",
    author: "Alex Malikov",
    description: "n/a",
    category: "My Apps",
    iconUrl: "http://action-dashboard.github.io/icon.png",
    iconX2Url: "http://action-dashboard.github.io/icon.png",
    oauth: true)


preferences {
	page(name: "selectDevices", install: true, uninstall: true) {
		section() {
			href "preferences", title: "Preferences"
		}
    }
	
	page(name: "preferences", title: "preferences")
}

def preferences() {
	//def title = "${location?.name}"
	dynamicPage(name: "preferences", title: "Preferences", install: false) {
	
		section() {
			//label title: "Title", required: false, defaultValue: title
			//label title: "Title", required: false, defaultValue: "${location?.name}"
			label title: "Title", required: false, defaultValue: "${location != null ? location.name : "none"}"
		}
		section() {
			input "weather", "device.smartweatherStationTile", title: "Weather...", multiple: true, required: false
		}
		
		section() {
			input "theme", title: "Theme", "enum", multiple: false, required: true, defaultValue: "default", options: [default: "Metro (default)", slate: "Slate", quartz: "Quartz", onyx: "Onyx", cobalt: "Cobalt"]
			input "tileSize", title: "Tile Size", "enum", multiple: false, required: true, defaultValue: "Medium", options: ["Small", "Medium", "Large"]
			input "fontSize", title: "Font Size", "enum", multiple: false, required: true, defaultValue: "Normal", options: ["Normal", "Larger", "Largest"]
			input "dropShadow", title: "Drop Shadow", "bool", required: true, defaultValue: false
		}
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	unschedule()
	
	initialize()
}

def initialize() {
	log.debug "initialized with settings: ${settings}"
	
    weatherRefresh()
	runEvery30Minutes(weatherRefresh)
}

def weatherRefresh() {
	log.debug "refreshing weather"
	weather?.refresh()
}
