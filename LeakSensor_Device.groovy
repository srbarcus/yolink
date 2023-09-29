/***
 *  YoLink™ LeakSensor (YS7903-UC)
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
 *  1.0.1: Process "Report" notification
 *          - Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *  1.0.2: Fix syncing of Temperature scale with YoLink™ Device Service app
 *  1.1.0: Fix donation URL 
 *         - New Function: Formats event timestamp according to user specifiable format
 *  1.1.1: Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions
 *  2.0.1: Added correct state ('water') for WaterSensor capability - fixes dashboard errors. Remove unused setSwitch() routine.
 *  2.0.2: Support diagnostics, correct various errors, make singleThreaded
 *  2.0.3: Support Switch capability via alerts and alertThreshold
 *         - Clean up code
 *         - Add SignalStrength capability (Replaces 'signal' attribute with standard 'rssi')
 *  2.0.4: Added unit values to: temperature, battery
 *         - Add formatted "signal" attribute as rssi & " dBm"
 *  2.0.5: Prevent Service app from waiting on device polling completion
 *  2.0.6: Updated driver version on poll
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.6"}
def copyright() {return "<br>© 2022, 2023 Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ LeakSensor (YS7903-UC) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Date Format Template Specifications"), description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink LeakSensor Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"	
        capability "WaterSensor"                //water - ENUM ["wet", "dry"]
        capability "TemperatureMeasurement"
        capability "Battery"
        capability "Switch"                     //switch - ENUM ["on", "off"] 
        capability "SignalStrength"             //rssi 
              
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]]   
        command "reset"
        command "timestampFormat", [[name:"timestampFormat",type:"STRING", description:"Formatting template for event timestamp values. See Preferences below for details."]] 
        command "alertThreshold", [[name:"alertThreshold",type:"NUMBER", description:"Number of successive alerts before switch state is set to 'on'"]]
                
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"          
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "lastResponse", "String" 
        
        attribute "interval", "integer"
        attribute "state", "String"  
        attribute "stateChangedAt", "String"  
        
        attribute "alerts", "integer"
        attribute "alertThreshold", "integer"
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

def alertThreshold(alertThreshold) {
   alertThreshold = alertThreshold.toInteger() 
    
   if (alertThreshold <= 0) {
       alertThreshold = 1
   } 
    
   rememberState("alertThreshold", alertThreshold)
   
   def alerts = state.alerts ?: 0
      
   if (alerts.toInteger() < alertThreshold) {
       reqState = "off"
   } else {
       reqState = "on"
   }   
    
  rememberState("switch", reqState)    
 }

def temperatureScale(value) {
    state.temperatureScale = value
 }

def timestampFormat(value) {
    value = value ?: "MM/dd/yyyy hh:mm:ss a" // No value, reset to default
    def oldvalue = state.timestampFormat 
    
    //Validate requested value
    try{                           
       def date = new Date()  
       def stamp = date.format(value)   
       state.timestampFormat = value   
       logDebug("Date format set to '${value}'")
       logDebug("Current date and time in requested format: '${stamp}'")  
     } catch(Exception e) {       
       log.error "Requested date format, '${value}', is invalid. Format remains '${oldvalue}'" 
     } 
 }

def on() {off()}
def off() {log.info "Switch command is non-functional for this device type."}
                     
def debug(value) { 
   rememberState("debug",value)
   if (value=="true") {
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
                 rememberState("water", "unknown")   
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
   def swState = object.data.state.state
   def stateChangedAt = object.data.state.stateChangedAt
   def firmware = object.data.state.version.toUpperCase()
       
   temperature = parent.convertTemperature(temperature) 
   stateChangedAt = formatTimestamp(stateChangedAt) 
     
   logDebug("Parsed: Online=$online, State=$swState, Battery=$battery, Temperature=$temperature, Alert Interval=$interval, Firmware=$firmware, State Changed At=$stateChangedAt")      
                
   rememberState("online", online)
   rememberState("state", swState)
   rememberState("water", waterState(swState)) 
   rememberState("battery", battery, "%")
   rememberState("temperature", temperature, "°".plus(state.temperatureScale)) 
   rememberState("interval", interval)
   rememberState("stateChangedAt", stateChangedAt) 
   rememberState("firmware", firmware)
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
		case "setInterval":
            def interval = object.data.interval      
            def rssi = object.data.loraInfo.signal             
    
            logDebug("Parsed: Interval=$interval, RSSI=$rssi")
            
            rememberState("online", "true")
            rememberState("interval",interval)
            fmtSignal(rssi)
 		    break;
            
        case "Report":     
        case "StatusChange":     
  		case "Alert":
            if (event == "Report") {
              def interval = object.data.interval 
              rememberState("interval", interval)
            }    
            
            def swState = object.data.state
            def battery = parent.batterylevel(object.data.battery) 
            def firmware = object.data.version.toUpperCase()
            def temperature = object.data.devTemperature   
            def rssi = object.data.loraInfo.signal  
            def stateChangedAt = object.data.stateChangedAt
       
            temperature = parent.convertTemperature(temperature) 
            stateChangedAt = formatTimestamp(stateChangedAt) 
                        
            logDebug("Parsed: State=$swState, Battery=$battery, Temperature=$temperature, Firmware=$firmware, State Changed At=$stateChangedAt, RSSI=$rssi")      
                
            rememberState("online", "true")
            rememberState("state", swState)
            if ((event == "Alert") || (event == "StatusChange"))  {rememberState("water", waterState(swState,"alert"))
            } else {rememberState("water", waterState(swState))}
            rememberState("battery", battery, "%")
            rememberState("firmware", firmware)
            rememberState("temperature", temperature, "°".plus(state.temperatureScale)) 
            fmtSignal(rssi)
            rememberState("stateChangedAt", stateChangedAt)                     
          
			break;
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
}

def waterState(value,alert=null) {
   switch(value) {
		case "alert":
             alertCount("on",alert)
             return "wet"
        case "normal":     
             alertCount("off",alert)
             return "dry"      
        case "dry":        
             alertCount("off",alert)
             return "dry" 
        case "wet":        
             alertCount("on",alert)
             return "wet" 
		default:
            log.error "Unknown water state received: $value"
            return value 
			break;
	    }  
  }   

def alertCount(swState, alert) {     
    def reqState = swState
    
    def alerts = state.alerts ?: 0
    def alertThreshold = state.alertThreshold ?: 1
    
    alerts = alerts.toInteger()
    alertThreshold = alertThreshold.toInteger()
    
    if (alert == "alert") {
      if (swState == "on") {           
         alerts = alerts + 1  
         if (alerts < alertThreshold) {reqState = "off"}
      } else {
         alerts = 0
      }
    }   
  
  logDebug("alertCount($swState, $alert) $reqState, Alerts=$alerts, Threshold=$alertThreshold")  
    
  rememberState("alerts", alerts)
  rememberState("alertThreshold", alertThreshold)     
  rememberState("switch", reqState)   
}       

def formatTimestamp(timestamp){    
    if (state.timestampFormat != null) {
      def date = new Date( timestamp as long )    
      date = date.format(state.timestampFormat)
      logDebug("formatTimestamp(): '$state.timestampFormat' = '$date'")
      return date  
    } else {
      return timestamp  
    }    
}

def reset(){   
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("state")
    state.remove("water")
    state.remove("battery")
    state.remove("temperature")
    state.remove("interval")
    state.remove("firmware")
    state.remove("rssi")
    state.remove("signal")
    state.remove("stateChangedAt")
    
    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a" 
    
    state.remove("alerts")
    rememberState("alerts", 0) 
    state.remove("alertThreshold")
    rememberState("alertThreshold", 1) 

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