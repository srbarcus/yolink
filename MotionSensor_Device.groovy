/***
 *  YoLink™ Motion Sensor (YS7804-UC)
 *  © 2022, 2023 Steven Barcus. All rights reserved.
 *  THIS SOFTWARE IS NEITHER DEVELOPED, ENDORSED, OR ASSOCIATED WITH YoLink™ OR YoSmart, Inc.
 *   
 *  DO NOT INSTALL THIS DEVICE MANUALLY - IT WILL NOT WORK. MUST BE INSTALLED USING THE YOLINK DEVICE SERVICE APP  
 *   
 *  Developer retains all rights, title, copyright, and interest, including patent rights and trade
 *  secrets in this software. Developer grants a non-exclusive perpetual license (License) to User to use
 *  this software under this Agreement. However, the User shall make no commercial use of the software without
 *  the Developer's written consent. Software Distribution is restricted and shall be done only with Developer's written approval.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied. 
 * 
 *  1.0.1: Fixed debug messages appearing when debug is off. Fixed poll()
 *  1.0.2: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *         - Return Motion as ENUM ["inactive", "active"] per standards
 *         - Correct attribute types
 *  1.0.3: Added MotionSensor capability
 *  1.0.4: Minor tracing fix, Fix syncing of Temperature scale with YoLink™ Device Service app
 *  1.0.5: Fix donation URL
 *  1.0.6: Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions
 *  2.0.1: Support diagnostics, correct various errors, make singleThreaded
 *  2.0.2: Add unit to temperature and battery attributes
 *         - Added formatted "signal" attribute as rssi & " dBm"
 *         - Added capability "SignalStrength"
 *  2.0.3: Handle event 'setOpenRemind'
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.3"}
def copyright() {return "<br>© 2022, 2023 Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ Motion Sensor (YS7804-UC) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink MotionSensor Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"				
		capability "Battery"
        capability "Temperature Measurement"
        capability "MotionSensor"
        capability "SignalStrength"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]] 
        command "reset" 
        
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"  
        attribute "reportAt", "String"
        attribute "signal", "String" 
        attribute "lastResponse", "String" 
        
        attribute "alertInterval", "Number"      
        attribute "openRemindDelay", "Number"                    
        }
   }

void ServiceSetup(Hubitat_dni,homeID,devname,devtype,devtoken,devId) {
	state.debug = false		
    
    state.my_dni = Hubitat_dni      
    state.homeID = homeID    
    state.name = devname
    state.type = devtype
    state.token = devtoken
    rememberState("devId", devId)   
    	
	log.info "ServiceSetup(Hubitat dni=${state.my_dni}, Home ID=${state.homeID}, Name=${state.name}, Type=${state.type}, Token=${state.token}, Device Id=${state.devId})"
	    
    reset()      
 }

public def getSetup() {
    def setup = [:]
        setup.put("my_dni", "${state.my_dni}")                   
        setup.put("homeID", "${state.homeID}") 
        setup.put("name", "${state.name}") 
        setup.put("type", "${state.type}") 
        setup.put("token", "${state.token}") 
        setup.put("devId", "${state.devId}") 
    return setup
}

public def isSetup() {
    return (state.my_dni && state.homeID && state.name && state.type && state.token && state.devId)
}

def installed() {
   log.info "Device Installed"
   rememberState("driver", clientVersion())    
 }

def updated() {
   log.info "Device Updated" 
   rememberState("driver", clientVersion()) 
 }

def uninstalled() { 
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def poll(force=null) {
    logDebug("poll(${force})")
    
    rememberState("driver", clientVersion())

    def lastPoll
    def cur_time = now()
    def min_seconds = 10                     // To avoid unecessary load on YoLink servers, limit rate of polling
    def min_interval = min_seconds * 1000    // Convert to milliseconds

    if (force != null) {
       logDebug("Forcing poll")
       state.lastPoll = cur_time - min_interval
    }

    lastPoll = state.lastPoll

    def min_time = lastPoll + min_interval

    if (cur_time < min_time ) {
       log.warn "Polling interval of once every ${min_seconds} seconds exceeded, device was not polled."	
    } else {
       logDebug("Getting device state")
       runIn(1,getDevicestate)
       state.lastPoll = now()
    }  
 }

def temperatureScale(value) {
    state.temperatureScale = value
 }

def debug(value) { 
   rememberState("debug",value)
   if (value == "true") {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

def getDevicestate() {
    state.driver=clientVersion()
    
	logDebug("getDevicestate() obtaining device state")
    
	boolean rc=false	//DEFAULT: Return Code = false
    
	try {  
        def request = [:]
        request.put("method", "${state.type}.getState")                
        request.put("targetDevice", "${state.devId}") 
        request.put("token", "${state.token}") 
        
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("getDevicestate()> pollAPI() response: ${object}")                        
               
            if (successful(object)) {             
               def online = object.data.online    
               def reportAt = object.data.reportAt 
                
               def alertInterval = object.data.state.alertInterval                             
               def battery = parent.batterylevel(object.data.state.battery)                
               def temperature = object.data.state.devTemperature 
                
               temperature = parent.convertTemperature(temperature) 
                
               def ledAlarm = object.data.state.ledAlarm       
               def nomotionDelay = object.data.state.nomotionDelay               
               def sensitivity = object.data.state.sensitivity
               def devstate = object.data.state.state    
               def firmware = object.data.state.version.toUpperCase()        
                    
               def motion = "active"                                 //ENUM ["inactive", "active"]
               if (devstate == "normal"){motion="inactive"} 
                
               logDebug("Device State: online(${online}), " +
                        "Report At(${reportAt}), " +
                        "Alert Interval(${alertInterval}), " +
                        "Battery(${battery}), " +
                        "Temperature(${temperature}), " +   
                        "LED Alarm(${ledAlarm}), " +
                        "No Motion Delay(${nomotionDelay}), " +
                        "Sensitivity(${sensitivity}), " + 
                        "State(${devstate}), " +  
                        "Motion(${motion}), " + 
                        "Firmware(${firmware})")

               rememberState("online",online) 
               rememberState("reportAt",reportAt) 
               rememberState("alertInterval",alertInterval) 
               rememberState("battery", battery, "%")
               rememberState("temperature", temperature, "°".plus(state.temperatureScale))
               rememberState("ledAlarm",ledAlarm)
               rememberState("nomotionDelay",nomotionDelay)
               rememberState("sensitivity",sensitivity)
               rememberState("state",devstate)
               rememberState("motion",motion)
               rememberState("firmware",firmware)                      
                    
  		       rc = true	
            } else {  //Error
               pollError(object) 
            } 
        } else {
            log.error "No response from API request"
        } 
	} catch (groovyx.net.http.HttpResponseException e) {	
            rc = false
            if (e?.statusCode == UNAUTHORIZED_CODE) { 
                lastResponse("Unauthorized")                
            } else {
                    lastResponse("Exception $e")                
					logDebug("getDevices() Exception $e")
			}              
	}
    
	return rc
}    


def parse(topic) {     
    processStateData(topic.payload)
}

def void processStateData(payload) {
    rememberState("online","true") 
    
    def object = new JsonSlurper().parseText(payload)    
    def devId = object.deviceId       
    
    if (state.devId == devId) {  // Only handle if message is for me    
        logDebug("processStateData(${payload})")
        
        def child = parent.getChildDevice(state.my_dni)
        def name = child.getLabel()                
        def event = object.event.replace("${state.type}.","")
        logDebug("Received Message Type: ${event} for: $name")
        
        switch(event) {
		case "Alert": case "StatusChange":                           
			def devstate = object.data.state                     
            def battery = parent.batterylevel(object.data.battery)    // Value = 0-4    
            def firmware = object.data.version.toUpperCase()    
            def ledAlarm = object.data.ledAlarm    
            def alertInterval = object.data.alertInterval    
            def nomotionDelay = object.data.nomotionDelay    
            def sensitivity = object.data.sensitivity              
            def rssi = object.data.loraInfo.signal           
            
            def motion = "active"                                 //ENUM ["inactive", "active"]
            if (devstate == "normal"){motion="inactive"}                               
    
            logDebug("Parsed: DeviceId=$devId, State=$devstate, Battery=$battery, Firmware=$firmware, LED Alarm=$ledAlarm, Alert Interval=$alertInterval, No Motion Delay=$nomotionDelay, Sensitivity=$sensitivity, RSSI=$rssi, Motion=$motion")
            
            rememberState("state",devstate)
            rememberState("battery", battery, "%")            
            rememberState("firmware",firmware)      
            rememberState("ledAlarm",ledAlarm)
            rememberState("alertInterval",alertInterval)
            rememberState("nomotionDelay",nomotionDelay)
            rememberState("sensitivity",sensitivity)                        
            fmtSignal(rssi) 
            rememberState("motion",motion)           
 		    break;     
            
//Message received: {"event":"MotionSensor.setOpenRemind","time":1681487912545,"msgid":"1681487912543","data":{"alertInterval":5,"ledAlarm":true,"nomotionDelay":1,
//"sensitivity":3,"loraInfo":{"signal":-32,"gatewayId":"d88b4c160400012d","gateways":1}},"deviceId":"d88b4c0200049cfa"}
        case "setOpenRemind":
            def alertInterval = object.data.alertInterval
            def ledAlarm = object.data.ledAlarm    
            def nomotionDelay = object.data.nomotionDelay    
            def sensitivity = object.data.sensitivity              
            def rssi = object.data.loraInfo.signal   
            
            logDebug("Parsed: DeviceId=$devId, Alert Interval=$alertInterval, LED Alarm=$ledAlarm, No Motion Delay=$nomotionDelay, Sensitivity=$sensitivity, RSSI=$rssi")
            
            rememberState("alertInterval",alertInterval)
            rememberState("ledAlarm",ledAlarm)
            rememberState("nomotionDelay",nomotionDelay)
            rememberState("sensitivity",sensitivity)                        
            fmtSignal(rssi)  
            break;
            
        case "Report":
			def devstate = object.data.state                     
            def battery = parent.batterylevel(object.data.battery)    // Value = 0-4    
            def firmware = object.data.version.toUpperCase()    
            def ledAlarm = object.data.ledAlarm    
            def alertInterval = object.data.alertInterval    
            def nomotionDelay = object.data.nomotionDelay    
            def sensitivity = object.data.sensitivity              
            def rssi = object.data.loraInfo.signal              
            def temperature = object.data.devTemperature 
                
            temperature = parent.convertTemperature(temperature) 
                        
            def motion = "active"                                 //ENUM ["inactive", "active"]
            if (devstate == "normal"){motion="inactive"} 
    
            logDebug("Parsed: DeviceId=$devId, State=$devstate, Battery=$battery, Firmware=$firmware, LED Alarm=$ledAlarm, Alert Interval=$alertInterval, No Motion Delay=$nomotionDelay, Sensitivity=$sensitivity, RSSI=$rssi, Motion=$motion, Temperature=$temperature")
            
            rememberState("state",devstate)
            rememberState("battery", battery, "%")            
            rememberState("firmware",firmware)      
            rememberState("ledAlarm",ledAlarm)
            rememberState("alertInterval",alertInterval)
            rememberState("nomotionDelay",nomotionDelay)
            rememberState("sensitivity",sensitivity)                        
            fmtSignal(rssi)  
            rememberState("motion",motion)
            rememberState("temperature", temperature, "°".plus(state.temperatureScale))
 		    break;                                              
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }
}

def reset(){
    state.remove("driver")
    rememberState("driver", clientVersion())
    state.remove("online")
    state.remove("firmware")
    state.remove("swState")
    state.remove("door")
    state.remove("alertType")
    state.remove("battery")
    state.remove("rssi")
    state.remove("signal")
    state.remove("reportAt")
    state.remove("alertInterval")
    state.remove("delay")             //Undocumented response   
    state.remove("temperature")
    state.remove("openRemindDelay")
    
    rememberState("temperatureScale", parent.temperatureScale)
    
    poll(true)
    
    log.warn "Device reset to default values"
}

def lastResponse(value) {
   sendEvent(name:"lastResponse", value: "$value", isStateChange:true)   
}

def rememberState(name,value,unit=null) {   
   if (state."$name" != value) {
     state."$name" = value   
     value=value.toString()
     if (unit==null) {  
         sendEvent(name:"$name", value: "$value", isStateChange:true)
     } else {        
         sendEvent(name:"$name", value: "$value", unit: "$unit", isStateChange:true)      
     }           
   }
}   

def successful(object) {
  return (object.code == "000000")     
}

def notConnected(object) {
  return (object.code == "000201")
}

def pollError(object) {
    def nc = false               //Assume not a connection error
    if (notConnected(object)) {  //Cannot connect to Device
       rememberState("online", "false")                                                                
       log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
       nc = true 
    } else {
       log.error "API polling returned error: $object.code - " + parent.translateCode(object.code)
       lastResponse("Polling error: $object.code - " + parent.translateCode(object.code))         
    }
    
    return nc    
} 

def logDebug(msg) {
  if (state.debug == "true") {log.debug msg}
}

def fmtSignal(rssi) {
   rememberState("rssi",rssi) 
   rememberState("signal",rssi.plus(" dBm")) 
}    