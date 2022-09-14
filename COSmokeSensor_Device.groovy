/***
 *  YoLink™ Smoke & CO Alarm (YS7A01-UC)
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
 *  1.0.1: Fixed switch errors in processStateData(). Fixed errors in poll()
 *  1.0.2: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *         - Removed superfluous Round code
 *  1.0.3: Fix syncing of Temperature scale with YoLink™ Device Service app
 *  1.0.4: Fix 'Unknown event received: StatusChange' error
 *  1.1.0: - Fix donation URL 
 *         - New Function: Formats event timestamps according to user specifiable format
 *         - Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions 
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.0"}

preferences {
    input title: "Driver Version", description: "Smoke & CO Alarm (YS7A01-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"	
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Date Format Template Specifications", description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink COSmokeSensor Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"				
		capability "Battery"
        capability "TemperatureMeasurement"    
        capability "CarbonMonoxideDetector" //carbonMonoxide - ENUM ["clear", "tested", "detected"]
        capability "SmokeDetector"          //smoke - ENUM ["clear", "tested", "detected"]
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]] 
        command "reset"  
        command "timestampFormat", [[name:"timestampFormat",type:"STRING", description:"Formatting template for event timestamp values. See Preferences below for details."]] 
       
        attribute "online", "String"
        attribute "firmware", "String"          
        attribute "signal", "String" 
        attribute "lastResponse", "String"    
        attribute "reportAt", "String"
                        
        attribute "timeZone", "String"
        attribute "alarmInterval", "String"  
        attribute "testingAlarm", "String"  
        attribute "alarmTestType", "String"
        attribute "alarmTestDay", "String"
        attribute "alarmTestTime", "String"   
        attribute "unexpected", "String"   
        attribute "lowBattery", "String"   
        attribute "smokeAlarm", "String"   
        attribute "gasAlarm", "String"   
        attribute "highTempAlarm", "String"   
        attribute "silence", "String"   
        attribute "gasAlarmChanged", "String" 
        attribute "smokeAlarmChanged", "String" 
        attribute "unexpectedChanged", "String" 
        attribute "lastTest", "String"
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
       //log.error "dateFormat() exception: ${e}"
       log.error "Requested date format, '${value}', is invalid. Format remains '${oldvalue}'" 
     } 
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
    def timeZone = object.data.state?.tz
    def firmware = object.data.state.version   
    
    def battery = parent.batterylevel(object.data.state.battery) 
    def temperature = object.data.state.devTemperature  
        temperature = parent.convertTemperature(temperature)   
    def alarmInterval = object.data.state.interval
    
    def testingAlarm = object.data.state.metadata.inspect
    def alarmTestType = object.data.state.sche.type
    def alarmTestDay = object.data.state.sche.day
    if (alarmTestType != "monthly") {alarmTestDay = parent.scheduledDay(alarmTestDay)} 
    def alarmTestTime = object.data.state.sche.time
    
    def unexpected = object.data.state.state.unexpected
    def lowBattery = object.data.state.state.sLowBattery
    def smokeAlarm = object.data.state.state.smokeAlarm
    def gasAlarm = object.data.state.state.gasAlarm
    def highTempAlarm = object.data.state.state.highTempAlarm      
    def silence = object.data.state.state.silence       

    def gasAlarmChanged = object.data.state.stateChangedAt.gasAlarm
    def smokeAlarmChanged = object.data.state.stateChangedAt.smokeAlarm
    def unexpectedChanged = object.data.state.stateChangedAt.unexpected    
  
    gasAlarmChanged = formatTimestamp(gasAlarmChanged)    
    smokeAlarmChanged = formatTimestamp(smokeAlarmChanged)    
    unexpectedChanged = formatTimestamp(unexpectedChanged)               
                   
    logDebug("Device State: online(${online}), " +
             "Report At(${reportAt}), " +
             "Time Zone(${timeZone}), " +
             "Firmware(${firmware}), " +
             "Battery(${battery}), " + 
             "Temperature(${temperature})," +
             "Alarm Interval(${alarmInterval})," +
             "Testing Alarm(${testingAlarm}), " +
             "Alarm Test Type:(${alarmTestType}), " +
             "Alarm Test Day(${alarmTestDay}), " +   
             "Alarm Test Time(${alarmTestTime}), " +
             "Unexpected Alarm(${unexpected}), " +
             "Low Battery Alarm(${lowBattery}), " +
             "Smoke Alarm(${smokeAlarm}), " +
             "Gas Alarm(${gasAlarm}), " +
             "High Temperature Alarm(${highTempAlarm}), " +
             "Silence Alarm(${silence}), " +
             "Gas Alarm Changed(${gasAlarmChanged}), " +
             "Smoke Alarm Changed(${smokeAlarmChanged}), " +
             "Unexpected Alarm Changed(${unexpectedChanged})")                  
        
    rememberState("online",online)
    rememberState("reportAt",reportAt)       
    rememberState("timeZone",timeZone)
    rememberState("firmware",firmware)
    rememberState("battery",battery)    
    rememberState("temperature",temperature)
    rememberState("alarmInterval",alarmInterval)
    rememberState("testingAlarm",testingAlarm)
    rememberState("alarmTestType",alarmTestType)
    rememberState("alarmTestDay",alarmTestDay)    
    rememberState("alarmTestTime",alarmTestTime)
    rememberState("unexpected",unexpected)
    rememberState("lowBattery",lowBattery)
    rememberState("smokeAlarm",smokeAlarm)             
    rememberState("gasAlarm",gasAlarm)
    rememberState("highTempAlarm",highTempAlarm)
    rememberState("silence",silence)            
    rememberState("gasAlarmChanged",gasAlarmChanged) 
    rememberState("smokeAlarmChanged",smokeAlarmChanged) 
    rememberState("unexpectedChanged",unexpectedChanged)              
    
    def carbonMonoxide
    def smoke
    
    if (gasAlarm == "true") {carbonMonoxide = "detected"} else {carbonMonoxide = "clear"}    
    if (smokeAlarm == "true") {smoke = "detected"} else {smoke = "clear" }    
    
    rememberState("carbonMonoxide",carbonMonoxide)              
    rememberState("smoke",smoke)                  
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
		case "Report": case "StatusChange":           
           def unexpected = object.data.state.unexpected
           def lowBattery = object.data.state.sLowBattery
           def smokeAlarm = object.data.state.smokeAlarm
           def gasAlarm = object.data.state.gasAlarm
           def highTempAlarm = object.data.state.highTempAlarm      
           def silence = object.data.state.silence    
           def testingAlarm = object.data.metadata.inspect 
           def lastInspection = object.data.lastInspection?.time
           def battery = parent.batterylevel(object.data.battery)  
           def alarmInterval = object.data.interval 
           def firmware = object.data.version   
           def temperature = object.data.devTemperature  
               temperature = parent.convertTemperature(temperature)   
           def timeZone = object.data.tz
                     
           def alarmTestType = object.data.sche.type
           def alarmTestDay = object.data.sche.day
           if (alarmTestType != "monthly") {alarmTestDay = parent.scheduledDay(alarmTestDay)} 
               
           def alarmTestTime = object.data.sche.time  
              
           def signal = object.data.loraInfo.signal  
           def gasAlarmChanged = object.data.stateChangedAt.gasAlarm
           def smokeAlarmChanged = object.data.stateChangedAt.smokeAlarm
           def unexpectedChanged = object.data.stateChangedAt.unexpected          
  
           lastInspection = formatTimestamp(lastInspection)     
           gasAlarmChanged = formatTimestamp(gasAlarmChanged)    
           smokeAlarmChanged = formatTimestamp(smokeAlarmChanged)    
           unexpectedChanged = formatTimestamp(unexpectedChanged)            
                    
           logDebug("Time Zone(${timeZone}), " +
                 "Firmware(${firmware}), " +
                 "Battery(${battery}), " + 
                 "Signal(${signal}), " +  
                 "Temperature(${temperature})," +
                 "Alarm Interval(${alarmInterval})," +
                 "Testing Alarm(${testingAlarm}), " +
                 "Last Test(${lastInspection}), " +   
                 "Alarm Test Type:(${alarmTestType}), " +
                 "Alarm Test Day(${alarmTestDay}), " +   
                 "Alarm Test Time(${alarmTestTime}), " +
                 "Unexpected Alarm(${unexpected}), " +
                 "Low Battery Alarm(${lowBattery}), " +
                 "Smoke Alarm(${smokeAlarm}), " +
                 "Gas Alarm(${gasAlarm}), " +
                 "High Temperature Alarm(${highTempAlarm}), " +
                 "Silence Alarm(${silence}), " +
                 "Gas Alarm Changed(${gasAlarmChanged}), " +
                 "Smoke Alarm Changed(${smokeAlarmChanged}), " +
                 "Unexpected Alarm Changed(${unexpectedChanged})")     
     
            rememberState("timeZone",timeZone)
            rememberState("firmware",firmware)
            rememberState("battery",battery) 
            rememberState("signal",signal) 
            rememberState("temperature",temperature)
            rememberState("alarmInterval",alarmInterval)
            rememberState("testingAlarm",testingAlarm)
            rememberState("lastTest",lastInspection)
            rememberState("alarmTestType",alarmTestType)
            rememberState("alarmTestDay",alarmTestDay)
            rememberState("alarmTestTime",alarmTestTime)
            rememberState("unexpected",unexpected)
            rememberState("lowBattery",lowBattery)
            rememberState("smokeAlarm",smokeAlarm)             
            rememberState("gasAlarm",gasAlarm)
            rememberState("highTempAlarm",highTempAlarm)
            rememberState("silence",silence)            
            rememberState("gasAlarmChanged",gasAlarmChanged) 
            rememberState("smokeAlarmChanged",smokeAlarmChanged) 
            rememberState("unexpectedChanged",unexpectedChanged)              
    
            def carbonMonoxide
            def smoke
           
            if (gasAlarm == "true") {carbonMonoxide = "detected"} else {carbonMonoxide = "clear"}    
            if (smokeAlarm == "true") {smoke = "detected"} else {smoke = "clear" }    
    
            rememberState("carbonMonoxide",carbonMonoxide)              
            rememberState("smoke",smoke)  
 		    break;   
            
		case "setInterval":     
            def alertInterval = object.data.alertInterval 
            def signal = object.data.loraInfo.signal 
            rememberState("alertInterval",alertInterval)
            rememberState("signal",signal)
            break; 
            
	    case "setSchedule":       
            def alarmTestType = object.data.type
            def alarmTestDay = object.data.day
            if (alarmTestType != "monthly") {alarmTestDay = parent.scheduledDay(alarmTestDay)} 
            def alarmTestTime = object.data.time
    
            logDebug("Alarm Test Type:(${alarmTestType}), " +
                     "Alarm Test Day(${alarmTestDay}), " +   
                     "Alarm Test Time(${alarmTestTime})")
            
            rememberState("alarmTestType",alarmTestType)
            rememberState("alarmTestDay",alarmTestDay)      //parent.scheduledDays(weekdays)
            rememberState("alarmTestTime",alarmTestTime)
            break;   
            
        case "setState":            
            break; 
           
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }				
    }
}

def formatTimestamp(timestamp){    
    if ((state.timestampFormat != null) && (timestamp != null)) {
      def date = new Date( timestamp as long )    
      date = date.format(state.timestampFormat)
      logDebug("formatTimestamp(): '$state.timestampFormat' = '$date'")
      return date  
    } else {
      return timestamp  
    }    
}

def reset(){       
    state.debug = false  
    state.remove("online")
    state.remove("reportAt")
    state.remove("timeZone")
    state.remove("firmware")
    state.remove("battery") 
    state.remove("temperature")
    state.remove("alarmInterval")
    state.remove("testingAlarm")
    state.remove("alarmTestType")
    state.remove("alarmTestDay")
    state.remove("alarmTestTime")
    state.remove("unexpected")
    state.remove("lowBattery")
    state.remove("smokeAlarm")
    state.remove("gasAlarm")
    state.remove("highTempAlarm")
    state.remove("silence")
    state.remove("gasAlarmChanged")
    state.remove("smokeAlarmChanged")
    state.remove("unexpectedChanged")
    state.remove("carbonMonoxide")
    state.remove("smoke")
      
    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a" 

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
