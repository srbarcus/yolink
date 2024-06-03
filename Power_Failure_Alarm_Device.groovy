/***
 *  YoLink™ Power Failure Alarm (YS7106-UC)
 *  © (See copyright()) Steven Barcus. All rights reserved.
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
 *  2.0.0: Initial release
 *           API is not reflecting correct values as of 06/03/2024:
 *                 - alarmDuration
 *                 - alertInterval
 *                 - mute
 *                 - volume
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.0"}
def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

/*
data.state.state	<String,Optional>	State of device,["normal","alert","off"]
data.state.alertType	<String,Optional>	Is in reminder
data.state.sound	<Integer,Necessary>	Sound level of device
data.state.battery	<Integer,Necessary>	Level of device's battery, 0 to 4 means empty to full
data.state.powerSupply	<Boolean,Necessary>	Is power supply connected
data.state.beep	<Boolean,Necessary>	Is beep enabled
data.state.mute	<Boolean,Necessary>	Is in mute mode
data.state.version	<String,Necessary>	Firmware Version of device
data.reportAt	<Date,Necessary>	Time of reported
data.deviceId	<String,Necessary>	Id of device
*/

preferences {
    input title: bold("Driver Version"), description: "Power Failure Alarm (YS7106-UC) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink PowerFailureAlarm Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"				
		capability "Battery"
        capability "Alarm"                  // ENUM ["strobe", "off", "both", "siren"]    
        capability "Switch"                 // ENUM ["on", "off"]
        capability "SignalStrength"
       
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:[true, false]]] 
        command "reset"    
        
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"          
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "lastResponse", "String"    
        attribute "reportAt", "String"

      //attribute "volume", "Number" 
      //attribute "alarmDuration", "Number"        
      //attribute "alertInterval", "Number"
      //attribute "mute", "String"    
        }
   }

def both() {unsupported("Both")}
def on() {unsupported("On")}
def off() {unsupported("Off")}
def siren() {unsupported("Siren")}
def strobe() {unsupported("Strobe")}            

def unsupported(cmd) {
    lastResponse("The '$cmd' command is not supported on a Power Failure Alarm")
    log.warn "The '$cmd' command is not supported on a Power Failure Alarm"
}

void setDeviceToken(token) {
    if (state.token != token) { 
      log.warn "Device token '${state.token}' changed to '${token}'"
      state.token=token
    } else {    
      logDebug("Device token remains set to '${state.token}'")
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
       pollDevice()
       state.lastPoll = now()
    }
 }

def pollDevice(delay=1) {
    rememberState("driver", clientVersion())
    runIn(delay,getDevicestate)
    def date = new Date()
    sendEvent(name:"lastPoll", value: date.format("MM/dd/yyyy hh:mm:ss a"), isStateChange:true)
 }

def temperatureScale(value) {}

