/***
 *  YoLink™ Siren (YS7103-UC)
 *  © 2022 Steven Barcus
 *  THIS SOFTWARE IS NEITHER DEVELOPED, ENDORSED, OR ASSOCIATED WITH YoLink™ OR YoSmart, Inc.
 *   
 *  DO NOT INSTALL THIS DEVICE MANUALLY - IT WILL NOT WORK. MUST BE INSTALLED USING THE YOLINK DEVICE SERVICE APP  
 *
 *  Donations are appreciated and allow me to purchase more YoLink devices for development: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1&currency_code=USD
 *   
 *  Developer retains all rights, title, copyright, and interest, including patent rights and trade
 *  secrets in this software. Developer grants a non-exclusive perpetual license (License) to User to use
 *  this software under this Agreement. However, the User shall make no commercial use of the software without
 *  the Developer's written consent. Software Distribution is restricted and shall be done only with Developer's written approval.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * 
 *  1.0.1: Remove superfluous code
 *  1.0.2: (skipped)
 *  1.0.3: Fixed clientVersion()
 *  1.0.4: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *         - Correct attribute types
 */

import groovy.json.JsonSlurper

def clientVersion() {return "1.0.4"}

preferences {
    input title: "Driver Version", description: "Siren (YS7103-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
	input title: "Please donate", description: "Donations allow me to purchase more YoLink devices for development. Copy and Paste the following into your browser: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}


metadata {
    definition (name: "YoLink Siren Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"				
		capability "Battery"
        capability "Alarm"                  // ENUM ["strobe", "off", "both", "siren"]    
        capability "PowerSource"            // ENUM ["battery", "dc", "mains", "unknown"]  API "usb" = "mains"
       
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]] 
        command "connect"
        command "reset"          
             
        attribute "API", "String" 
        attribute "online", "String"
        attribute "firmware", "String"          
        attribute "signal", "String"  
        attribute "lastResponse", "String"    
        attribute "reportAt", "String"

        attribute "volume", "Number" 
        attribute "alarmDuration", "Number"        
        }
   }

void ServiceSetup(Hubitat_dni,homeID,devname,devtype,devtoken,devId) {
	state.debug = false	
    
    state.my_dni = Hubitat_dni      
    state.homeID = homeID    
    state.name = devname
    state.type = devtype
    state.token = devtoken
    state.devId = devId   
    	
	log.info "ServiceSetup(Hubitat dni=${state.my_dni}, Home ID=${state.homeID}, Name=${state.name}, Type=${state.type}, Token=${state.token}, Device Id=${state.devId})"
	    
    reset()      
 }

def installed() {
 }

def updated() {
 }

def uninstalled() {
   interfaces.mqtt.disconnect() // Guarantee we're disconnected 
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def poll(force=null) {
    if (force == null) {
      def min_interval = 10                  // To avoid unecessary load on YoLink servers, limit rate of polling
	  def min_time = (now()-(min_interval * 1000))
	  if ((state?.lastPoll) && (state?.lastPoll > min_time)) {
         log.warn "Polling interval of once every ${min_interval} seconds exceeded, device was not polled."	    
         return     
       } 
    }    
    
    getDevicestate() 
    check_MQTT_Connection()
    state.lastPoll = now()    
 }

def connect() {
    establish_MQTT_connection(state.my_dni)
 }

def debug(value) { 
    def bool = parent.validBoolean("debug",value)
    
    if (bool != null) {
        if (bool) {
            state.debug = true
            log.info "Debugging enabled"
        } else {
            state.debug = false
            log.info "Debugging disabled"
        }   
    }        
}

def strobe () {  
   setSiren("True")       
}

def both () {  
   setSiren("True")       
}

def siren () {  
   setSiren("True")       
}
    
def off () {
   setSiren("False")  
}     

def setSiren(setState) {   
   def params = [:] 
   def alarm = [:] 
    
   alarm.put("alarm", setState.toBoolean())    
   params.put("state", alarm)      
    
   def request = [:] 
   request.put("method", "${state.type}.setState")      
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
      def object = parent.pollAPI(request, state.name, state.type)       
    } catch (e) {	
        log.error "setSiren() exception: $e"
        lastResponse("Error ${e}")     
        sendEvent(name:"alarm", value: "unknown", isStateChange:true)
	} 
          
    getDevicestate()
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
    def online = true
    def alarm = object.data.state
    def volume = object.data.soundLevel    
    def battery = parent.batterylevel(object.data.battery) 
    def powerSource = object.data.powerSupply 
    if (powerSource == "usb") {powerSource = "mains"}
    def alarmDuration = duration(object.data.alarmDuation)   //API message has a spelling error   
    def firmware = object.data.version
    def signal = object.data.loraInfo.signal
                   
    logDebug("Device State: online(${online}), " +
             "Alarm(${alarm}), " +
             "Volume(${volume}), " +             
             "Battery(${battery}), " + 
             "Power Source(${powerSource}), " +
             "Alarm Duration(${alarmDuration}), " +
             "Firmware(${firmware}), " +
             "Signal(${signal})")     
             
    rememberState("online",online)
    rememberState("alarm",alarm)       
    rememberState("volume",volume)
    rememberState("battery",battery)
    rememberState("powerSource",powerSource)    
    rememberState("alarmDuration",alarmDuration)
    rememberState("firmware",firmware)    
    rememberState("signal",signal)
}   

