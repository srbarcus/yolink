/**
 *  YoLinkService
 *  © 2021 Steven Barcus. All rights reserved.
 *  LinkedIn profile: www.linkedin.com/in/yracine
 *  Refer to readme file for installation instructions.
 *     http://github.com/yracine/device-type.myFlair/blob/master/README.md
 * 
 *  Developer retains all right, title, copyright, and interest, including all copyright, patent rights,
 *  trade secret in the Background technology. May b subject to consulting fees under an Agreement 
 *  between the Developer and the Customer. Developer grants a non exclusive perpetual license to use
 *  the Background technology in the Software developed for and delivered to Customer under this
 *  Agreement. However, the Customer shall make no commercial use of the Background technology without
 *  Developer's written consent.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * 
 * Software Distribution is restricted and shall be done only with Developer's written approval.
 *
**/
definition(
    name: "${get_APP_NAME()}",
    namespace: "sbarcus",
    author: "Steven Barcus",
    description: "Connect your YoLink devices to Hubitat.",
    oauth: false,    
    category: "My Apps",
    iconUrl: "",
    iconX2Url: ""
    //iconUrl: "${getCustomImagePath()}yolink.png",
    //iconX2Url: "${getCustomImagePath()}yolink.png"
)

private def get_APP_VERSION() {
	return "1.0.0"
}
preferences {

	page(name: "about", title: "About", nextPage: "otherSettings")
    //page(name: "auth", title: "YoLink Authorization", content:"authPage", nextPage:"otherSettings")
	//page(name: "auth", title: "YoLink ", content:"authPage", nextPage:"structureList")
	//page(name: "structureList",title: "Flair locations", content:"structureList",nextPage: "puckDeviceList")   
	//page(name: "puckDeviceList", title: "Flair Puck devices", content:"puckDeviceList",nextPage: "flairVentList")
	//page(name: "flairVentList", title: "Flair Vent devices", content:"flairVentList",nextPage: "flairTstatList")
	//page(name: "flairTstatList", title: "Flair Thermostats devices", content:"flairTstatList",nextPage: "flairHvacList")
	//page(name: "flairHvacList", title: "Flair HVAC devices", content:"flairHvacList",nextPage: "otherSettings")
	page(name: "otherSettings", title: "YoLink App User Access Credentials", content:"yolink_credentials", install:true)
	//page(name: "watchdogSettingsSetup")    
	//page(name: "reportSettingsSetup")    
}

def about() {

 	dynamicPage(name: "about", title: pageTitle("About"),install: false, uninstall: true) {
 		section("") {	
			paragraph image:"${getCustomImagePath()}yolink.png", boldTitle("${get_APP_NAME()}, this smartapp connects your YoLink devices to Hubitat via cloud-to-cloud integration")
			paragraph boldTitle("Version ${get_APP_VERSION()}") 
			paragraph "If you like this smartapp, please support the developer via PayPal by clicking on this Paypal button:" 
				href url:"https://www.paypal.me/BarcusSoftware",
	 				title:"Paypal donation..."
			paragraph boldTitle ("© 2021 Steven Barcus. All rights reserved.")
				//href url:"https://github.com/yracine/device-type.myflair", style:"embedded", required:false, title:"More information...", 
				//	description: "https://github.com/yracine/flairdevices"
		}
	}        
}

def yolink_credentials() {    
	dynamicPage(name: "yolink_credentials", title: pageTitle("YoLink Access Credentials"), install: true, uninstall: false) {		
		section(sectionTitle("User Access Credentials from YoLink mobile app")) {
		    input "UAID", "text", title: boldTitle("UAID:"), required: true	    	
            input "secret_key", "text", title: boldTitle("Secret Key:"), required: true		
       
	    }
        section("") {	
			paragraph "Click here for information on obtaining your User Access Credentials from the YoLink mobile app" 
				href url:"https://www.paypal.me/BarcusSoftware",
	 				title:"User Access Credentials"			
		}
  } 
}

def otherSettings() {
	dynamicPage(name: "otherSettings", title: pageTitle("Other Settings"), install: true, uninstall: false) {
		section(smallTitle("Polling at which interval in minutes (range=[5,10,15,30],default=10 min.)?")) {
			input "givenInterval", "enum", title:boldTitle("Interval"), required: false, options:[5,10,15,30]
		}
		section("Handle/Notify any exception proactively [default=false, you will not receive any exception notification]") {
			input "handleExceptionFlag", "bool", title: "Handle exceptions proactively?", required: false
		}           
        section(sectionTitle("Logging")) {
			input "detailedNotif", "bool", title: "Detailed Logging & Notifications?", required:false
    		input "logFilter", "enum", title: "log filtering [Level 1=ERROR only,2=<Level 1+WARNING>,3=<2+INFO>,4=<3+DEBUG>,5=<4+TRACE>]?",required:false, options:[1,2,3,4,5]
        }
		section(sectionTitle("Summary Report Settings")) {
			href(name: "toReportSettingsSetup", page: "reportSettingsSetup",required:false,  description: "Optional",
				title: "Summary Reports via notifications/Ask Alexa", image: "${getCustomImagePath()}reports.jpg" ) 
		}
	}
}

def authPage() {
    atomicState?.oauthTokenProvided = false
  	
	def description = "Required"
	def uninstallAllowed = false

	if (atomicState?.oauthTokenProvided) {
		description = "You are already connected to YoLink. You just need to tap the upper right 'Next' button.\n\nIf text in red, please re-login at Flair by pressing here as there was a connection error."
		uninstallAllowed = true
	} else {
		description = "YoLink Connection Required, press here for access credentials prompt."
	}

	if (!atomicState?.oauthTokenProvided) {

		return dynamicPage(name: "auth", title: pageTitle("Login to YoLink"), nextPage:null, uninstall:uninstallAllowed, submitOnChange: true) {
			section(){
				paragraph "Tap below to log in to the YoLink portal and authorize Hubitat access. Be sure to scroll down on page 2 and press the 'Allow' button."
				href url:redirectUrl, style:"external", required:true, title:"YoLink Connection>", description:description
			}
		}

	} else {

		return dynamicPage(name: "auth", title:  pageTitle("Login to YoLink"), nextPage:"structureList", uninstall:uninstallAllowed,submitOnChange: true) {
			section(){
				paragraph "Tap Next to continue to setup your YoLink devices."
				href url:redirectUrl, style:"external", state:"complete", title:"YoLink Connection Status>", description:description
			}
		}

	}

    atomicState?.oauthTokenProvided = true

}

def watchdogSettingsSetup() {
	dynamicPage(name: "watchdogSettingsSetup", title: pageTitle("Scheduler's Watchdog Settings "), uninstall: false) {
		section("Watchdog options: the watchdog should be a single polling device amongst the choice of sensors below. The watchdog needs to be regularly polled every 5-10 minutes and will be used as a 'heartbeat' to reschedule if needed.") {
			input (name:"tempSensor", type:"capability.temperatureMeasurement", title: "What do I use as temperature sensor to restart smartapp processing?",
				required: false, description: "Optional Watchdog- just use a single polling device")
			input (name:"motionSensor", type:"capability.motionSensor", title: "What do I use as a motion sensor to restart smartapp processing?",
				required: false, description: "Optional Watchdog -just use a single polling device")
			input (name:"energyMeter", type:"capability.powerMeter", title: "What do I use as energy sensor to restart smartapp processing?",
				required: false, description: "Optional Watchdog-  just use a single polling device")
			input (name:"powerSwitch", type:"capability.switch", title: "What do I use as Master on/off switch to restart smartapp processing?",
				required: false, description: "Optional Watchdog - just use a single  polling device")
		}
		section {
			href(name: "toOtherSettingsPage", title: "Back to Other Settings Page", page: "otherSettings")
		}
	}
}   