def debug(value) { 
   rememberState("debug",value)
   if (value == "true") {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

def getDevicestate() {  
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
                parseDevice(object)                     
                rc = true	
                rememberState("online", "true") 
                lastResponse("Success") 
            } else {  //Error
               pollError(object)                
            }     
        } else {
            log.error "No response from API request"
            lastResponse("No response from API") 
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

def parseDevice(object) {   
    def online = object.data.online
    def battery = parent.batterylevel(object.data.state.battery)
    def alarm = object.data.state.state
    def firmware = object.data.state.version.toUpperCase()
   
    rememberState("online",online)
    rememberState("battery",battery)
    rememberState("firmware",firmware)    
   
  //def alarmDuration = duration(object.data.state.alertDuration)
  //def alertInterval = duration(object.data.state.alertInterval)
  //def mute = object.data.state.mute  
  //def volume = object.data.state.sound
  //rememberState("alarmDuration",alarmDuration)
  //rememberState("alertInterval",alertInterval)
  //rememberState("mute",mute)    
  //rememberState("volume",volume)
    
    setAlarmState(alarm)
                   
    logDebug("Online(${online}), " +
             "Battery(${battery}), " + 
             "Alarm(${state.alarm}), " +
             "Firmware(${firmware})")
}   

def setAlarmState(alarm) {
    def swstate
    def online = true
       
    switch(alarm) {		
        case "normal":       
            alarm = "off" 
            swstate = "on" 
 		    break;   
            
        case "alert":
            alarm = "both" 
            swstate = "off"  
            break;   
            
        case "off":
            alarm = "off" 
            swstate = "on" 
            break; 
        
        case "disabled":
            alarm = "disabled" 
            swstate = "disabled"                      
            log.warn "Siren has been turned off using the device's switch"
            online = false
            lastResponse("Siren has been turned off using the device's switch")   
            break; 
           
		default:
            alarm = "unknown" 
            swstate ="unknown"            
            log.error "Unknown power state received: ${alarm}"
            online = false
			break;
	    }				
    
    rememberState("alarm",alarm)         
    rememberState("switch",swstate)  
    rememberState("online",online) 
    
    logDebug("setAlarmState(): Alarm(${state.alarm}), Switch(${state.switch})")
}    

def duration(seconds) {
    if (seconds == 65535) {seconds = "Forever"}
    return seconds                              
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
/*            
		case "Report":
            def alarm = object.data.state
            def volume = object.data.sound    
            def battery = parent.batterylevel(object.data.battery) 
            def alarmDuration = duration(object.data.alertDuration)
            def alertInterval = duration(object.data.alertInterval)  
            def mute = object.data.mute
            def firmware = object.data.version.toUpperCase()
            def rssi = object.data.loraInfo.signal
    
            rememberState("volume",volume)
            rememberState("battery",battery)
            rememberState("alarmDuration",alarmDuration)
            rememberState("alertInterval",alertInterval)
            rememberState("mute",mute)
            rememberState("firmware",firmware)    
            fmtSignal(rssi)
    
            setAlarmState(alarm)
 		    break;       
  */          
        case "getState":       
            parseDevice(object) 
 		    break;   
            
        case "setOption":
          //def alarmDuration = duration(object.data.alertDuration) 
          //def alertInterval = duration(object.data.alertInterval)
          //def mute = object.data.mute
            def rssi = object.data.loraInfo.signal
           
          //rememberState("alarmDuration",alarmDuration)
          //rememberState("alertInterval",alertInterval)
          //rememberState("mute",mute)
            fmtSignal(rssi)
		    break;     
            
        case "StatusChange":  
          //def volume = object.data.sound 
          //def alarmDuration = duration(object.data.alertDuration)
          //def alertInterval = duration(object.data.alertInterval)
          //def mute = object.data.mute
          //rememberState("volume",volume)
          //rememberState("alarmDuration",alarmDuration)
          //rememberState("alertInterval",alertInterval)
          //rememberState("mute",mute)
            def alarm = object.data.state
            def battery = parent.batterylevel(object.data.battery) 
            def firmware = object.data.version.toUpperCase()
            def rssi = object.data.loraInfo.signal
            rememberState("battery",battery)
            rememberState("firmware",firmware)    
            fmtSignal(rssi)
    
            setAlarmState(alarm)
 		    break;       

        case "Alert":
          //def volume = object.data.sound    
          //def alarmDuration = duration(object.data.alertDuration)
          //def alertInterval = duration(object.data.alertInterval)
          //def mute = object.data.mute
          //rememberState("volume",volume)
          //rememberState("alarmDuration",alarmDuration)
          //rememberState("alertInterval",alertInterval)
          //rememberState("mute",mute)
            def alarm = object.data.state            
            def firmware = object.data.version.toUpperCase()
            def rssi = object.data.loraInfo.signal
            def battery = parent.batterylevel(object.data.battery)             
            rememberState("battery",battery)
            rememberState("firmware",firmware)    
            fmtSignal(rssi)
    
            setAlarmState(alarm)
 		    break;       
            
        case "setTimeZone":
            break;              
            
        case "setState":  //"normal","alert","off"
            def alarm = object.data.state    
            def rssi = object.data.loraInfo.signal
            
            setAlarmState(alarm)
            
            fmtSignal(rssi)
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
    state.remove("battery")

    state.remove("firmware")
    state.remove("rssi")
    state.remove("signal")
    state.remove("alarm")
    state.remove("switch")
    
    state.remove("mute")
    state.remove("alarmDuration")
    state.remove("alertInterval")
    state.remove("volume")
          
    poll(true)    
    
    lastResponse("Device reset to default values")
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
