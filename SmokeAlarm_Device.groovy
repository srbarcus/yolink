/***
 *  YoLink™ Smoke Alarm (YS7A12-UC)
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
 *  1.0.0: Initial Release
 */

import groovy.json.JsonSlurper

def clientVersion() {return "1.0.0"}
def copyright() {return "<br>© 2026-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "Smoke Alarm v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"	
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Date Format Template Specifications"), description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink SmokeAlarm Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"				
		capability "Battery"
        capability "TemperatureMeasurement"    
        capability "SmokeDetector"              //smoke - ENUM ["clear", "tested", "detected"]
        capability "SignalStrength"             //rssi 
        capability "FilterStatus"               //filterStatus - ENUM ["normal", "replace"] - Used to indicate Alarm EOL reached
                                  
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]] 
        command "reset"
        command "timestampFormat", [[name:"timestampFormat",type:"STRING", description:"Formatting template for event timestamp values. See Preferences below for details."]] 
       
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"          
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "lastResponse", "String"    
        attribute "reportAt", "String"        
    
        attribute "timeZone", "String"
        attribute "alertInterval", "String"
        attribute "muteDuration", "String" 
        attribute "testDay", "String"
        attribute "testTime", "String"
        attribute "testType", "String"        
        attribute "lowBattery", "String"           
        attribute "highTempAlarm", "String"   
        attribute "silence", "String"   
        attribute "smokeAlarmChanged", "String"
        attribute "testingAlarm", "String"
        attribute "lastTest", "String"
        
        attribute "deviceModel", "String"
        
        /* Have no idea of their meaning
        attribute "unexpected", "String"  
        attribute "unexpectedChanged", "String"
        */                
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
    state.devModel = devModel
    rememberState("devId", devId)   
    	
	log.info "ServiceSetup(Hubitat dni=${state.my_dni}, Home ID=${state.homeID}, Name=${state.name}, Type=${state.type}, Token=${state.token}, Device Id=${state.devId})"
	    
    //reset()  - Not necessary?     
 }

void SetModel(devModel) {	
    rememberState("deviceModel", devModel)   
	log.info "Device model is ${state.devModel})"
 }