def reportSettingsSetup() {
	dynamicPage(name: "reportSettingsSetup", title: pageTitle("Summary Report Settings "), uninstall: false) {
		section("Report options: Daily/Weekly Summary reports are sent by notifications (right after midnight, early morning) and/or can be verbally given by Ask Alexa") {
			input (name:"puckDaySummaryFlag", title: "include Past Day Summary Report for your pucks [default=false]?", type:"bool",required:false)
			input (name:"hvacDaySummaryFlag", title: "include Past Day Summary Report for your hvac units [default=false]?", type:"bool",required:false)
			input (name:"ventDaySummaryFlag", title: "include Past Day Summary Report for your vents [default=false]?", type:"bool",required:false)
			input (name:"puckWeeklySummaryFlag", title: "include Weekly Summary Report for your pucks [default=false]?", type:"bool",required:false)
			input (name:"hvacWeeklySummaryFlag", title: "include Weekly Summary Report for your hvac units [default=false]?", type:"bool", 	required:false)
			input (name:"ventWeeklySummaryFlag", title: "include Weekly Summary Report for your vents [default=false]?", type:"bool", required:false)
		}
		section {
			href(name: "toOtherSettingsPage", title: "Back to Other Settings Page", page: "otherSettings")
		}
	}
}   




def structureList() {
//    settings.logFilter=5
//    settings.detailedNotif=true    
	traceEvent(settings.logFilter,"structureList>begin", detailedNotif)
	def structures=getObject("structures")
    
	int structureCount=structures.size()    
	dynamicPage(name: "structureList", title: sectionTitle("Select Your Location to be exposed to Hubitat ($structureCount found)."), uninstall: true) {
		section(""){
			paragraph "Tap below to see the list of Locations available in your Flair account. "
			input(name: "structure", title:"", type: "enum", required:true,  description: "Tap to choose", options:structures)
		}
	}
}

def puckDeviceList() {
    
	def struct_info  = structure.tokenize('.')
	def structureId = struct_info.last()
	traceEvent(settings.logFilter,"puckDeviceList>About to call getStructurePucks() with structureId= $structureId", detailedNotif)
	def pucksList = getObject("structures", structureId, "pucks")
	int puckCount= pucksList.size()    
	traceEvent(settings.logFilter,"puckDeviceList>device list: $pucksList, count=$puckCount", detailedNotif)

	def p = dynamicPage(name: "puckDeviceList", title: sectionTitle("Select Your Puck Device(s) to be exposed to Hubitat ($puckCount found)."), uninstall: true) {
		section(""){
			paragraph image: "${getCustomImagePath()}puckIcon.jpg","Tap below to see the list of Flair Puck Devices available in your Flair account."
			input(name: "pucks", title:"", type: "enum", required:false, multiple:true, description: "Tap to choose", options:pucksList)
		}
	}

	traceEvent(settings.logFilter,"puckDeviceList>list p: $p",detailedNotif)
	return p
}

def flairTstatList() {
	traceEvent(settings.logFilter,"flairTstatList>begin", detailedNotif)

	def struct_info  = structure.tokenize('.')
	def structureId = struct_info.last()

	def tstats = getObject("structures", structureId, "thermostats")

	int tstatCount= tstats.size()    
	traceEvent(settings.logFilter,"flairTstatList>device list: $tstats", detailedNotif)

	def p = dynamicPage(name: "flairTstatList", title: sectionTitle("Select Your Flair Thermostats to be exposed to Hubitat ($tstatCount found)."), uninstall: true) {
		section(""){
        
			paragraph image: "${getCustomImagePath()}flairTstat.jpg", "Tap below to see the list of Flair Tstats available in your Flair account. The thermostats will be exposed to Hubitat in read-only mode."
			input(name: "thermostats", title:"", type: "enum", required:false, multiple:true, description: "Tap to choose", options: tstats)
		}
	}

	traceEvent(settings.logFilter,"flairTstatList>list p: $p",detailedNotif)
	return p
}

def flairHvacList() {
	traceEvent(settings.logFilter,"flairHvacList>begin", detailedNotif)

	def struct_info  = structure.tokenize('.')
	def structureId = struct_info.last()
     	
	def hvacs = getObject("structures", structureId, "hvac-units")
	int hvacsCount= hvacs.size()    

	traceEvent(settings.logFilter,"flairHvacList>device list: $hvacs, count=$hvacsCount", detailedNotif)

	def p = dynamicPage(name: "flairHvacList", title: sectionTitle("Select Your Flair HVAC unit(s) to be exposed to Hubitat ($hvacsCount found)."), uninstall: true) {
		section(""){
			paragraph image: "${getCustomImagePath()}flairUnit.jpg","Tap below to see the list of Flair HVAC units (windows, splits, heaters, etc) available in your Flair account. "
			input(name: "hvacUnits", title:"", type: "enum", required:false, multiple:true, description: "Tap to choose", options:hvacs)
		}
	}

	traceEvent(settings.logFilter,"flairHvacList>list p: $p",detailedNotif)
	return p
}


def flairVentList() {
	traceEvent(settings.logFilter,"flairVentList>begin", detailedNotif)

	def struct_info  = structure.tokenize('.')
	def structureId = struct_info.last()
     
	traceEvent(settings.logFilter,"flairVentsList>About to call getStructureVents() with structureId= $structureId", detailedNotif)
	def vents = getObject("structures", structureId, "vents")
	int ventCount= vents.size()
	traceEvent(settings.logFilter,"flairVentList>device list: $vents, count=$ventCount", detailedNotif)

	def p = dynamicPage(name: "flairVentList", title: sectionTitle("Select Your Flair Vents to be exposed to Hubitat ($ventCount found)."), uninstall: true) {
		section(""){
			paragraph image: "${getCustomImagePath()}flairVent.jpg","Tap below to see the list of Flair Vents available in your Flair account. "
			input(name: "vents", title:"", type: "enum", required:false, multiple:true, description: "Tap to choose", options: vents)
		}
	}

	traceEvent(settings.logFilter,"flairVentList>list p: $p",detailedNotif)
	return p
}


def setParentAuthTokens(auth_data) {
	if (auth_data.authexptime > atomicState.authexptime) {
		if (handleException) {
/*
			For Debugging purposes, due to the fact that logging is not working when called (separate thread)
			traceEvent(settings.logFilter,"setParentAuthTokens>begin auth_data: $auth_data",detailedNotif)
*/
			traceEvent(settings.logFilter,"setParentAuthTokens>begin auth_data: $auth_data",detailedNotif)
		}   
		save_auth_data(auth_data)        
		refreshAllChildAuthTokens()
		if (handleException) {
/*
			For Debugging purposes, due to the fact that logging is not working when called (separate thread)
			send("setParentAuthTokens>atomicState =$atomicState")
*/
			traceEvent(settings.logFilter,"setParentAuthTokens>setParentAuthTokens>atomicState auth=$atomicState",detailedNotif)
		}            
	}        

}

void save_auth_data(auth_data) {
	atomicState?.refresh_token = auth_data?.refresh_token
	atomicState?.access_token = auth_data?.access_token
	atomicState?.expires_in=auth_data?.expires_in
	atomicState?.token_type = auth_data?.token_type
	atomicState?.authexptime= auth_data?.authexptime
	traceEvent(settings.logFilter,"save_auth_data>atomicState auth=$atomicState",detailedNotif)
}
def refreshAllChildAuthTokens() {
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	traceEvent(settings.logFilter,"refreshAllChildAuthTokens>begin updating children with ${atomicState.auth}")
*/

	def children= getChildDevices()
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	traceEvent(settings.logFilter,"refreshAllChildAuthtokens> refreshing ${children.size()} thermostats",detailedNotif)
*/

	children.each { 
/*
		For Debugging purposes, due to the fact that logging is not working when called (separate thread)
		traceEvent(settings.logFilter,"refreshAllChildAuthTokens>begin updating $it.deviceNetworkId with ${$atomicState.auth}",detailedNotif)
*/
    	it.refreshChildTokens(atomicState) 
	}
}
def refreshThisChildAuthTokens(child) {

/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	traceEvent(settings.logFilter,"refreshThisChildAuthTokens>begin child id: ${child.device.deviceNetworkId}, updating it with ${atomicState}", detailedNotif)
*/
	child.refreshChildTokens(atomicState)

/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	traceEvent(settings.logFilter,"refreshThisChildAuthTokens>end",detailedNotif)
*/
}



