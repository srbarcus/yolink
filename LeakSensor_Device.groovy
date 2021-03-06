/***
 *  YoLink™ LeakSensor (YS7903-UC)
 *  © 2022 Steven Barcus
 *  THIS SOFTWARE IS NEITHER DEVELOPED, ENDORSED, OR ASSOCIATED WITH YoLink™ OR YoSmart, Inc.
 *   
 *  DO NOT INSTALL THIS DEVICE MANUALLY - IT WILL NOT WORK. MUST BE INSTALLED USING THE YOLINK DEVICE SERVICE APP  
 *
 *  Donations are appreciated and allow me to purchase more YoLink devices for development: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1
 *   
 *  Developer retains all rights, title, copyright, and interest, including patent rights and trade
 *  secrets in this software. Developer grants a non-exclusive perpetual license (License) to User to use
 *  this software under this Agreement. However, the User shall make no commercial use of the software without
 *  the Developer's written consent. Software Distribution is restricted and shall be done only with Developer's written approval.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *  
 *  v1.0.1 - Process "Report" notification
 *         - Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 */

import groovy.json.JsonSlurper

def clientVersion() {return "1.0.1"}

preferences {
    input title: "Driver Version", description: "YoLink™ LeakSensor (YS7903-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
	input title: "Please donate", description: "Donations allow me to purchase more YoLink devices for development. Copy and Paste the following into your browser: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink LeakSensor Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"	
        capability "WaterSensor"                //water - ENUM ["wet", "dry"]
        capability "TemperatureMeasurement"
        capability "Battery"
              
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]]  
        command "connect"                       // Attempt to establish MQTT connection
        command "reset"

      //command "interval", ['integer']                                                                                                  // Not supported? Get device connection error
      //command "beep", [[name:"beep",type:"ENUM", description:"Beep when device alerts", constraints:["True", "False"]]]                // Not supported
      //command "mode", [[name:"mode",type:"ENUM", description:"Mode for leak sensor", constraints:["WaterPeak","WaterLeak"]]]           // Not supported
      //command "sensitivity", [[name:"sensitivity",type:"ENUM", description:"Sensitivity of leak sensor", constraints:["low","high"]]]  // Not supported
                
        attribute "API", "String" 
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "signal", "String"
        attribute "lastResponse", "String" 
        
        attribute "interval", "integer"
      //attribute "beep", "String"              - Not supported
      //attribute "mode", "String"              - Supported, but irrelevant since can't be changed
      //attribute "sensitivity", "String"       - Not supported
      //attribute "supportChangeMode", "String" - Not supported   
        attribute "state", "String"  
        attribute "stateChangedAt", "String"  
     
        attribute "reportAt", "String"        
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

/* Setting of params through API appears to not be supported as always returns "Device offline" error
def setParam(value) {
   //params.interval	<Number,Optional>	Interval (in minutes) for continuous alarm.
   //params.beep	<Boolean,Optional>	Weather to enable beep when device alerts
   //params.sensorMode	<String,Optional>	Work mode for leak sensor,["WaterPeak":"WaterLeak"]
   //params.sensitivity	<String,Optional>	Sensitivity for leak sensor,["low","high"]

   def params = [:]   
   //params.put("sensitivity", value.toInteger())   
   params.put("sensitivity", value) 
    
   def request = [:] 
   request.put("method", "LeakSensor.setSettings")                
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
        def swState
         
        if (object) {
            logDebug("setOption(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {                               
                logDebug("setOption() was successful")               
                
            } else {
                pollError(object)  
            }                         						
                
	    } else { 			               
            logDebug("setOption() failed")	            
        }     		
	} catch (e) {	
        log.error "setOption() exception: $e" 
	} 
}
*/