def duration(seconds) {
    if (seconds == 65535) {seconds = "Forever"}
    return seconds                              
}

def check_MQTT_Connection() {
  def MQTT = interfaces.mqtt.isConnected()  
  logDebug("MQTT connection is ${MQTT}")  
  if (MQTT) {  
     rememberState("API", "connected")     
  } else {    
     establish_MQTT_connection(state.my_dni)      //Establish MQTT connection to YoLink API
  }
}    

def establish_MQTT_connection(mqtt_ID) {
    parent.refreshAuthToken()
    def authToken = parent.AuthToken() 
      
    def MQTT = "disconnected"
    
    def topic = "yl-home/${state.homeID}/${state.devId}/report"
    
    try {  	
        mqtt_ID =  "${mqtt_ID}_${state.homeID}"
        logDebug("Connecting to MQTT with ID '${mqtt_ID}', Topic:'${topic}, Token:'${authToken}")
      
        interfaces.mqtt.connect("tcp://api.yosmart.com:8003","${mqtt_ID}",authToken,null)                         	
          
        logDebug("Subscribing to MQTT topic '${topic}'")
        interfaces.mqtt.subscribe("${topic}", 0) 
         
        MQTT = "connected"          
          
        logDebug("MQTT connection to YoLink successful")
		
	} catch (e) {	
        log.error ("establish_MQTT_connection() Exception: $e")	
    }
     
    rememberState("API", MQTT)    
    lastResponse("API MQTT ${MQTT}")  
}    

def mqttClientStatus(String message) {                          
    logDebug("mqttClientStatus(${message})")

    if (message.startsWith("Error:")) {
        log.error "MQTT Error: ${message}"

        try {
            log.warn "Disconnecting from MQTT"    
            interfaces.mqtt.disconnect()           // Guarantee we're disconnected            
            rememberState("API","disconnected") 
        }
        catch (e) {
        } 
    }
}

def parse(message) { 
    def topic = interfaces.mqtt.parseMessage(message)
    def payload = new JsonSlurper().parseText(topic.payload)
    logDebug("parse(${payload})")

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
		case "Report":   
        case "StatusChange":   
        case "getState":       
            parseDevice(object) 
 		    break;   
            
        case "setDuation": //API message has a spelling error    
            def alarmDuration = duration(object.data.alarmDuation)   //API message has a spelling error    
            def signal = object.data.loraInfo.signal
            rememberState("alarmDuration",alarmDuration)
            rememberState("signal",signal)
            break;   
            
        case "setState":  //"normal","alert","off"
            def alarmState = object.data.state    
            def signal = object.data.loraInfo.signal
            rememberState("alarmState",alarmState)  
            rememberState("signal",signal)
            break; 
           
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }				
    }
}

def reset(){       
    state.debug = false  
    state.remove("API")
    state.remove("online")  
    state.remove("alarm")
    state.remove("volume") 
    state.remove("battery")
    state.remove("alarmDuration")
    state.remove("powerSource")    
    state.remove("firmware")
    state.remove("signal")
          
    interfaces.mqtt.disconnect()      // Guarantee we're disconnected  
    connect()                         // Reconnect to API Cloud  
    poll(true)    
    
    logDebug("Device reset to default values")
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
   if (state.debug) {log.debug msg}
} 