boolean refreshParentTokens() {

	if (isTokenExpired()) {
		if (refreshAuthToken()) {
			refreshAllChildAuthTokens()
			return true            
		}		        
	} else {
		refreshAllChildAuthTokens()
		return true            
	}    
	return false    
    
}

def getObject( objectType, objectId="", relatedType="") {
//	settings.detailedNotif=true // set to true initially
//	settings.logFilter=4    
    
	traceEvent(settings.logFilter,"getObject>begin fetching $objectType using relatedType= $relatedType...", detailedNotif)
	def msg
 	def TOKEN_EXPIRED=401
   
	def requestBody = "/api/${objectType}"
    
    
	if (relatedObjectId !="" && relatedObjectId != null) {
		requestBody=requestBody + "/${relatedObjectId}"    
	} else if (objectId !="" && objectId != null) {
		requestBody=requestBody + "/${objectId}"    
	}    

	if (relatedType) {
		requestBody=requestBody + "/${relatedType}"  
	}    
	traceEvent(settings.logFilter,"getObject>requestBody=${requestBody}", detailedNotif)
	def deviceListParams = [
		uri: "${get_URI_ROOT()}",
		path: "${requestBody}",
		headers: ["Content-Type": "text/json", "Authorization": "Bearer ${atomicState.access_token}", "Accept": "${get_APPLICATION_VERSION()}"],
		query: [format: 'json']
	]

	traceEvent(settings.logFilter,"getObject>device list params: $deviceListParams",detailedNotif)
//	log.debug "getObject>device list params: $deviceListParams"

	def objects = [:]
	def objectData=[]   
	def responseValues=[]
	try {
		httpGet(deviceListParams) { resp ->

			traceEvent(settings.logFilter, "getObject>resp.data=${resp.data}",detailedNotif)
//			log.debug "getObject>resp.data=${resp.data}"
			traceEvent(settings.logFilter,"getObject>resp.status=${resp.status}", detailedNotif)
			if (resp.status == 200) {

				if (resp.data.data instanceof Collection) { 
					traceEvent(settings.logFilter,"about to loop on $objectType for objectId=$objectId", detailedNotif)
					responseValues=resp.data.data                    
				} else {
					traceEvent(settings.logFilter,"found single $objectType for objectId=$objectId", detailedNotif)
					responseValues[0]=resp.data.data                    
				}                
                
				responseValues.each { object ->
					if (object.attributes.name) {
						traceEvent(settings.logFilter, "getObject>found ${object.attributes.name}...", detailedNotif)
//						log.debug "getObject>found ${object.attributes.name}.."
						def name = object.attributes.name.minus('[').minus(']')                            
						def dni = [ app.id, name, object.id ].join('.')
						objectData << object // save all structures for reference later                   
						objects[dni] = name
						traceEvent(settings.logFilter, "getObject>objects[${dni}]= ${objects[dni]}", detailedNotif)
//						log.debug "getObject>objects[dni]= ${objects[dni]}"
					}
				}                   
			} else {
				traceEvent(settings.logFilter,"getObject>http status: ${resp.status}",detailedNotif)

				//refresh the auth token
				if (resp.status == TOKEN_EXPIRED) {
					if (handleException) {            
						traceEvent(settings.logFilter,"http status=${resp.status}: need to refresh your auth_token, about to call refreshAuthToken() (resp status= ${resp.data.status.code})",
							detailedNotif)                        
					}                        
					traceEvent(settings.logFilter,"getObject>Need to refresh your auth_token!, about to call refreshAuthToken()",detailedNotif)
					refreshAuthToken()
                    
				} else {
					if (handleException) {            
						send "http status=${resp.status}: authentication error, invalid authentication method, lack of credentials, (resp status= ${resp.data.status.code})"
					}                        
					traceEvent(settings.logFilter,"getObject>http status=${resp.status}: authentication error, invalid authentication method, lack of credentials,  (resp status= ${resp.data.status.code})",
						detailedNotif)                    
				}
			}
		}        
	} catch (java.net.UnknownHostException e) {
		msg ="getObject>Unknown host - check the URL " + deviceListParams.uri
		traceEvent(settings.logFilter,msg, true, get_LOG_ERROR())   
	} catch (java.net.NoRouteToHostException t) {
		msg= "getObject>No route to host - check the URL " + deviceListParams.uri
		traceEvent(settings.logFilter,msg, true,get_LOG_ERROR())   
	} catch (e) {
		msg= "getObject>exception $e while getting $objectType list" 
		if (handleException) {            
			traceEvent(settings.logFilter,msg, true, get_LOG_ERROR())   
		}                        
	}

	if (relatedType !="") {
		state?."${relatedType}"=objectData // save all objects for futher references
		msg = "getObject>state.'${relatedType}'=" + state."${relatedType}"       
		traceEvent(settings.logFilter,msg, detailedNotif)
//		log.debug(msg)
	} else {
		state?."${objectType}"=objectData // save all objects for further references
		msg = "getObject>state.'${objectType}'=" + state."${objectType}"       
		traceEvent(settings.logFilter,msg, detailedNotif)
//		log.debug(msg)
 
	}    
	traceEvent(settings.logFilter,"getObject>${objectType} with relatedType=${relatedType}, return= $objects", detailedNotif)
//	log.debug "getObject>${objectType} return= $objects"
	return objects
}

def updateObjects(child, objectType) {
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	traceEvent(settings.logFilter,"updateStructures>begin child id: ${child.device.deviceNetworkId}, updating it with ${atomicState?.structures}", detailedNotif)
*/
	def objectsToBeUdpated=state?."${objectType}"
	child.updateChildData(objectsToBeUpdated)

/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	traceEvent(settings.logFilter,"updateStructures>end child id: ${child.device.deviceNetworkId}, updating it with ${atomicState?.structures}", detailedNotif)
*/
}

def updateStructures(child) {
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	traceEvent(settings.logFilter,"updateStructures>begin child id: ${child.device.deviceNetworkId}, updating it with ${atomicState?.structures}", detailedNotif)
*/
	child.updateStructures(state?.structures)

/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	traceEvent(settings.logFilter,"updateStructures>end child id: ${child.device.deviceNetworkId}, updating it with ${atomicState?.structures}", detailedNotif)
*/
}

def updateZones(child) {
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	traceEvent(settings.logFilter,"updateZones>begin child id: ${child.device.deviceNetworkId}, updating it with ${state?.zones}", detailedNotif)
*/
	child.updateZones(state?.zones)

/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
	traceEvent(settings.logFilter,"updateZones>end child id: ${child.device.deviceNetworkId}, updating it with ${state?.zones}", detailedNotif)
*/
}

def updateRooms(child) {
/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
*/
	traceEvent(settings.logFilter,"updateRooms>begin child id: ${child.device.deviceNetworkId}, updating it with ${state?.rooms}", detailedNotif)
	child.updateRooms(state?.rooms)

/*
	For Debugging purposes, due to the fact that logging is not working when called (separate thread)
*/
	traceEvent(settings.logFilter,"updateRooms>end child id: ${child.device.deviceNetworkId}, updating it with ${state?.rooms}", detailedNotif)
}



def refreshAuthToken() {	
}


def installed() {
	settings.detailedNotif=true // set to true initially
	settings.logFilter=4    
	traceEvent(settings.logFilter,"Installed with settings: ${settings}", detailedNotif)

	initialize()
}

 def initialize() {
    
	traceEvent(settings.logFilter,"initialize begin...", detailedNotif)
	atomicState?.exceptionCount=0    
	def msg
	atomicState?.poll = [ last: 0, rescheduled: now() ]
    
	Integer delay = (givenInterval) ? givenInterval.toInteger(): 10 // By default, do it every 10 min.
    
	//Subscribe to different events (ex. sunrise and sunset events) to trigger rescheduling if needed
//	subscribe(location, "askAlexaMQ", askAlexaMQHandler)    
//	subscribe(location, "sunrise", rescheduleIfNeeded)
//	subscribe(location, "sunset", rescheduleIfNeeded)
//	subscribe(location, "mode", rescheduleIfNeeded)
//	subscribe(location, "sunriseTime", rescheduleIfNeeded)
//	subscribe(location, "sunsetTime", rescheduleIfNeeded)

//	delete_child_devices()	
//	create_child_tstats()
//	create_child_pucks()
//	create_child_vents()
//	create_child_hvacs()

	traceEvent(settings.logFilter,"initialize>polling delay= ${delay}...", detailedNotif)
//	rescheduleIfNeeded()   
}