def getDevicestate() {
    state.driver=clientVersion()
    
	logDebug("getDevicestate() obtaining device state")
    
	boolean rc=false	//DEFAULT: Return Code = false   
    
	try {  
        def request = [:]
            request.put("method", "${state.type}.getState")                 
            request.put("targetDevice", "${state.devId}") 
            request.put("token", "${state.token}") 
        
        def object = parent.pollAPI(request,state.name,state.type)
      
        if (object) {
            logDebug("getDevicestate()> pollAPI() response: ${object}")     
            
            if (successful(object)) {                
                parseDevice(object)                     
                rc = true	
                rememberState("online", "true") 
                lastResponse("Success") 
            } else {  //Error
               if (pollError(object) ) {  //Cannot connect to Device
                 rememberState("switch", "unknown")                      
               }
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
   def temperature = object.data.state.devTemperature      
   def interval = object.data.state.interval      
   def mode = object.data.state.sensorMode                        //Supported, but irrelevant since can't be changed
   def swState = object.data.state.state
   def stateChangedAt = object.data.state.stateChangedAt
   def supportChangeMode = object.data.state.supportChangeMode    //Supported, but irrelevant since always false
   def firmware = object.data.state.version
   def reportAt = object.data.reportAt
       
   temperature = parent.convertTemperature(temperature)  
   swState = contactState(swState)    
     
   logDebug("Parsed: Online=$online, State=$swState, Battery=$battery, Temperature=$temperature, Alert Interval=$interval, Mode=$mode, Firmware=$firmware, State Changed At=$stateChangedAt, Support Change Mode=$supportChangeMode, Reported at=$reportAt")      
                
   rememberState("online", online)
   rememberState("state", swState)
   rememberState("battery", battery)
   rememberState("temperature", temperature) 
   rememberState("interval", interval)
   rememberState("stateChangedAt", stateChangedAt) 
   rememberState("firmware", firmware)
   rememberState("reportAt", reportAt)                        
 //rememberState("mode", mode)    
 //rememberState("supportChangeMode", supportChangeMode)      
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
		case "setInterval":
            def interval = object.data.interval      
            def mode = object.data.sensorMode                        //Supported, but irrelevant since can't be changed   
            def signal = object.data.loraInfo.signal             
    
            logDebug("Parsed: Interval=$interval, Mode=$mode, Signal=$signal")
            
            rememberState("online", "true")
            rememberState("interval",interval)
         // rememberState("mode",mode)
            rememberState("signal",signal)                          
 		    break;
            
        case "Report":     
            def interval = object.data.interval 
            rememberState("interval", "interval")
        case "StatusChange":     
  		case "Alert":
            def mode = object.data.sensorMode                        //Supported, but irrelevant since can't be changed
            def swState = object.data.state
            def battery = parent.batterylevel(object.data.battery) 
            def firmware = object.data.version
            def temperature = object.data.devTemperature   
            def signal = object.data.loraInfo.signal             
            def stateChangedAt = object.data.stateChangedAt
       
            temperature = parent.convertTemperature(temperature) 
            swState = contactState(swState)
            
            logDebug("Parsed: Mode=$mode, State=$swState, Battery=$battery, Temperature=$temperature, Firmware=$firmware, State Changed At=$stateChangedAt, Signal=$signal")      
                
            rememberState("online", "true")
            rememberState("state", swState)
            rememberState("battery", battery)
            rememberState("firmware", firmware)
            rememberState("temperature", temperature) 
            rememberState("signal", signal)
            rememberState("stateChangedAt", stateChangedAt)                     
          //rememberState("mode", mode)    
          
			break;
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
}

def setSwitch(setState) {
   def params = [:] 
   params.put("state", setState)    
    
   def request = [:]    
   request.put("method", "${state.type}.setState")   
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
        def swState
         
        if (object) {
            logDebug("setSwitch(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {               
                swState = parent.relayState(object.data.state)   
                def signal = object.data.loraInfo.signal       
                logDebug("Parsed: Switch=$swState, Signal=$signal")
                rememberState("switch",swState)
                rememberState("signal",signal)  
                lastResponse("Switch ${swState}")     
                               
            } else {
                swState = "unknown"
                if (notConnected(object)) {  //Cannot connect to Device
                   rememberState("switch","unknown")  
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
               }
            }                     
                                        
            return swState							
                
	    } else { 			               
            logDebug("setSwitch() failed")	
            state.swState = "unknown"
            sendEvent(name:"switch", value: state.swState, isStateChange:true)
            lastResponse("setSwitch() failed")     
        }     		
	} catch (e) {	
        log.error "setSwitch() exception: $e"
        lastResponse("Error ${e}")     
        state.swState = "unknown"
        sendEvent(name:"switch", value: state.swState, isStateChange:true)  
	} 
}   

def contactState(value) {
   if (value == "alert") {
        return "wet"
   } else {
       if (value == "normal") {
           return "dry"    
       } else {    
           return "unknown"    
       }
   }    
}    

def reset(){          
    state.debug = false
    state.remove("API")
    
    state.remove("online")
    state.remove("state")
    state.remove("battery")
    state.remove("temperature")
    state.remove("interval")
    state.remove("firmware")
    state.remove("reportAt")
    state.remove("stateChangedAt")
    
  //state.remove("beep")               - Not Supported
  //state.remove("mode")               - Supported, but irrelevant since can't be changed
  //state.remove("sensitivity")        - Not Supported 
  //state.remove("supportChangeMode")  - Supported, but irrelevant since always false
   
    interfaces.mqtt.disconnect()      // Guarantee we're disconnected  
    connect()                         // Reconnect to API Cloud  
    poll(true)
   
    lastResponse("Device reset to default values")   
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
  return (object.code  == "000000")     
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