public def getSetup() {
    def setup = [:]
        setup.put("my_dni", "${state.my_dni}")                   
        setup.put("homeID", "${state.homeID}") 
        setup.put("name", "${state.name}") 
        setup.put("type", "${state.type}") 
        setup.put("token", "${state.token}") 
        setup.put("devId", "${state.devId}") 
        setup.put("devModel", "${state.devModel}") 
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

    if ((force != null) || (state.lastPoll == null)) {
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
 //pollAPI() response: [code:000000, data:[deviceId:d88b4c01000fd5bf, online:true, reportAt:2026-07-15T00:05:17.459Z, 
 //state:[attributes:[alertInterval:2, muteDuration:600], 
 //battery:4, detectorVersion:163, devTemperature:28, loraInfo:[devNetType:A, netId:010204, subnetId:null], loraP2PHash:0,
 //schedule:[day:0, time:0:0, type:disable], 
 //state:[denseSmokeAlarm:false, detectorEol:false, localInspect:false, remoteInspect:false, sLowBattery:false, silence:false, smokeAlarm:false, unexpected:false], 
 //stateChangedAt:[gasAlarm:null, smokeAlarm:1784073916857, unexpected:1784073916857], tz:0, version:1205]], desc:Success, method:SmokeAlarm.getState, msgid:1784074477519, time:1784074477519]
   
    def online = object.data.online     
    def reportAt = object.data.reportAt     
    
    alertInterval = object.data.state.attributes.alertInterval
    muteDuration = object.data.state.attributes.muteDuration
       
    def battery = parent.batterylevel(object.data.state.battery)
    def detectorVersion = object.data.state.detectorVersion  
    def temperature = object.data.state.devTemperature  
        temperature = parent.convertTemperature(temperature) 
    
    def testType = object.data.state.schedule.type
    def testDay = object.data.state.schedule.day    
    def testTime = object.data.state.schedule.time
    
    if (testType == "disable") {
        logDebug("Alarm test is disabled. Overriding schedule values.")
        testType = "disabled"
        testDay = "none"
        testTime = "none"
    } else {
        if (testType != "monthly") {testDay = parent.scheduledDay(testDay)}  //Convert to Mon-Fri
    	def testHour = testTime.substring(0,testTime.indexOf(":")+1)  
    	def testMin =  testTime.substring(testHour.length(),testTime.length())
    	testTime = testHour + testMin.padLeft(2, "0")        
    }    

    def denseSmokeAlarm = object.data.state.state.denseSmokeAlarm
    def endOfLife = object.data.state.state.detectorEol
    if (!endOfLife) {filterStatus = "normal"} else {filterStatus = "replace"} 
    rememberState("filterStatus",filterStatus)
        
    def localTest = object.data.state.state.localInspect
    def remoteTest = object.data.state.state.remoteInspect
    def lowBattery = object.data.state.state.sLowBattery
    def silence = object.data.state.state.silence
    def smokeAlarm = object.data.state.state.smokeAlarm
    if (!smokeAlarm) {smoke= "clear"} else {smoke= "detected"} //["clear", "tested", "detected"]
       
    def smokeAlarmChanged = object.data.state.stateChangedAt.smokeAlarm
    smokeAlarmChanged = formatTimestamp(smokeAlarmChanged)  
   
    //def unexpected = object.data.state.state.unexpected
    //def unexpectedChanged = formatTimestamp(object.data.state.stateChangedAt.unexpecte)

    def timeZone = object.data.state?.tz
    def firmware = object.data.state.version.toUpperCase()   
                   
    logDebug("Device State: online(${online}), " +
             "Report At(${reportAt}), " +
             "Time Zone(${timeZone}), " +
             "Firmware(${firmware}), " +
             "Battery(${battery}), " + 
             "Temperature(${temperature})," +
             "Alert Interval(${alertInterval})," +
             "Mute Duration(${muteDuration})," +
             "Detector Version(${detectorVersion})," + 
             "Dense Smoke(${denseSmokeAlarm})," + 
             "End-of-Life(${endOfLife})," + 
             "Filter Status(${filterStatus})," + 
             "Local Test(${localTest})," + 
             "Remote Test(${remoteTest})," + 
             "Low Battery Alarm(${lowBattery}), " +
             "Silence Alarm(${silence}), " +
             "Smoke Alarm(${smokeAlarm}), " +
             "Test Day(${testDay}), " +
             "Test Time(${testTime}), " +
             "Test Interval(${testType}), " +
             "Smoke Alarm Changed(${smokeAlarmChanged})") 
             
             //"Unexpected Alarm(${unexpected}), " +
             //"Unexpected Alarm Changed(${unexpectedChanged})")                  
        
    rememberState("online",online)
    rememberState("reportAt",reportAt)       
    rememberState("timeZone",timeZone)
    rememberState("firmware",firmware)
    rememberState("battery", battery, "%")
    rememberState("lowBattery",lowBattery)
    rememberState("temperature", temperature, "°".plus(state.temperatureScale))    
    rememberState("alertInterval",alertInterval)
    rememberState("muteDuration",muteDuration)    
    rememberState("detectorVersion",detectorVersion)
    rememberState("endOfLife",endOfLife)
    rememberState("filterStatus",filterStatus) 
    rememberState("localTest",localTest) 
    rememberState("remoteTest",remoteTest) 
    rememberState("lowBattery",lowBattery) 
    rememberState("silence",silence)
    rememberState("smokeAlarm",smokeAlarm)     
    rememberState("testDay",testDay)    
    rememberState("testTime",testTime)
    rememberState("testType",testType)    
    rememberState("smoke",smoke)    
    rememberState("smokeAlarmChanged",smokeAlarmChanged) 

    //rememberState("unexpected",unexpected)   
    //rememberState("unexpectedChanged",unexpectedChanged) 
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
        case "Alert":         
         //data":{"metadata":{"events":["LocalInspectEvent"],"reminder":false,"detectorError":false,"temperatureRiseAlarm":false},
         //"state":{"smokeAlarm":false,"localInspect":true,"remoteInspect":false,"sLowBattery":false,"unexpected":false,"silence":false,"detectorEol":false,"denseSmokeAlarm":false},
         //"version":"1205","devTemperature":27,"loraInfo":{"netId":"010204","devNetType":"A","signal":-20,"gatewayId":"d88b4c16060000b1","gateways":2},
         //"stateChangedAt":{"smokeAlarm":1784073916857,"unexpected":1784073916857}},"deviceId":"d88b4c01000fd5bf"}   
            
            def smokeAlarm = object.data.state.smokeAlarm
            if (!smokeAlarm) {smoke= "clear"} else {smoke= "detected"} //["clear", "tested", "detected"]
            
            def localTest = object.data.state.localInspect
    		def remoteTest = object.data.state.remoteInspect
    		def lowBattery = object.data.state.sLowBattery            
            def silence = object.data.state.silence
            def endOfLife = object.data.state.detectorEol
            if (!endOfLife) {filterStatus = "normal"} else {filterStatus = "replace"} 
 
            def denseSmokeAlarm = object.data.state.denseSmokeAlarm
       
            def temperature = object.data.devTemperature  
        	temperature = parent.convertTemperature(temperature) 
    
		    def smokeAlarmChanged = formatTimestamp(object.data.stateChangedAt.smokeAlarm)
            
            // def unexpected = object.data.state.unexpected
    		// def unexpectedChanged = formatTimestamp(object.data.stateChangedAt.unexpected)

            def firmware = object.data.version.toUpperCase()  
            
            fmtSignal(object.data.loraInfo.signal)  //Format and remember 'signal' and 'rssi'
                   
   			logDebug("Firmware(${firmware}), " +		
                     "Signal(${state.signal}), " +
		             "RSSI(${state.rssi}), " +
        		     "Temperature(${temperature})," +             
		             "Dense Smoke(${denseSmokeAlarm})," + 
        		     "End-of-Life(${endOfLife})," + 
		             "Filter Status(${filterStatus})," + 
        		     "Local Test(${localTest})," + 
		             "Remote Test(${remoteTest})," + 
		             "Low Battery Alarm(${lowBattery}), " +
		             "Silence Alarm(${silence}), " +
		             "Smoke Alarm(${smokeAlarm}), " +		             
		             "Smoke Alarm Changed(${smokeAlarmChanged})")
                     
                     //"Unexpected Alarm(${unexpected}), " +
		             //"Unexpected Alarm Changed(${unexpectedChanged})")                  
        
		    rememberState("firmware",firmware)
    		rememberState("lowBattery",lowBattery)
    		rememberState("temperature", temperature, "°".plus(state.temperatureScale))    
    		rememberState("endOfLife",endOfLife)
    		rememberState("filterStatus",filterStatus) 
    		rememberState("localTest",localTest) 
    		rememberState("remoteTest",remoteTest) 
    		rememberState("lowBattery",lowBattery) 
    		rememberState("silence",silence)
    		rememberState("smokeAlarm",smokeAlarm) 
		    rememberState("smoke",smoke)
		    rememberState("smokeAlarmChanged",smokeAlarmChanged) 
                     
    		//rememberState("unexpected",unexpected)                      
    		//rememberState("unexpectedChanged",unexpectedChanged)              
     
            if (localTest || remoteTest) {
                def date = new Date()    
                date = date.format(state.timestampFormat)  
                rememberState("lastTest", date)
            }   
            
 		    break;
            
        case "setSchedule":
            //data":{"schedule":{"type":"weekly","day":4,"time":"10:0"},
            //"loraInfo":{"netId":"010204","devNetType":"A","signal":-17,"gatewayId":"d88b4c16060000b1","gateways":1}},"deviceId":"d88b4c01000fd5bf"})":                 
            
            def testType = object.data.schedule.type
            def testDay = parent.scheduledDay(object.data.schedule.day)    
            def testTime = object.data.schedule.time
    
    		if (testType == "disable") {
		        logDebug("Alarm test is disabled. Overriding schedule values.")
		        testType = "disabled"
		        testDay = "none"
		        testTime = "none"
    		} else {
        		if (testType != "monthly") {testDay = parent.scheduledDay(testDay)}  //Convert to Mon-Fri
    			def testHour = testTime.substring(0,testTime.indexOf(":")+1)  
    			def testMin =  testTime.substring(testHour.length(),testTime.length())
    			testTime = testHour + testMin.padLeft(2, "0")        
    		}             
               
            fmtSignal(object.data.loraInfo.signal)  //Format and remember 'signal' and 'rssi'
            
            logDebug("Signal(${state.signal}), " +
		             "RSSI(${state.rssi}), " +
                     "Alarm Test Type:(${testType}), " +
                     "Alarm Test Day(${testDay}), " +   
                     "Alarm Test Time(${testTime})")
            
            rememberState("testType",testType)
            rememberState("testDay",testDay)      //parent.scheduledDays(weekdays)
            rememberState("testTime",testTime)         
            break;             
            
		case "setAttributes":     
            //data":{"attributes":{"alertInterval":2,"muteDuration":510}},"deviceId":"d88b4c01000fd5bf"})
            def alertInterval = object.data.attributes.alertInterval
            def muteDuration = object.data.attributes.muteDuration
            logdebug("Alert Interval(${alertInterval})," +
		             "Mute Duration(${muteDuration})")
            rememberState("alertInterval",alertInterval)
            rememberState("muteDuration",muteDuration)
            break; 
            
        case "StatusChange":
            //data":{"metadata":{"events":["SmokeEvent"]},"state":{"smokeAlarm":false,"localInspect":false,"remoteInspect":false,"sLowBattery":false,"unexpected":false,
            //"silence":false,"detectorEol":false,"denseSmokeAlarm":false},"version":"1205","devTemperature":29,
            //"loraInfo":{"netId":"010204","devNetType":"D","signal":-24,"gatewayId":"d88b4c16060000b1","gateways":2},
            //"stateChangedAt":{"smokeAlarm":1789171965321,"unexpected":1787661814459}},"deviceId":"d88b4c01000fd5bf"})
            
            def smokeAlarm = object.data.state.smokeAlarm
            if (!smokeAlarm) {smoke= "clear"} else {smoke= "detected"} //["clear", "tested", "detected"]
            
            def localTest = object.data.state.localInspect
    		def remoteTest = object.data.state.remoteInspect
    		def lowBattery = object.data.state.sLowBattery
            def silence = object.data.state.silence
            def endOfLife = object.data.state.detectorEol
            if (!endOfLife) {filterStatus = "normal"} else {filterStatus = "replace"} 
 
            def denseSmokeAlarm = object.data.state.denseSmokeAlarm
       
            def temperature = object.data.devTemperature  
        	temperature = parent.convertTemperature(temperature) 
    
		    def smokeAlarmChanged = formatTimestamp(object.data.stateChangedAt.smokeAlarm)
                     
            //def unexpected = object.data.state.unexpected                     
    		//def unexpectedChanged = formatTimestamp(object.data.stateChangedAt.unexpected)

            def firmware = object.data.version.toUpperCase()  
            
            fmtSignal(object.data.loraInfo.signal)  //Format and remember 'signal' and 'rssi'
            
            rememberState("firmware",firmware)
    		rememberState("lowBattery",lowBattery)
    		rememberState("temperature", temperature, "°".plus(state.temperatureScale))    
    		rememberState("endOfLife",endOfLife)
    		rememberState("filterStatus",filterStatus) 
    		rememberState("localTest",localTest) 
    		rememberState("remoteTest",remoteTest) 
    		rememberState("lowBattery",lowBattery) 
    		rememberState("silence",silence)
    		rememberState("smokeAlarm",smokeAlarm)     		
		    rememberState("smoke",smoke)
		    rememberState("smokeAlarmChanged",smokeAlarmChanged) 
                     
            //rememberState("unexpected",unexpected)          
    		//rememberState("unexpectedChanged",unexpectedChanged) 
                   
   			logDebug("Firmware(${firmware}), " +		
                     "Signal(${state.signal}), " +
		             "RSSI(${state.rssi}), " +
        		     "Temperature(${temperature})," +             
		             "Dense Smoke(${denseSmokeAlarm})," + 
        		     "End-of-Life(${endOfLife})," + 
		             "Filter Status(${filterStatus})," + 
        		     "Local Test(${localTest})," + 
		             "Remote Test(${remoteTest})," + 
		             "Low Battery Alarm(${lowBattery}), " +
		             "Silence Alarm(${silence}), " +
		             "Smoke Alarm(${smokeAlarm}), " +
		             "Smoke Alarm Changed(${smokeAlarmChanged})")                     
                     
                     //"Unexpected Alarm(${unexpected}), " +
		             //"Unexpected Alarm Changed(${unexpectedChanged})")                  
     
            if (localTest || remoteTest) {
                def date = new Date()    
                date = date.format(state.timestampFormat)  
                rememberState("lastTest", date)
            }           
            break;   
            
		case "setState":
            //"data":{"mute":{"enable":false}},"deviceId":"d88b4c01000fd5bf"}"}
            def mute = object.data.mute.enable            //No idea the meaning of this value            
            logDebug("Mute Enable(${mute})")         
            break;              

        /*case "Report":
   		    break;  
        */              
            
        /*case "setTimeZone":
            break;   
        */       
                   
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
      return date  
    } else {
      return timestamp  
    }    
}

def reset(){    
      def varlist = "driver,online,rssi,signal,reportAt,timeZone,firmware,battery,temperature,alertInterval,testingAlarm,muteDuration,"
varlist = varlist + "localTest,remoteTest,testDay,testTime,testType,lowBattery,smokeAlarm,smokeAlarmChanged,temperature,alarmInterval,testingAlarm,"
varlist = varlist + "denseSmokeAlarm, highTempAlarm, denseSmoke, lastTest" 
//rlist = varlist + "unexpected,unexpectedChanged"    
   
    removeStates(varlist)
    
    rememberState("driver", clientVersion())
    rememberState("lastTest", "none")
    rememberState("smokeAlarmChanged","never")     		
	rememberState("smokeAlarm","false")
    rememberState("smoke","clear")
    
    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a" 

    poll(true)    
    
    log.warn "Device reset to default values"
}

def removeStates(states) {
   def vars = states.split(',')
   vars.each { var ->
        var = var.trim()
        logDebug("Removing state variable '" + var +"'")
        state.remove(var)      
   }  
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