def logsOff(){
    log.warn "debug logging disabled..."
    app.updateSetting("detailedNotif",[value:"false",type:"bool"])
} 



def updated() {
//	settings.detailedNotif=true // set to true initially
//	settings.logFilter=4    
	traceEvent(settings.logFilter,"Updated with settings: ${settings}", detailedNotif)

	unsubscribe()
	try {    
		unschedule()
	} catch (e) {
		traceEvent(settings.logFilter,"updated>exception $e, continue processing", detailedNotif)    
	}    
	initialize()
	if (settings.detailedNotif) runIn(900,"logsOff")    

}

def uninstalled() {
	delete_child_devices(getChildDevices())
//	revokeAccessToken()     
    atomicState.accessToken = null
    state.accessToken = null        
}


def terminateMe() {
	try {
		app.delete()
	} catch (Exception e) {
		traceEvent(settings.logFilter, "terminateMe>failure, exception $e", get_LOG_ERROR(), true)
	}
}

boolean isST() { 
    return (getHub() == "SmartThings") 
}

private getHub() {
    def result = "SmartThings"
    if(state?.hub == null) {
        try { [value: "value"]?.encodeAsJson(); } catch (e) { result = "Hubitat" }
        state?.hub = result
    }
//    log.debug "hubPlatform: (${state?.hub})"
    return state?.hub
}    

def purgeChildDevice(childDevice) {
	def dni = childDevice.device.deviceNetworkId
	def foundThermostat=thermostats.find {dni}    
	if (foundThermostat) {
		thermostats.remove(dni)
//		app.updateSetting("thermostats", thermostats ? thermostats : [])
		app.updateSetting("thermostats", [value:thermostats, type:"enum"])
	} 
	def foundPuck=pucks.find {dni}    
	if (foundPuck) {
		pucks.remove(dni)
//		app.updateSetting("pucks", pucks ? pucks : [])
		app.updateSetting("pucks", [value: pucks, type:"enum"])
	}	
	def foundVent=vents.find {dni}    
	if (foundVent) {
		vents.remove(dni)
//		app.updateSetting("vents", vents ? vents : [])
		app.updateSetting("vents", [value: vents, type:"enum"])
	}	
	def foundHvacUnit=hvacUnits.find {dni}    
	if (foundHvacUnit) {
		hvacUnits.remove(dni)
//		app.updateSetting("hvacUnits", hvacUnits ? hvacUnits : [])
		app.updateSetting("hvacUnits", [value: hvacUnits, type:"enum"])
	}	
    
	if (getChildDevices().size <= 1) {
		traceEvent(settings.logFilter,"purgeChildDevice>no more devices to poll, unscheduling and terminating the app", true,get_LOG_ERROR())
		unschedule()
		atomicState.authToken=null
		runIn(1, "terminateMe")
	}
}


def offHandler(evt) {
	traceEvent(settings.logFilter,"$evt.name: $evt.value", detailedNotif)
}



def rescheduleHandler(evt) {
	traceEvent(settings.logFilter,"$evt.name: $evt.value", detailedNotif)
	rescheduleIfNeeded()		
}


private def delete_child_devices(delete=null) {
	def deletePucks=[], deleteTstats=[], deleteVents=[], deleteHvacs=[]

	if (delete) {
        	delete.each {
     			traceEvent(settings.logFilter,"delete_child_devices>about to delete all pucks, vents, tstats, and hvacUnits, processing ${it.displayName}", detailedNotif)
            		deleteChildDevice(it.deviceNetworkId)        
		}            
        	return        
    	}        
    
	// Delete any that are no longer in settings

	if(!thermostats) {
		deleteTstats = getChildDevices().findAll {  (it.getName().contains(getTstatChildName())) }
 		traceEvent(settings.logFilter,"delete_child_devices>about to delete all flair thermostats", detailedNotif)
 	} else {
		deleteTstats = getChildDevices().findAll { ((!thermostats.contains(it.deviceNetworkId)) && (it.getName().contains(getTstatChildName()))) }
 		traceEvent(settings.logFilter,"delete_child_devices>about to delete ${deleteTstats.size()} flair thermostats", detailedNotif)
 	}
 
	traceEvent(settings.logFilter,"delete_child_devices>about to delete ${deleteTstats.size()} thermostat devices", detailedNotif)

	deleteTstats.each { 
		try {    
			deleteChildDevice(it.deviceNetworkId) 
		} catch (e) {
			traceEvent(settings.logFilter,"delete_child_devices>exception $e while deleting flair thermostat ${it.deviceNetworkId}", detailedNotif, get_LOG_ERROR())
		}   
	}


	if(!pucks) {
		deletePucks = getChildDevices().findAll {  (it.getName().contains(getPuckChildName())) }
		traceEvent(settings.logFilter,"delete_child_devices>about to delete all flair pucks", detailedNotif)
	} else {
		deletePucks = getChildDevices().findAll { ((!pucks.contains(it.deviceNetworkId)) && (it.getName().contains(getPuckChildName()))) }
	}        
	traceEvent(settings.logFilter,"delete_child_devices>about to delete ${deletePucks.size()} puck devices", detailedNotif)

	deletePucks.each { 
		try {    
			deleteChildDevice(it.deviceNetworkId) 
		} catch (e) {
			traceEvent(settings.logFilter,"delete_child_devices>exception $e while deleting puck device ${it.deviceNetworkId}", detailedNotif, get_LOG_ERROR())
		}   
	}

	
	if(!vents) {
		deleteVents = getChildDevices().findAll {(it.getName().contains(getVentChildName()))}
		traceEvent(settings.logFilter,"delete_child_devices>about to delete all flair vents", detailedNotif)
	} else {

		deleteVents = getChildDevices().findAll { ((!vents.contains(it.deviceNetworkId)) && (it.getName().contains(getVentChildName())))}
	}        
	traceEvent(settings.logFilter,"delete_child_devices>about to delete ${deleteVents.size()} flair vents", detailedNotif)

	deleteVents.each { 
		try {    
			deleteChildDevice(it.deviceNetworkId) 
		} catch (e) {
			traceEvent(settings.logFilter,"delete_child_devices>exception $e while deleting flair vent ${it.deviceNetworkId}", detailedNotif, get_LOG_ERROR())
		}   
	}

	if(!hvacUnits) {
		deleteHvacs = getChildDevices().findAll { (it.getName().contains(getHvacChildName()))}
		traceEvent(settings.logFilter,"delete_child_devices>about to delete all flair hvac units", detailedNotif)
	} else {
		deleteHvacs = getChildDevices().findAll { ((!hvacUnits.contains(it.deviceNetworkId)) && (it.getName().contains(getHvacChildName())))}
	}        
	traceEvent(settings.logFilter,"delete_child_devices>about to delete ${deleteHvacs.size()} hvac devices", detailedNotif)

	deleteHvacs.each { 
		try {    
			deleteChildDevice(it.deviceNetworkId) 
		} catch (e) {
			traceEvent(settings.logFilter,"delete_child_devices>exception $e while deleting hvac device ${it.deviceNetworkId}", detailedNotif, get_LOG_ERROR())
		}   
	}

	

}


