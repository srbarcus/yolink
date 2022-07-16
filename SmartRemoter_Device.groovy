/***
 *  YoLink™ Fob (YS3604-UC)
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
 * 01.00.01: Fixed errors in poll()
 */

import groovy.json.JsonSlurper

def clientVersion() {return "01.00.01"}

preferences {
    input title: "Driver Version", description: "YoLink™ Fob (YS3604-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
	input title: "Please donate", description: "Donations allow me to purchase more YoLink devices for development. Copy and Paste the following into your browser: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink SmartRemoter Device", namespace: "srbarcus", author: "Steven Barcus") {   
        capability "Polling"	
        capability "Battery"
        capability "Temperature Measurement"
        capability "HoldableButton"
        capability "PushableButton"
        
        command "debug", ['boolean']
        command "connect"                       // Attempt to establish MQTT connection
        command "reset"            
        command "hold", ['integer']
        command "push", ['integer']            
                 
        attribute "API", "String" 
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "signal", "String" 
        attribute "battery", "String"  
        attribute "lastResponse", "String" 
        
        attribute "remoteType", "String"         
        attribute "held", "Integer" 
        attribute "numberOfButtons", "Integer" 
        attribute "pushed", "Integer"         
        
        attribute "temperature", "String" 
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
    rememberState("numberOfButtons",4)         
    rememberState("remoteType","FlexFob")  
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

def push(button) {    
   if (button.isNumber()) {
      button = button.toInteger() 
      if ((button >= 1) && (button <= 4)) { 
          rememberState("pushed",button)                    
          rememberState("action","pushed")
      } else {
          log.error "Specified button (${button}) is outside of allowable range of 1 to 4"
      } 
    } else {
       log.error "Specified button (${button}) is non-numeric"
    }    
 }

def hold(button) {   
   if (button.isNumber()) {
      button = button.toInteger()  
      if ((button >= 1) && (button <= 4)) { 
          rememberState("held",button)
          rememberState("action","held")
      } else {
          log.error "Specified button (${button}) is outside of allowable range of 1 to 4"
      } 
    } else {
       log.error "Specified button (${button}) is non-numeric"
    }     
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
   def devId = object.data.deviceId  
   def reportAt = object.data.reportAt
    
   def online = object.data.online
    
   def battery = parent.batterylevel(object.data.state.battery)            
   def temperature = object.data.state.devTemperature
   def firmware = object.data.state.version   
      
   temperature = parent.convertTemperature(temperature)   
    
   logDebug("Parsed: DeviceId=$devId, Battery=$battery, Temperature=$temperature, Report At=$reportAt, Firmware=$firmware, Online=$online")      
                
   rememberState("online", "true")
   rememberState("battery", battery)
   rememberState("reportAt", reportAt)  
   rememberState("temperature", temperature)   
   rememberState("firmware", firmware)   
}   


def check_MQTT_Connection() {
  def MQTT = interfaces.mqtt.isConnected()  
  logDebug("MQTT connection is ${MQTT}")  
  if (MQTT) {  
     rememberState("API", "connected")
     lastResponse("API MQTT is connected")     
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
		case "StatusChange":
            def button = object.data.event.keyMask 
            def action = object.data.event.type 
            def battery = parent.batterylevel(object.data.battery)
            def firmware = object.data.version            
            def temperature = object.data.devTemperature
            def signal = object.data.loraInfo.signal  
    
            temperature = parent.convertTemperature(temperature)            
               
            logDebug("Parsed: DeviceId=$devId, Button=$button, Action=$action, Battery=$battery, Firmware=$firmware, Temperature=$temperature, Signal=$signal")
            
            switch(button) {
		        case "4":                      
                    button = 3                    
                    break;
                case "8":          
                    button = 4                    
                    break;                  
	        }
            
            switch(action) {
                case "Press":   
                    rememberState("pushed",button)  
                    rememberState("action","pushed")  
                    break;
                case "LongPress":          
                    rememberState("held",button)               
                    rememberState("action","held")  
                    break;                  
	        }
            
            rememberState("battery",battery)
            rememberState("firmware",firmware)  
            rememberState("signal",signal)                                
            rememberState("temperature",temperature)                                    
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
   request.put("method", "Switch.setState")                
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
                if (object.code == "000201") {  //Cannot connect to Device" 
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

def reset(){          
    state.debug = false
    state.remove("API")
    state.remove("firmware")
    state.remove("switch")
    state.remove("delay_ch")
    state.remove("delay_on")
    state.remove("delay_off")    
    state.remove("power")
    state.remove("watt")   
    state.remove("time")  
    state.remove("tzone")   
    state.remove("signal")    
    state.remove("PowerOnState")
    state.remove("online")      
        
    interfaces.mqtt.disconnect()      // Guarantee we're disconnected  
    connect()                         // Connect to API Cloud
    poll(true)    
        
    logDebug("Device reset to default values")
}

def lastResponse(value) {
   sendEvent(name:"lastResponse", value: "$value", isStateChange:true)   
}

def rememberState(name,value) {
   if (state."$name" != value) {
     state."$name" = value   
     sendEvent(name:"$name", value: "$value", isStateChange:true)
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