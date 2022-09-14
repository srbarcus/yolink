/***
 *  YoLink™ VibrationSensor Device (YS7201-UC)
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
 *  1.0.1: Fix donation URL
 *  1.0.2: Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions 
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.0"}

preferences {
    input title: "Driver Version", description: "YoLink™ VibrationSensor Device (YS7201-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink VibrationSensor Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"				
		capability "Battery"
        capability "Temperature Measurement"
        capability "MotionSensor"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]] 
        command "reset" 
        
        attribute "API", "String" 
        attribute "online", "String"
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
    state.devId = devId   
    	
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

def installed() {
 }

def updated() {
 }

def uninstalled() {
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
    state.lastPoll = now()    
 }

def temperatureScale(value) {
    state.temperatureScale = value
 }

def debug(value) { 
   rememberState("debug",value)
   if (value) {
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
               def noVibrationDelay = object.data.state.noVibrationDelay     
               noVibrationDelay = noVibrationDelay * 60    
               def sensitivity = object.data.state.sensitivity
               def devstate = object.data.state.state    
               def firmware = object.data.state.version        
                    
               def motion = "active"                                 //ENUM ["inactive", "active"]
               if (devstate == "normal"){motion="inactive"} 
                
               logDebug("Device State: online(${online}), " +
                        "Report At(${reportAt}), " +
                        "Alert Interval(${alertInterval}), " +
                        "Battery(${battery}), " +
                        "Temperature(${temperature}), " +   
                        "LED Alarm(${ledAlarm}), " +
                        "No Vibration Delay(${noVibrationDelay}), " +
                        "Sensitivity(${sensitivity}), " + 
                        "State(${devstate}), " +  
                        "Motion(${motion}), " + 
                        "Firmware(${firmware})")

               rememberState("online",online) 
               rememberState("reportAt",reportAt) 
               rememberState("alertInterval",alertInterval) 
               rememberState("battery",battery)
               rememberState("temperature",temperature)
               rememberState("ledAlarm",ledAlarm)
               rememberState("noVibrationDelay",noVibrationDelay)
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
            def alertInterval = object.data.alertInterval                
            def noVibrationDelay = object.data.noVibrationDelay     
            noVibrationDelay = noVibrationDelay * 60    
            def sensitivity = object.data.sensitivity  
            
            def temperature = object.data.devTemperature 
            temperature = parent.convertTemperature(temperature) 

            def signal = object.data.loraInfo.signal           
            
            def motion = "active"                                 //ENUM ["inactive", "active"]
            if (devstate == "normal"){motion="inactive"}                               
    
            logDebug("Parsed: State=$devstate, Battery=$battery, Alert Interval=$alertInterval, No Vibration Delay=$nomotionDelay, Sensitivity=$sensitivity, Temperature=$temperature, Signal=$signal, Motion=$motion")
            
            rememberState("state",devstate)
            rememberState("battery",battery)                     
            rememberState("alertInterval",alertInterval)
            rememberState("noVibrationDelay",noVibrationDelay)
            rememberState("sensitivity",sensitivity)  
            rememberState("temperature",temperature) 
            rememberState("signal",signal)  
            rememberState("motion",motion)           
 		    break;      
            
        case "Report":
			def devstate = object.data.state                     
            def battery = parent.batterylevel(object.data.battery)    // Value = 0-4    
            def firmware = object.data.version               
            def alertInterval = object.data.alertInterval    
            def noVibrationDelay = object.data.noVibrationDelay 
            noVibrationDelay = noVibrationDelay * 60  
            def sensitivity = object.data.sensitivity              
            def signal = object.data.loraInfo.signal              
            def temperature = object.data.devTemperature 
                
            temperature = parent.convertTemperature(temperature) 
                        
            def motion = "active"                                 //ENUM ["inactive", "active"]
            if (devstate == "normal"){motion="inactive"} 
    
            logDebug("Parsed: DeviceId=$devId, State=$devstate, Battery=$battery, Firmware=$firmware, LED Alarm=$ledAlarm, Alert Interval=$alertInterval, No Motion Delay=$nomotionDelay, Sensitivity=$sensitivity, Signal=$signal, Motion=$motion, Temperature=$temperature")
            
            rememberState("state",devstate)
            rememberState("battery",battery)            
            rememberState("firmware",firmware)         
            rememberState("alertInterval",alertInterval)
            rememberState("noVibrationDelay",noVibrationDelay)
            rememberState("sensitivity",sensitivity)                        
            rememberState("signal",signal)              
            rememberState("temperature",temperature)
            rememberState("motion",motion)
 		    break;    
            
       case "setOpenRemind": 
            def alertInterval = object.data.alertInterval    
            def noVibrationDelay = object.data.noVibrationDelay   
            noVibrationDelay = noVibrationDelay * 60    
            def sensitivity = object.data.sensitivity              
            def signal = object.data.loraInfo.signal                                     
            
            logDebug("Parsed: Alert Interval=$alertInterval, No Vibration Delay=$noVibrationDelay, Sensitivity=$sensitivity, Signal=$signal")
            
            rememberState("alertInterval",alertInterval)
            rememberState("noVibrationDelay",noVibrationDelay)
            rememberState("sensitivity",sensitivity)                        
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
    state.remove("firmware")     
    state.remove("battery")     
    state.remove("signal")  
    state.remove("online")
    state.remove("reportAt")
    state.remove("temperature")
    state.remove("alertInterval")
    state.remove("noVibrationDelay")
    state.temperatureScale = parent.temperatureScale
    
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