private void create_child_tstats() {

   	int countNewChildDevices =0
	def struct_info  = structure.tokenize('.')
	def structureId = struct_info.last()
	def allChildDevices=getChildDevices()
	def devices = thermostats.collect { dni ->

		def tstat_info  = dni.tokenize('.')
		def thermostatId = tstat_info.last()
 		def name = tstat_info[1]
		def d= allChildDevices.find {it.deviceNetworkId.contains(thermostatId)}               
		traceEvent(settings.logFilter,"create_child_devices>looping thru thermostats, found id $dni", detailedNotif)

		if(!d) {
			def labelName = 'My Flair Tstat ' + "${name}"
			traceEvent(settings.logFilter,"create_child_devices>about to create child device with id $dni, thermostatId = $thermostatId, name=  ${name}", detailedNotif)
			d = addChildDevice(getChildNamespace(), getTstatChildName(), dni, [label: "${labelName}"]) 
			d.initialSetup( getHubitatClientId() , getHubitatClientSecretId(), atomicState.access_token,atomicState.refresh_token,atomicState.token_type,atomicState.authexptime, 
				structureId, thermostatId ) 	// initial setup of the Child Device
			traceEvent(settings.logFilter,"create_child_devices>created ${d.displayName} with id $dni", detailedNotif)
			countNewChildDevices++            
		} else {
			traceEvent(settings.logFilter,"create_child_devices>found ${d.displayName} with id $dni already exists", detailedNotif)
			try {
				if (d.isTokenExpired()) {  // called refreshAllChildAuthTokens when updated
 					refreshAllChildAuthTokens()    
				}
			} catch (e) {
				traceEvent(settings.logFilter,"create_child_devices>exception $e while trying to refresh existing tokens in child $d", detailedNotif,get_LOG_ERROR())
            
			}            
		}

	}

	traceEvent(settings.logFilter,"create_child_devices>created $countNewChildDevices, total=${devices.size()} thermostats", detailedNotif)

}


private void create_child_pucks() {

   	int countNewChildDevices =0
	def struct_info  = structure.tokenize('.')
	def structureId = struct_info.last()
	def allChildDevices=getChildDevices()
	def devices = pucks.collect { dni ->

		def puck_info  = dni.tokenize('.')
		def puckId = puck_info.last()
 		def name = puck_info[1]
		def d= allChildDevices.find {it.deviceNetworkId.contains(puckId)}               
		traceEvent(settings.logFilter,"create_child_devices>looping thru pucks, found id $dni", detailedNotif)

		if(!d) {
			def labelName = 'My Puck ' + "${name}"
			traceEvent(settings.logFilter,"create_child_devices>about to create child device with id $dni, puckId = $puckId, name=  ${name}", detailedNotif)
			d = addChildDevice(getChildNamespace(), getPuckChildName(), dni, [label: "${labelName}"]) 
			d.initialSetup( getHubitatClientId() , getHubitatClientSecretId(), atomicState.access_token,atomicState.refresh_token,atomicState.token_type,atomicState.authexptime, 
				structureId,puckId ) 	// initial setup of the Child Device
			traceEvent(settings.logFilter,"create_child_devices>created ${d.displayName} with id $dni", detailedNotif)
			countNewChildDevices++            
		} else {
			traceEvent(settings.logFilter,"create_child_devices>found ${d.displayName} with id $dni already exists", detailedNotif)
			try {
				if (d.isTokenExpired()) {  // called refreshAllChildAuthTokens when updated
 					refreshAllChildAuthTokens()    
				}
			} catch (e) {
				traceEvent(settings.logFilter,"create_child_devices>exception $e while trying to refresh existing tokens in child $d", detailedNotif)
            
			}            
		}

	}

	traceEvent(settings.logFilter,"create_child_devices>created $countNewChildDevices, total=${devices.size()} pucks", detailedNotif)
}


private void create_child_vents() {

   	int countNewChildDevices =0
	def struct_info  = structure.tokenize('.')
	def structureId = struct_info.last()
	def allChildDevices=getChildDevices()    
	def devices = vents.collect { dni ->

		def vent_info  = dni.tokenize('.')
		def ventId = vent_info.last()
 		def name = vent_info[1]
		def d= allChildDevices.find {it.deviceNetworkId.contains(ventId)}               
		traceEvent(settings.logFilter,"create_child_devices>looping thru vents, found id $dni", detailedNotif)

		if(!d) {
			def labelName = 'My Flair Vent ' + "${name}"
			traceEvent(settings.logFilter,"create_child_devices>about to create child device with id $dni, ventId = $ventId, name=  ${name}", detailedNotif)
			d = addChildDevice(getChildNamespace(), getVentChildName(), dni, [label: "${labelName}"])  
			d.initialSetup( getHubitatClientId() , getHubitatClientSecretId(), atomicState.access_token,atomicState.refresh_token,atomicState.token_type,atomicState.authexptime, 
				structureId,ventId ) 	// initial setup of the Child Device
			traceEvent(settings.logFilter,"create_child_devices>created ${d.displayName} with id $dni", detailedNotif)
			countNewChildDevices++            
		} else {
			traceEvent(settings.logFilter,"create_child_devices>found ${d.displayName} with id $dni already exists", detailedNotif)
			try {
				if (d.isTokenExpired()) {  // called refreshAllChildAuthTokens when updated
 					refreshAllChildAuthTokens()    
				}
			} catch (e) {
				traceEvent(settings.logFilter,"create_child_devices>exception $e while trying to refresh existing tokens in child $d", detailedNotif)
            
			}            
		}

	}

}

private void create_child_hvacs() {

   	int countNewChildDevices =0
	def struct_info  = structure.tokenize('.')
	def structureId = struct_info.last()
	def allChildDevices=getChildDevices()
	def devices = hvacUnits.collect { dni ->

		def hvac_info  = dni.tokenize('.')
		def hvacUnitId = hvac_info.last()
 		def name = hvac_info[1]
		def d= allChildDevices.find {it.deviceNetworkId.contains(hvacUnitId)}               
		traceEvent(settings.logFilter,"create_child_devices>looping thru HVAC units, found id $dni", detailedNotif)

		if(!d) {
			def labelName = 'My Flair HVAC ' + "${name}"
			traceEvent(settings.logFilter,"create_child_devices>about to create child device with id $dni, hvacUnitId = $hvacUnitId, name=  ${name}", detailedNotif)
			d = addChildDevice(getChildNamespace(), getHvacChildName(), dni, [label: "${labelName}"]) 
			d.initialSetup( getHubitatClientId() , getHubitatClientSecretId(), atomicState.access_token,atomicState.refresh_token,atomicState.token_type,atomicState.authexptime, 
				structureId, hvacUnitId ) 	// initial setup of the Child Device
			traceEvent(settings.logFilter,"create_child_devices>created ${d.displayName} with id $dni", detailedNotif)
			countNewChildDevices++            
		} else {
			traceEvent(settings.logFilter,"create_child_devices>found ${d.displayName} with id $dni already exists", detailedNotif)
			try {
				if (d.isTokenExpired()) {  // called refreshAllChildAuthTokens when updated
 					refreshAllChildAuthTokens()    
				}
			} catch (e) {
				traceEvent(settings.logFilter,"create_child_devices>exception $e while trying to refresh existing tokens in child $d", detailedNotif,get_LOG_ERROR())
            
			}            
		}

	}

	traceEvent(settings.logFilter,"create_child_devices>created $countNewChildDevices, total=${devices.size()} HVAC units", detailedNotif)

}




def askAlexaMQHandler(evt) {
	if (!evt) return
	switch (evt.value) {
		case "refresh":
		state?.askAlexaMQ = evt.jsonData && evt.jsonData?.queues ? evt.jsonData.queues : []
		traceEvent(settings.logFilter,"askAlexaMQHandler>new refresh value=$evt.jsonData?.queues", detailedNotif, get_LOG_INFO())
		break
	}
}

def appTouch(evt) {
	rescheduleIfNeeded()
//	takeAction()    
}

