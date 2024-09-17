/***
 *  YoLink™ Water Depth Sensor
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
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.0"}
def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "Water Depth Sensor v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"	
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Date Format Template Specifications"), description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink WaterDepthSensor Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"				
		capability "Battery" 
        capability "SignalStrength"             //rssi
        capability "WaterSensor"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]] 
        command "reset"  
       
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"         
                        
        attribute "detectorError", "String"
        attribute "highAlarm", "String"  
        attribute "lowAlarm", "String"  
        attribute "reminder", "String"
        attribute "alarmStandby", "String"
        attribute "alarmInterval", "String"   
        attribute "highWater", "String"   
        attribute "lowWater", "String"   
        
        attribute "waterDepth", "String"   
        attribute "water", "String"   
        attribute "reportInterval", "String"   
        
        attribute "probe", "String"   
        
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "lastResponse", "String"    
        attribute "reportAt", "String"          
        }
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
    def reportAt = object.data.reportAt     
    def firmware = object.data.state.version.toUpperCase()   
    def battery = parent.batterylevel(object.data.state.battery) 
    
    def detectorError = object.data.state.alarm.detectorError
    if (detectorError) {rememberState("probe", "disconnected")
    } else {rememberState("probe", "connected")}
    def highAlarm = object.data.state.alarm.highAlarm
    def lowAlarm = object.data.state.alarm.lowAlarm    
    def reminder = object.data.state.alarm.reminder
    
    def alarmStandby = object.data.state.alarmSettings.standby
    def alarmInterval = object.data.state.alarmSettings.interval
    def highWater = object.data.state.alarmSettings.high
    def lowWater = object.data.state.alarmSettings.low
    
    def reportInterval = object.data.state.reportInterval
    def waterDepth = object.data.state.waterDepth
    
    def water="wet"
    if ((lowAlarm) || (waterDepth == 0)) {
       water="dry"
    }
                   
    logDebug("Device State: online(${online}), " +
             "Report At(${reportAt}), " +
             "Firmware(${firmware}), " +
             "Battery(${battery}), " + 
             "detectorError(${detectorError}), " +
             "highAlarm(${highAlarm}), " +
             "lowAlarm(${lowAlarm}), " +
             "reminder(${reminder}), " +             
             "alarmStandby(${alarmStandby}), " +   
             "alarmInterval(${alarmInterval}), " +
             "highWater(${highWater}), " +
             "lowWater(${lowWater}), " + 
             "waterDepth(${waterDepth}), " + 
             "Water(${water}), " + 
             "reportInterval(${reportInterval})")  
       
    rememberState("online",online)
    rememberState("reportAt",reportAt)  
    rememberState("firmware",firmware)
    rememberState("battery", battery, "%")
    
    rememberState("detectorError",detectorError)
    rememberState("highAlarm",highAlarm)
    rememberState("lowAlarm",lowAlarm)
    rememberState("reminder",reminder)
    
    rememberState("alarmStandby",alarmStandby)   
    rememberState("alarmInterval",alarmInterval)
    rememberState("highWater",highWater)
    rememberState("lowWater",lowWater)
    
    rememberState("waterDepth",waterDepth)
    rememberState("water",water)
    rememberState("reportInterval",reportInterval)
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
		case "StatusChange":  
//data":{"alarm":{"highAlarm":false,"lowAlarm":false,"detectorError":false,"reminder":false},"battery":4,"waterDepth":168,"version":"050a",
//"loraInfo":{"netId":"010201","devNetType":"C","signal":-60,"gatewayId":"d88b4c160400012d","gateways":2}},"deviceId":"d88b4c0100082ab7"})
            
           def highAlarm = object.data.alarm.highAlarm
           def lowAlarm = object.data.alarm.lowAlarm 
           def detectorError = object.data.alarm.detectorError
           if (detectorError) {rememberState("probe", "disconnected")
           } else {rememberState("probe", "connected")}
           def reminder = object.data.alarm.reminder 
            
           def battery = parent.batterylevel(object.data.battery)            
           def waterDepth = object.data.waterDepth
           
           def firmware = object.data.version.toUpperCase()   
                     
           def rssi = object.data.loraInfo.signal
           fmtSignal(rssi)  
       
           def water="wet"
           if ((lowAlarm) || (waterDepth == 0)) {
               water="dry"
           }
            
           logDebug("Firmware(${firmware}), " +
                 "Battery(${battery}), " + 
                 "RSSI(${rssi}), " +  
                 "highAlarm(${highAlarm}), " +
                 "lowAlarm(${lowAlarm}), " +
                 "detectorError(${detectorError}), " +
                 "reminder(${reminder}), " +   
                 "waterDepth:(${waterDepth}), " +
                 "Water(${water})")   
            
            rememberState("firmware",firmware)
            rememberState("battery", battery, "%")                        
            rememberState("highAlarm",highAlarm)
            rememberState("lowAlarm",lowAlarm)
            rememberState("detectorError",detectorError)
            rememberState("reminder",reminder)
            rememberState("waterDepth",waterDepth)
            rememberState("water",water)   
            
 		    break;   
        
        case "Report":    
           def highAlarm = object.data.alarm.highAlarm
           def lowAlarm = object.data.alarm.lowAlarm 
           def detectorError = object.data.alarm.detectorError
           if (detectorError) {rememberState("probe", "disconnected")
           } else {rememberState("probe", "connected")}
           def reminder = object.data.alarm.reminder 
            
           def battery = parent.batterylevel(object.data.battery)            
           def waterDepth = object.data.waterDepth
           
           def alarmStandby = object.data.alarmSettings.standby 
           def alarmInterval = object.data.alarmSettings.interval
           def highWater = object.data.alarmSettings.high
           def lowWater = object.data.alarmSettings.low
           
           def reportInterval = object.data.reportInterval 
           def firmware = object.data.version.toUpperCase()   
                     
           def rssi = object.data.loraInfo.signal
           fmtSignal(rssi)  
       
           def water="wet"
           if ((lowAlarm) || (waterDepth == 0)) {
               water="dry"
           }
            
           logDebug("Firmware(${firmware}), " +
                 "Battery(${battery}), " + 
                 "RSSI(${rssi}), " +  
                 "highAlarm(${highAlarm}), " +
                 "lowAlarm(${lowAlarm}), " +
                 "detectorError(${detectorError}), " +
                 "reminder(${reminder}), " +   
                 "waterDepth:(${waterDepth}), " +
                 "alarmStandby(${alarmStandby}), " +   
                 "alarmInterval(${alarmInterval}), " +
                 "highWater(${highWater}), " +
                 "lowWater(${lowWater}), " +
                 "Water(${water}), " +   
                 "reportInterval(${reportInterval})")   
            
            rememberState("firmware",firmware)
            rememberState("battery", battery, "%")                        
            
            rememberState("highAlarm",highAlarm)
            rememberState("lowAlarm",lowAlarm)
            rememberState("detectorError",detectorError)
            rememberState("reminder",reminder)
            rememberState("waterDepth",waterDepth)
            rememberState("alarmStandby",alarmStandby)
            rememberState("alarmInterval",alarmInterval)            
            rememberState("highWater",highWater)
            rememberState("lowWater",lowWater)

            rememberState("water",water)   
            
 		    break;              
            
        case "Alert":    
            def highAlarm = object.data.alarm.highAlarm
            def lowAlarm = object.data.alarm.lowAlarm
            def detectorError = object.data.alarm.detectorError
            if (detectorError) {rememberState("probe", "disconnected")
            } else {rememberState("probe", "connected")}
            def reminder = object.data.alarm.reminder
            
            def battery = parent.batterylevel(object.data.battery)            
            def waterDepth = object.data.waterDepth
            
            def water="wet"
            if ((lowAlarm) || (waterDepth == 0)) {
               water="dry"
            }
                                           
            def rssi = object.data.loraInfo.signal  
            fmtSignal(rssi)
            
            logDebug("Alarm Message: " +             
             "Battery(${battery}), " + 
             "signal(${rssi}), " + 
             "highAlarm(${highAlarm}), " +
             "lowAlarm(${lowAlarm})," +
             "detectorError(${detectorError}), " +
             "reminder(${reminder}), " +   
             "waterDepth(${waterDepth}), " +
             "water(${water})")
            
            rememberState("battery", battery, "%")                        
            rememberState("highAlarm",highAlarm)
            rememberState("lowAlarm",lowAlarm)
            rememberState("detectorError",detectorError)
            rememberState("reminder",reminder)
            rememberState("waterDepth",waterDepth)
            rememberState("water",water)    
            break;             
            
        case "setAttributes": 
            def alarmStandby = object.data.alarmSettings.standby 
            def alarmInterval = object.data.alarmSettings.interval
            def highWater = object.data.alarmSettings.high
            def lowWater = object.data.alarmSettings.low
            
            def reportInterval = object.data.reportInterval
            def rssi = object.data.loraInfo.signal  
            fmtSignal(rssi)
            
            logDebug("setAttributes: " +                 
                 "RSSI(${rssi}), " +  
                 "alarmStandby(${alarmStandby}), " +   
                 "alarmInterval(${alarmInterval}), " +
                 "highWater(${highWater}), " +
                 "lowWater(${lowWater}), " +                 
                 "reportInterval(${reportInterval})")   
            
            rememberState("alarmStandby",alarmStandby)
            rememberState("alarmInterval",alarmInterval)    
            rememberState("highWater",highWater)    
            rememberState("lowWater",lowWater)    
            rememberState("reportInterval",reportInterval)  

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
    state.remove("rssi")
    state.remove("signal")
    state.remove("reportAt")
    state.remove("timeZone")
    state.remove("firmware")
    state.remove("battery")
    
    state.remove("detectorError")
    state.remove("highAlarm") 
    state.remove("lowAlarm")  
    state.remove("reminder")
    state.remove("alarmStandby")
    state.remove("alarmInterval") 
    state.remove("highWater")   
    state.remove("lowWater")   
    state.remove("waterDepth")  
    state.remove("water")   
    state.remove("reportInterval")          
    state.remove("lastPoll")
    state.remove("lastResponse")  
    state.remove("reportAt")  
    state.remove("probe")
    
    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a" 

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