def rescheduleIfNeeded(evt) {
	if (evt) traceEvent(settings.logFilter,"rescheduleIfNeeded>$evt.name=$evt.value", detailedNotif)
	Integer delay = (givenInterval) ? givenInterval.toInteger() : 10 // By default, do it every 10 min.
	long currentTime = now()    
	long lastPollTime = (currentTime - (atomicState?.poll["last"]?:0).toLong()) 
	if (lastPollTime != currentTime) {    
		Double lastPollTimeInMinutes = (lastPollTime/60000).toDouble().round(1)      
		traceEvent(settings.logFilter,"rescheduleIfNeeded>last poll was  ${lastPollTimeInMinutes.toString()} minutes ago", detailedNotif, get_LOG_INFO())
	}
	if ((((atomicState?.poll["last"]?:0) + (delay * 60000)).toLong() < currentTime)) {
		traceEvent(settings.logFilter,"rescheduleIfNeeded>scheduling takeAction in ${delay} minutes..", detailedNotif,get_LOG_INFO())
//        unschedule()
        if ((delay >=5) && (delay <10)) {      
			runEvery5Minutes(takeAction)
		} else if ((delay >=10) && (delay <15)) {  
			runEvery10Minutes(takeAction)
		} else if ((delay >=15) && (delay <30)) {  
			runEvery15Minutes(takeAction)
		} else {  
			runEvery30Minutes(takeAction)
		}
        takeAction()    
	}
    
    
	// Update rescheduled state
    
    if (!evt) {
        def pollTimestamps=atomicState?.poll
        pollTimestamps["rescheduled"]= now()    
        atomicState?.poll = pollTimestamps 
    } 
}



def takeAction() {
	traceEvent(settings.logFilter,"takeAction>begin", detailedNotif)
	def todayDay
	atomicState?.newDay=false        
    
	if (!location.timeZone) {    	
		traceEvent(settings.logFilter,"takeAction>Your location is not set in your ST account, you'd need to set it as indicated in the prerequisites for better exception handling..",
			true,get_LOG_ERROR(), true)
	} else {
		todayDay = new Date().format("dd",location.timeZone)
	}        
	if ((!atomicState?.today) || (todayDay != atomicState?.today)) {
		if (atomicState?.today) atomicState?.newDay=true        
		atomicState?.alerts=[:] // reinitialize the alerts & exceptionCount every day
		atomicState?.exceptionCount=0   
		atomicState?.sendExceptionCount=0        
		atomicState?.today=todayDay        
	}   
    
	Integer delay = (givenInterval) ? givenInterval.toInteger(): 10 // By default, do it every 10 min.
    def pollTimestamps=atomicState?.poll
    pollTimestamps["last"]= now()   
    atomicState?.poll = pollTimestamps

/*    
	//schedule the rescheduleIfNeeded() function
    
	if (((state?.poll["rescheduled"]?:0) + (delay * 60000)).toLong() < now()) {
		traceEvent(settings.logFilter,"takeAction>scheduling rescheduleIfNeeded() in ${delay} minutes..", detailedNotif, get_LOG_INFO())
		unschedule()        
		schedule("0 0/${delay} * * * ?", rescheduleIfNeeded)
		// Update rescheduled state
		state?.poll["rescheduled"] = now()
	}
*/    
	poll_pucks()    
	poll_tstats()    
	poll_vents()   
	poll_hvacs()   


	traceEvent(settings.logFilter,"takeAction>end", detailedNotif)

}



private void poll_pucks() {
	traceEvent(settings.logFilter,"poll_pucks>begin", detailedNotif)
	def exceptionCheck    
	def MAX_EXCEPTION_COUNT=10
	boolean handleException = (handleExceptionFlag)?: false
    
	def devicesPucks = pucks.collect { dni ->
		def d = getChildDevice(dni)
		if (d) {       
			traceEvent(settings.logFilter,"poll_pucks>Looping thru pucks, found id $dni, about to poll", detailedNotif, get_LOG_INFO())
			d.poll()
			exceptionCheck = d.currentVerboseTrace
			if ((exceptionCheck) && (((exceptionCheck.contains("exception") || (exceptionCheck.contains("error"))) && 
				(!exceptionCheck.contains("TimeoutException")) && (!exceptionCheck.contains("No signature of method: physicalgraph.device.CommandService.executeAction")) &&
				(!exceptionCheck.contains("UndeclaredThrowableException"))))) {  
				// check if there is any exception or an error reported in the verboseTrace associated to the device (except the ones linked to rate limiting).
				atomicState.exceptionCount=atomicState.exceptionCount+1    
				traceEvent(settings.logFilter,"found exception/error after polling, exceptionCount= ${atomicState?.exceptionCount}: $exceptionCheck", detailedNotif, get_LOG_ERROR()) 
			} else {   // poll was successful          
				// reset exception counter            
				atomicState?.exceptionCount=0      
				if (atomicState?.newDay && askAlexaFlag) { // produce summary reports only at the beginning of a new day
					def PAST_DAY_SUMMARY=1 // day
					def PAST_WEEK_SUMMARY=7 // days
					if (settings.puckDaySummaryFlag) {
						traceEvent(settings.logFilter,"About to call produceSummaryReport for device ${d.displayName} in the past day", detailedNotif, get_LOG_TRACE()) 
						d.produceSummaryReport(PAST_DAY_SUMMARY)
						String summary_report =d.currentValue("summaryReport")                        
						if (summary_report) {                        
							send (summary_report, askAlexaFlag)                        
						}                            
					}                    
					if (settings.puckWeeklySummaryFlag) { // produce summary report only at the beginning of a new day
						traceEvent(settings.logFilter,"About to call produceSummaryReport for device ${d.displayName} in the past week", detailedNotif, get_LOG_TRACE()) 
						d.produceSummaryReport(PAST_WEEK_SUMMARY)
						String summary_report =d.currentValue("summaryReport")                        
						if (summary_report) {                        
							send (summary_report, askAlexaFlag)                        
						}                            
					}
				} /* end if askAlexa */                    
			}                
			                
		} /* end if (d) */        
	}
	if (handleException) {    
		if ((exceptionCheck) && (exceptionCheck.contains("Unauthorized")) && (atomicState?.exceptionCount>=MAX_EXCEPTION_COUNT)) {
			// need to authenticate again    
			atomicState?.access_token=null                    
			atomicState?.oauthTokenProvided=false
			traceEvent(settings.logFilter,"$exceptionCheck after ${atomicState?.exceptionCount} errors, press on 'flair' and re-login..." , 
				true, get_LOG_ERROR(),true)
		} else if (atomicState?.exceptionCount>=MAX_EXCEPTION_COUNT) {
			traceEvent(settings.logFilter,"too many exceptions/errors, $exceptionCheck (${atomicState?.exceptionCount} errors so far), you may need to press on 'flair' and re-login..." ,
				true, get_LOG_ERROR(), true)
		}
	} /* end if handleException */        

	traceEvent(settings.logFilter,"poll_pucks>end", detailedNotif)

}



private void poll_tstats() {
	traceEvent(settings.logFilter,"poll_tstats>begin", detailedNotif)
	def exceptionCheck    
	def MAX_EXCEPTION_COUNT=10
	boolean handleException = (handleExceptionFlag)?: false

	def devicesTstats = thermostats.collect { dni ->
		def d = getChildDevice(dni)
		if (d) {       
			traceEvent(settings.logFilter,"poll_tstats>Looping thru thermostats, found id $dni, about to poll", detailedNotif, get_LOG_INFO())
			d.poll()
			exceptionCheck = d.currentVerboseTrace
			if ((exceptionCheck) && (((exceptionCheck.contains("exception") || (exceptionCheck.contains("error"))) && 
				(!exceptionCheck.contains("TimeoutException")) && (!exceptionCheck.contains("No signature of method: physicalgraph.device.CommandService.executeAction")) &&
				(!exceptionCheck.contains("UndeclaredThrowableException"))))) {  
				// check if there is any exception or an error reported in the verboseTrace associated to the device (except the ones linked to rate limiting).
				atomicState.exceptionCount=atomicState.exceptionCount+1    
				traceEvent(settings.logFilter,"found exception/error after polling, exceptionCount= ${atomicState?.exceptionCount}: $exceptionCheck", detailedNotif, get_LOG_ERROR()) 
			} else {             
				// reset exception counter            
				atomicState?.exceptionCount=0      
			}                
		} /* end if (d) */        
	} 
	
	if (handleException) {    
		if ((exceptionCheck) && (exceptionCheck.contains("Unauthorized")) && (atomicState?.exceptionCount>=MAX_EXCEPTION_COUNT)) {
			// need to authenticate again    
			atomicState?.access_token=null                    
			atomicState?.oauthTokenProvided=false
			traceEvent(settings.logFilter,"$exceptionCheck after ${atomicState?.exceptionCount} errors, press on 'flair' and re-login..." , 
				true, get_LOG_ERROR(),true)
		} else if (atomicState?.exceptionCount>=MAX_EXCEPTION_COUNT) {
			traceEvent(settings.logFilter,"too many exceptions/errors, $exceptionCheck (${atomicState?.exceptionCount} errors so far), you may need to press on 'flair' and re-login..." ,
				true, get_LOG_ERROR(), true)
		}
	} /* end if handleException */        

	traceEvent(settings.logFilter,"poll_tstats>end", detailedNotif)

}

private void poll_vents() {
	traceEvent(settings.logFilter,"poll_vents>begin", detailedNotif)
	def exceptionCheck    
	def MAX_EXCEPTION_COUNT=10
	boolean handleException = (handleExceptionFlag)?: false
    
	def deviceVents = vents.collect { dni ->
		def d = getChildDevice(dni)
		if (d) {       
			traceEvent(settings.logFilter,"poll_vents>Looping thru vents, found id $dni, about to poll", detailedNotif)
			d.poll()
			exceptionCheck = d.currentVerboseTrace
			if ((exceptionCheck) && (((exceptionCheck.contains("exception") || (exceptionCheck.contains("error"))) && 
				(!exceptionCheck.contains("TimeoutException")) && (!exceptionCheck.contains("No signature of method: physicalgraph.device.CommandService.executeAction")) &&
				(!exceptionCheck.contains("UndeclaredThrowableException"))))) {  
				// check if there is any exception or an error reported in the verboseTrace associated to the device (except the ones linked to rate limiting).
				atomicState.exceptionCount=atomicState.exceptionCount+1    
				traceEvent(settings.logFilter,"found exception/error after polling, exceptionCount= ${atomicState?.exceptionCount}: $exceptionCheck", detailedNotif, get_LOG_ERROR(), true) 
			} else {             
				// reset exception counter            
				atomicState?.exceptionCount=0      
				if (atomicState?.newDay && askAlexaFlag) { // produce summary reports only at the beginning of a new day
					def PAST_DAY_SUMMARY=1 // day
					def PAST_WEEK_SUMMARY=7 // days
					if (settings.ventDaySummaryFlag) {
						traceEvent(settings.logFilter,"About to call produceSummaryReport for device ${d.displayName} in the past day", detailedNotif, get_LOG_TRACE()) 
						d.produceSummaryReport(PAST_DAY_SUMMARY)
						String summary_report =d.currentValue("summaryReport")                        
						if (summary_report) {                        
							send (summary_report, askAlexaFlag)                        
						}                            
					}                    
					if (settings.ventWeeklySummaryFlag) { // produce summary report only at the beginning of a new day
						traceEvent(settings.logFilter,"About to call produceSummaryReport for device ${d.displayName} in the past week", detailedNotif, get_LOG_TRACE()) 
						d.produceSummaryReport(PAST_WEEK_SUMMARY)
						String summary_report =d.currentValue("summaryReport")                        
						if (summary_report) {                        
							send (summary_report, askAlexaFlag)                        
						}                            
					}
				} /* end if askAlexa */                    
			}                
		} /* end if (d) */        
	}
	
	if (handleException) {    
		if ((exceptionCheck) && (exceptionCheck.contains("Unauthorized")) && (atomicState?.exceptionCount>=MAX_EXCEPTION_COUNT)) {
			// need to authenticate again    
			atomicState?.access_token=null                    
			atomicState?.oauthTokenProvided=false
			traceEvent(settings.logFilter,"$exceptionCheck after ${atomicState?.exceptionCount} errors, press on 'flair' and re-login..." , 
				true, get_LOG_ERROR(),true)
		} else if (atomicState?.exceptionCount>=MAX_EXCEPTION_COUNT) {
			traceEvent(settings.logFilter,"too many exceptions/errors, $exceptionCheck (${atomicState?.exceptionCount} errors so far), you may need to press on 'flair' and re-login..." ,
				true, get_LOG_ERROR(), true)
		}
	} /* end if handleException */        

	traceEvent(settings.logFilter,"poll_vents>end", detailedNotif)

}


private void poll_hvacs() {
	traceEvent(settings.logFilter,"poll_hvacs>begin", detailedNotif)
	def exceptionCheck    
	def MAX_EXCEPTION_COUNT=10
	boolean handleException = (handleExceptionFlag)?: false
    
	def devicesHvacs = hvacUnits.collect { dni ->
		def d = getChildDevice(dni)
		if (d) {       
			traceEvent(settings.logFilter,"poll_hvacs>Looping thru HVAC units, found id $dni, about to poll", detailedNotif, get_LOG_INFO())
			d.poll()
			exceptionCheck = d.currentVerboseTrace
			if ((exceptionCheck) && (((exceptionCheck.contains("exception") || (exceptionCheck.contains("error"))) && 
				(!exceptionCheck.contains("TimeoutException")) && (!exceptionCheck.contains("No signature of method: physicalgraph.device.CommandService.executeAction")) &&
				(!exceptionCheck.contains("UndeclaredThrowableException"))))) {  
				// check if there is any exception or an error reported in the verboseTrace associated to the device (except the ones linked to rate limiting).
				atomicState.exceptionCount=atomicState.exceptionCount+1    
				traceEvent(settings.logFilter,"found exception/error after polling, exceptionCount= ${atomicState?.exceptionCount}: $exceptionCheck", detailedNotif, get_LOG_ERROR()) 
			} else {             
				// reset exception counter            
				atomicState?.exceptionCount=0      
				if (atomicState?.newDay && askAlexaFlag) { // produce summary reports only at the beginning of a new day
					def PAST_DAY_SUMMARY=1 // day
					def PAST_WEEK_SUMMARY=7 // days
					if (settings.hvacDaySummaryFlag) {
						traceEvent(settings.logFilter,"About to call produceSummaryReport for device ${d.displayName} in the past day", detailedNotif, get_LOG_TRACE()) 
						d.produceSummaryReport(PAST_DAY_SUMMARY)
						String summary_report =d.currentValue("summaryReport")                        
						if (summary_report) {                        
							send (summary_report, askAlexaFlag)                        
						}                            
					}                    
					if (settings.hvacWeeklySummaryFlag) { // produce summary report only at the beginning of a new day
						traceEvent(settings.logFilter,"About to call produceSummaryReport for device ${d.displayName} in the past week", detailedNotif, get_LOG_TRACE()) 
						d.produceSummaryReport(PAST_WEEK_SUMMARY)
						String summary_report =d.currentValue("summaryReport")                        
						if (summary_report) {                        
							send (summary_report, askAlexaFlag)                        
						}                            
					}
				} /* end if askAlexa */                    
			}                
		} /* end if (d) */        
	} 
	
	if (handleException) {    
		if ((exceptionCheck) && (exceptionCheck.contains("Unauthorized")) && (atomicState?.exceptionCount>=MAX_EXCEPTION_COUNT)) {
			// need to authenticate again    
			atomicState?.access_token=null                    
			atomicState?.oauthTokenProvided=false
			traceEvent(settings.logFilter,"$exceptionCheck after ${atomicState?.exceptionCount} errors, press on 'flair' and re-login..." , 
				true, get_LOG_ERROR(),true)
		} else if (atomicState?.exceptionCount>=MAX_EXCEPTION_COUNT) {
			traceEvent(settings.logFilter,"too many exceptions/errors, $exceptionCheck (${atomicState?.exceptionCount} errors so far), you may need to press on 'flair' and re-login..." ,
				true, get_LOG_ERROR(), true)
		}
	} /* end if handleException */        

	traceEvent(settings.logFilter,"poll_hvacs>end", detailedNotif)

}


def isTokenExpired() {
	def buffer_time_expiration=5  // set a 5 min. buffer time before token expiration to avoid auth_err 
	def time_check_for_exp = now() + (buffer_time_expiration * 60 * 1000);
	traceEvent(settings.logFilter,"isTokenExpired>expiresIn timestamp: ${atomicState?.authexptime} > timestamp check for exp: ${time_check_for_exp}?", detailedNotif)
	if (atomicState?.authexptime > time_check_for_exp) {
		traceEvent(settings.logFilter,"isTokenExpired>not expired", detailedNotif)
		return false
	}
	traceEvent(settings.logFilter,"isTokenExpired>expired", detailedNotif)
	return true    
}




def success() {

	def html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=640">
<title>Withings Connection</title>
<style type="text/css">
	@font-face {
		font-family: 'Swiss 721 W01 Thin';
		src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
		src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
		font-weight: normal;
		font-style: normal;
	}
	@font-face {
		font-family: 'Swiss 721 W01 Light';
		src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
		src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
			 url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
		font-weight: normal;
		font-style: normal;
	}
	.container {
		width: 560px;
		padding: 40px;
		/*background: #eee;*/
		text-align: center;
	}
	img {
		vertical-align: middle;
	}
	img:nth-child(2) {
		margin: 0 30px;
	}
	p {
		font-size: 2.2em;
		font-family: 'Swiss 721 W01 Thin';
		text-align: center;
		color: #666666;
		padding: 0 40px;
		margin-bottom: 0;
	}
/*
	p:last-child {
		margin-top: 0px;
	}
*/
	span {
		font-family: 'Swiss 721 W01 Light';
	}
</style>
</head>
<body>
	<div class="container">
		<img src="${getCustomImagePath()}flairLogo.png" width="216" height="216" alt="flair icon" />
		<img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" />
		<img src="//cdn.shopify.com/s/files/1/2575/8806/files/logo_30_color_32x32.png?v=1511400156" width="52" height="60" alt="Hubitat icon"/>
		<p>Your Flair Account is now connected to Hubitat!</p>
		<p>Close this window to continue in the previous page</p>
	</div>
</body>
</html>
"""

	render contentType: 'text/html', data: html
}


def fail() {
	def message = """
		<p>There was an error connecting your Flair account with Hubitat</p>
		<p>Please try again.</p>
		<p>Close this window to continue in the previous page</p>
	"""
	displayMessageAsHtml(message)
}

def displayMessageAsHtml(message) {
	def html = """
		<!DOCTYPE html>
		<html>
			<head>
			</head>	
			<body>
				<div>
					${message}
				</div>
			</body>
		</html>
	"""
	render contentType: 'text/html', data: html
}

String pageTitle 	(String txt) 	{ return (!isST()) ? formatText('header','<h2>'+(txt.contains("\n") ? '<b>'+txt.replace("\n","</b>\n") : txt )+'</h2>') : txt }
String sectionTitle	(String txt) 	{ return (!isST()) ? formatText('header','<h3><b>&nbsp;&nbsp;'+txt+'</b></h3>')	: txt }
String smallTitle	(String txt)	{ return txt ? ((!isST()) ? '<h3 style="color:#8c8c8c"><b>'+txt+'</b></h3>'				: txt) : '' } // <hr style="background-color:#5BBD76;height:1px;width:52%;border:0;align:top">
String boldTitle	(String txt) 	{ return (!isST()) ? '<b>'+txt+'</b>'								: txt }

String formatText(type, myText=""){
	switch(type) {
		case "header":
			return "<div style='width:50%;min-width:400px;color:#FFFFFF;background-color:#8c8c8c;padding-left:0.5em;padding-right:0.5em;box-shadow: 0px 3px 3px 0px #b3b3b3'>${myText}</div>"
			break;
		case "line":
			return (!isST()) ? "<hr style='background-color:#8c8c8c; height: 1px; border: 0;'></hr>" : "-----------------------------------------------"
			break;
		case "title":
			return "<h2 style='color:#8c8c8c;font-weight: bold'>${myText}</h2>"
			break;
		case "warning":
			return (!isST())? "<span style='color:red'><b>WARNING: </b><i></span>${myText}</i>" : "WARNING: ${myText}"
			break;
		case "note":
			return (!isST()) ? "<b>NOTE: </b>${myText}" : "NOTE: ${myText}"
			break;
		default:
			return myText
			break;
	}
}


def toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}
def getChildNamespace() { "srbarcus" }
def getPuckChildName() { "My Puck Device" }
def getTstatChildName() { "My Flair Thermostat" }
def getVentChildName() { "My Flair Vent" }
def getHvacChildName() { "My Flair HvacUnit" }

def getServerUrl() { return getApiServerUrl()  }

def getHubitatClientId() { return "xAJc8sphfVr5XdtKk9DSyRK46j6tw8MnqGtDgPnE" }

def getHubitatClientSecretId() { return "dVAnxcfKNM6McQiGU11J791fnyHymY8QXxSSKsl3wb4A0xwjskmn9JPYJ6me"}

private def get_APPLICATION_VERSION() {
	return "application/vnd.api+json; co.flair.api.version=${get_API_VERSION()}"
}
private def get_URI_ROOT() {
	return "https://api.flair.co"
}
private def get_API_VERSION() {
	return "1"
}
private def get_ST_URI_ROOT() {
	return "https://graph.api.smartthings.com"
}
 

private def getStandardImagePath() {
	return "http://cdn.device-icons.smartthings.com/"
}


private send(String msg, askAlexa=false) {
	int MAX_EXCEPTION_MSG_SEND=5

	// will not send exception msg when the maximum number of send notifications has been reached
	if (msg.contains("exception")) {
		atomicState?.sendExceptionCount=atomicState?.sendExceptionCount+1         
		traceEvent(settings.logFilter,"checking sendExceptionCount=${atomicState?.sendExceptionCount} vs. max=${MAX_EXCEPTION_MSG_SEND}", detailedNotif)
		if (atomicState?.sendExceptionCount >= MAX_EXCEPTION_MSG_SEND) {
			traceEvent(settings.logFilter,"send>reached $MAX_EXCEPTION_MSG_SEND exceptions, exiting", detailedNotif)
			return        
		}        
	}    
	def message = "${get_APP_NAME()}>${msg}"


	if (sendPushMessage != "No") {
		traceEvent(settings.logFilter,"contact book not enabled", false, get_LOG_INFO())
		sendPush(message)
	}
	if (askAlexa) {
		def expiresInDays=(AskAlexaExpiresInDays)?:2    
		sendLocationEvent(
			name: "AskAlexaMsgQueue", 
			value: "${get_APP_NAME()}", 
			isStateChange: true, 
			descriptionText: msg, 
			data:[
				queues: listOfMQs,
		        expires: (expiresInDays*24*60*60)  /* Expires after 2 days by default */
		    ]
		)
	} /* End if Ask Alexa notifications*/
	
	if (phoneNumber) {
		sendSms(phoneNumber, message)
	}
}


private int get_LOG_ERROR()	{return 1}
private int get_LOG_WARN()	{return 2}
private int get_LOG_INFO()	{return 3}
private int get_LOG_DEBUG()	{return 4}
private int get_LOG_TRACE()	{return 5}

def traceEvent(filterLog, message, displayEvent=false, traceLevel=4, sendMessage=false) {
	int LOG_ERROR= get_LOG_ERROR()
	int LOG_WARN=  get_LOG_WARN()
	int LOG_INFO=  get_LOG_INFO()
	int LOG_DEBUG= get_LOG_DEBUG()
	int LOG_TRACE= get_LOG_TRACE()
    Integer filterLevel=(filterLog)?filterLog.toInteger():get_LOG_WARN()


	if (filterLevel >= traceLevel.toInteger()) {
		if (displayEvent) {    
			switch (traceLevel) {
				case LOG_ERROR:
					log.error "${message}"
				break
				case LOG_WARN:
					log.warn "${message}"
				break
				case LOG_INFO:
					log.info "${message}"
				break
				case LOG_TRACE:
					log.trace "${message}"
				break
				case LOG_DEBUG:
				default:            
					log.debug "${message}"
				break
			}                
		}			                
		if (sendMessage) send (message,settings.askAlexaFlag) //send message only when true
	}        
}

private def get_APP_NAME() {
	return "YoLink Hub Service"
}

def getCustomImagePath() {   
	return "https://raw.githubusercontent.com/srbarcus/yolink/main/icons/"
}   