/***
 *  YoLink™ Sprinkler (YS4102-UC)
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
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied. 
 * 
 *  2.0.0: First Release
 *  2.0.1: Support diagnostics, correct various errors, make singleThreaded
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.1"}

preferences {
    input title: "Driver Version", description: "YoLink™ Sprinkler (YS4102-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink Sprinkler Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"	
        capability "Valve"
        capability "SignalStrength"  //rssi 
        capability "SwitchLevel"     //Level will be used to indicate watering progress of active zone
                                 
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]]
        command "reset"        
        command "AutoResolve", [[name:"AutoResolve",type:"ENUM", description:"Automatically resolve conflicts between heating and cooling settings", constraints:["true", "false"]]]
        command "mode", [[name:"mode",type:"ENUM", description:"Set operating mode", constraints:["auto", "manual", "off"]]]
        command "delay", [[name:"delay",type:"NUMBER", description:"Number of hours to delay before running manual schedule"]]
        
        command "setLevel"  // Not used - override capability functions
        command "stop"
        command "start"
        
        command "zoneSize", [[name:"zoneSize", type:"ENUM", description:"Number of installed zones", constraints:[1, 2, 3, 4, 5, 6]]]
        command "maxWaterTime", [[name:"maxWaterTime",type:"NUMBER", description:"Maximum number of minutes per zone in manual mode (1-100)"]] 
        command "zone1Mins", [[name:"zone1Mins",type:"NUMBER", description:"Number of minutes to run zone 1 in manual mode (1 to maxWaterTime)"]]   
        command "zone2Mins", [[name:"zone2Mins",type:"NUMBER", description:"Number of minutes to run zone 2 in manual mode (1 to maxWaterTime)"]]   
        command "zone3Mins", [[name:"zone3Mins",type:"NUMBER", description:"Number of minutes to run zone 3 in manual mode (1 to maxWaterTime)"]]   
        command "zone4Mins", [[name:"zone4Mins",type:"NUMBER", description:"Number of minutes to run zone 4 in manual mode (1 to maxWaterTime)"]]   
        command "zone5Mins", [[name:"zone5Mins",type:"NUMBER", description:"Number of minutes to run zone 5 in manual mode (1 to maxWaterTime)"]]   
        command "zone6Mins", [[name:"zone6Mins",type:"NUMBER", description:"Number of minutes to run zone 6 in manual mode (1 to maxWaterTime)"]]   
        
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"  
        attribute "lastResponse", "String" 
  
        attribute "zoneSize", "String"
        attribute "mode", "String"
        attribute "delay", "String"
        attribute "activeZone", "String"
        attribute "activeZoneTotal", "String"
        attribute "activeZoneLeft", "String"
        attribute "level", "String"
        attribute "zone1Mins", "String"
        attribute "zone2Mins", "String"
        attribute "zone3Mins", "String"
        attribute "zone4Mins", "String"
        attribute "zone5Mins", "String"
        attribute "zone6Mins", "String"
        attribute "maxWaterTime", "String"        
        attribute "springTime1", "String"
        attribute "summerTime1", "String"
        attribute "autumnTime1", "String"
        attribute "winterTime1", "String"
        attribute "springTime2", "String"
        attribute "summerTime2", "String"
        attribute "autumnTime2", "String"
        attribute "winterTime2", "String"
        attribute "springTime1Text", "String"
        attribute "summerTime1Text", "String"
        attribute "autumnTime1Text", "String"
        attribute "winterTime1Text", "String"
        attribute "springTime2Text", "String"
        attribute "summerTime2Text", "String"
        attribute "autumnTime2Text", "String"
        attribute "winterTime2Text", "String"
        attribute "springMins1", "String"
        attribute "summerMins1", "String"
        attribute "autumnMins1", "String"
        attribute "winterMins1", "String"
        attribute "springMins2", "String"
        attribute "summerMins2", "String"
        attribute "autumnMins2", "String"
        attribute "winterMins2", "String"  
        attribute "springStart", "String"
        attribute "springStartText", "String"
        attribute "springRunDays", "String"
        attribute "summerStart", "String" 
        attribute "summerStartText", "String"
        attribute "summerRunDays", "String"
        attribute "autumnStart", "String"
        attribute "autumnStartText", "String"
        attribute "autumnRunDays", "String"
        attribute "winterStart", "String"
        attribute "winterStartText", "String"
        attribute "winterRunDays", "String"
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
    
    runIn(2, reset)    
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

def temperatureScale(value) {}

def debug(value) { 
   rememberState("debug",value)
   if (value) {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

def setLevel() { 
   log.error "'Set Level' is unsupported. 'level' attribute is used to indicate active zone watering progress."
}

 def zone1Mins(value) {
     rememberState("zone1Mins", value)
     setSprinkler(null,null,null,"true")
 }

 def zone2Mins(value) {
     rememberState("zone2Mins", value)
     setSprinkler(null,null,null,"true")
}

 def zone3Mins(value) {
     rememberState("zone3Mins", value)
     setSprinkler(null,null,null,"true")
 }

 def zone4Mins(value) {
     rememberState("zone4Mins", value)
     setSprinkler(null,null,null,"true")
}

 def zone5Mins(value) {
     rememberState("zone5Mins", value)
     setSprinkler(null,null,null,"true")
}

 def zone6Mins(value) {
     rememberState("zone6Mins", value)
     setSprinkler(null,null,null,"true")
}

def start() {open()}
def stop()  {close()}

def open()  {
    setSprinkler(null,null,null,"true")  //mode=null, zoneSize=null, delay=null, manualWater=null, maxWaterTime=null)
    water("start")
}

def close() {
    water("stop")
}

def mode(value){
  setSprinkler(value)  //mode=null, zoneSize=null, delay=null, manualWater=null, maxWaterTime=null)
}

def zoneSize(value){
  setSprinkler(null,value)  //mode=null, zoneSize=null, delay=null, manualWater=null, maxWaterTime=null)
}

def delay(value){
  setSprinkler(null,null,value * 60)  //mode=null, zoneSize=null, delay=null, manualWater=null, maxWaterTime=null)
}

def manualWater(value){
  setSprinkler(null,null,null,value)  //mode=null, zoneSize=null, delay=null, manualWater=null, maxWaterTime=null)
}

def maxWaterTime(value){
  setSprinkler(null,null,null,null,value)  //mode=null, zoneSize=null, delay=null, manualWater=null, maxWaterTime=null)
}

def setSprinkler(mode=null, zoneSize=null, delay=null, manualWater=null, maxWaterTime=null) {  
   def params = [:] 
   def vals = [:] 
   def zones = [] 
             
   if (mode != null) {
       vals.put("mode", mode)
       params.put("state", vals)
   }
    
   if (zoneSize != null) {
       vals.put("zoneSize", zoneSize)
       params.put("state", vals)
   }
     
   if (delay != null) {
       vals.put("delay", delay)
       params.put("state", vals)
   }  
    
       
   if (manualWater != null) {
       def err = ") exceeds maximum watering time (${maxTime}). Time forced to maximum watering time."
       def z1min = state.zone1Mins
       def z2min = state.zone2Mins
       def z3min = state.zone3Mins
       def z4min = state.zone4Mins
       def z5min = state.zone5Mins
       def z6min = state.zone6Mins
       def maxTime = state.maxWaterTime
       
       logDebug("manualWater: Max time ${maxTime}: ${z1min}, ${z2min}, ${z3min}, ${z4min}, ${z5min}, ${z6min}") 
       
       if (z1min > maxTime) {log.warn "Zone 1 minutes (${z1min}" + err}
       if (z2min > maxTime) {log.warn "Zone 2 minutes (${z2min}" + err}
       if (z3min > maxTime) {log.warn "Zone 3 minutes (${z3min}" + err}
       if (z4min > maxTime) {log.warn "Zone 4 minutes (${z4min}" + err}
       if (z5min > maxTime) {log.warn "Zone 5 minutes (${z5min}" + err}
       if (z6min > maxTime) {log.warn "Zone 6 minutes (${z6min}" + err}
    
       zones[0] = z1min
       zones[1] = z2min
       zones[2] = z3min
       zones[3] = z4min
       zones[4] = z5min
       zones[5] = z6min
       logDebug("Setting manual waterinf zones times: ${zones}")  
       
       vals.put("manualWater", zones)       
       params.put("setting", vals)
   } 
   
   if (maxWaterTime != null) {
       rememberState("maxWaterTime",maxWaterTime)
       vals.put("maxWaterTime", maxWaterTime)
       params.put("setting", vals)
   } 

   def request = [:] 
   request.put("method", "${state.type}.setState")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
        def swState
         
        if (object) {
            logDebug("setSprinkler(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {               
                lastResponse("Success")
                parseDevice(object) 
                runIn(2, getDevicestate)
             } else {                
                if (notConnected(object)) { //Cannot connect to Device - YoLink bug? Device appears offline while state is changing
                   lastResponse("Cannot connect to Device")      
                } else {
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                }
            }                     
                
	    } else { 			               
            logDebug("setSprinkler() failed")	 
            lastResponse("setSprinkler() failed")     
        }     		
	} catch (e) {	
        log.error "setSprinkler() exception: $e"
        lastResponse("Error ${e}")             
	} 
}  

def getSchedules(){
   def request = [:] 
   request.put("method", "Sprinkler.getSchedules")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
    
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
        def swState
         
        if (object) {
            logDebug("getSchedules(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {               
                lastResponse("Success")
                parseSchedules(object)                
            } else {                
                if (notConnected(object)) { //Cannot connect to Device - YoLink bug? Device appears offline while state is changing
                   lastResponse("Cannot connect to Device")      
                } else {
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                }
            }                     
                
	    } else { 			               
            logDebug("getSchedules() failed")	
            lastResponse("getSchedules() failed")     
        }     		
	} catch (e) {	
        log.error "getSchedules() exception: $e"
        lastResponse("Error ${e}")     
	} 
}  

def parseSchedules(object) {
   def spring = object.data.sches[0]
   def summer = object.data.sches[1]
   def autumn = object.data.sches[2]
   def winter = object.data.sches[3]

   logDebug("Parsed: Winter=$winter")
   logDebug("Parsed: Autumn=$autumn")
   logDebug("Parsed: Summer=$summer")
   logDebug("Parsed: Spring=$spring")

   def springStart1 = spring.plans[0].time
   def summerStart1 = summer.plans[0].time
   def autumnStart1 = autumn.plans[0].time
   def winterStart1 = winter.plans[0].time
        
   def springStart2 = spring.plans[1].time
   def summerStart2 = summer.plans[1].time
   def autumnStart2 = autumn.plans[1].time
   def winterStart2 = winter.plans[1].time
        
   springStart1 = Date.parse('H:m:s', springStart1).format('HH:mm')
   summerStart1 = Date.parse('H:m:s', summerStart1).format('HH:mm')
   autumnStart1 = Date.parse('H:m:s', autumnStart1).format('HH:mm')
   winterStart1 = Date.parse('H:m:s', winterStart1).format('HH:mm')
        
   springStart2 = Date.parse('H:m:s', springStart2).format('HH:mm')
   summerStart2 = Date.parse('H:m:s', summerStart2).format('HH:mm')
   autumnStart2 = Date.parse('H:m:s', autumnStart2).format('HH:mm')
   winterStart2 = Date.parse('H:m:s', winterStart2).format('HH:mm')
        
   def springStart1Text = Date.parse('H:m', springStart1).format('h:mm a')
   def summerStart1Text = Date.parse('H:m', summerStart1).format('h:mm a')
   def autumnStart1Text = Date.parse('H:m', autumnStart1).format('h:mm a')
   def winterStart1Text = Date.parse('H:m', winterStart1).format('h:mm a')
        
   def springStart2Text = Date.parse('H:m', springStart2).format('h:mm a')
   def summerStart2Text = Date.parse('H:m', summerStart2).format('h:mm a')
   def autumnStart2Text = Date.parse('H:m', autumnStart2).format('h:mm a')
   def winterStart2Text = Date.parse('H:m', winterStart2).format('h:mm a') 
        
   def springMins1 = spring.plans[0].zones
   def summerMins1 = summer.plans[0].zones
   def autumnMins1 = autumn.plans[0].zones
   def winterMins1 = winter.plans[0].zones
      
   def springMins2 = spring.plans[1].zones
   def summerMins2 = summer.plans[1].zones
   def autumnMins2 = autumn.plans[1].zones
   def winterMins2 = winter.plans[1].zones

   logDebug("Plan 1 Start time: Spring ${springStart1} (${springStart1Text}), Summer ${summerStart1} (${summerStart1Text}), Autumn ${autumnStart1} (${autumnStart1Text}), Winter ${winterStart1} (${winterStart1Text})") 
   logDebug("Plan 1 Minstime (mins): Spring ${springMins1}, Summer ${summerMins1}, Autumn ${autumnMins1}, Winter ${winterMins1}") 
   logDebug("Plan 2 Start time: Spring ${springStart2} (${springStart2Text}), Summer ${summerStart2} (${summerStart2Text}), Autumn ${autumnStart2} (${autumnStart2Text}), Winter ${winterStart2} (${winterStart2Text})")  
   logDebug("Plan 2 Minstime (mins): Spring ${springMins2}, Summer ${summerMins2}, Autumn ${autumnMins2}, Winter ${winterMins2}")
        
   def month
   def day
    
   month           = Date.parse('M-d', spring.date).format('M').toInteger().plus(1)
   day             = Date.parse('M-d', spring.date).format('d')     
   def springStart     = Date.parse('M-d', month + "-" + day).format('M/d') 
   def springStartText = Date.parse('M-d', month + "-" + day).format('MMMM d')  
   def springRunDays = parent.scheduledDays(spring.weekmask)
   logDebug("Spring: Start Date ${springStart}  (${springStartText}), Days=${springRunDays}")

   month           = Date.parse('M-d', summer.date).format('M').toInteger().plus(1)
   day             = Date.parse('M-d', summer.date).format('d')     
   def summerStart     = Date.parse('M-d', month + "-" + day).format('M/d') 
   def summerStartText = Date.parse('M-d', month + "-" + day).format('MMMM d')  
   def summerRunDays = parent.scheduledDays(summer.weekmask)
   logDebug("Summer: Start Date ${summerStart}  (${summerStartText}), Days=${summerRunDays}")

   month           = Date.parse('M-d', autumn.date).format('M').toInteger().plus(1)
   day             = Date.parse('M-d', autumn.date).format('d')     
   def autumnStart     = Date.parse('M-d', month + "-" + day).format('M/d') 
   def autumnStartText = Date.parse('M-d', month + "-" + day).format('MMMM d')  
   def autumnRunDays = parent.scheduledDays(autumn.weekmask)
   logDebug("Autumn: Start Date ${autumnStart}  (${autumnStartText}), Days=${autumnRunDays}")

   month           = Date.parse('M-d', winter.date).format('M').toInteger().plus(1)
   day             = Date.parse('M-d', winter.date).format('d')     
   def winterStart     = Date.parse('M-d', month + "-" + day).format('M/d') 
   def winterStartText = Date.parse('M-d', month + "-" + day).format('MMMM d')  
   def winterRunDays = parent.scheduledDays(winter.weekmask)
   logDebug("Winter: Start Date ${winterStart}  (${winterStartText}), Days=${winterRunDays}")                    
   
   rememberState("springStart1",springStart1)
   rememberState("summerStart1",summerStart1)
   rememberState("autumnStart1",autumnStart1)
   rememberState("winterStart1",winterStart1)
   rememberState("springStart2",springStart2)
   rememberState("summerStart2",summerStart2)
   rememberState("autumnStart2",autumnStart2)
   rememberState("winterStart2",winterStart2)
   rememberState("springStart1Text",springStart1Text)
   rememberState("summerStart1Text",summerStart1Text)
   rememberState("autumnStart1Text",autumnStart1Text)
   rememberState("winterStart1Text",winterStart1Text)
   rememberState("springStart2Text",springStart2Text)
   rememberState("summerStart2Text",summerStart2Text)
   rememberState("autumnStart2Text",autumnStart2Text)
   rememberState("winterStart2Text",winterStart2Text)
   rememberState("springMins1",springMins1)
   rememberState("summerMins1",summerMins1)
   rememberState("autumnMins1",autumnMins1)
   rememberState("winterMins1",winterMins1)
   rememberState("springMins2",springMins2)
   rememberState("summerMins2",summerMins2)
   rememberState("autumnMins2",autumnMins2)
   rememberState("winterMins2",winterMins2)  
   rememberState("springStart",springStart)
   rememberState("springStartText",springStartText)
   rememberState("springRunDays",springRunDays)
   rememberState("summerStart",summerStart) 
   rememberState("summerStartText",summerStartText)
   rememberState("summerRunDays",summerRunDays)
   rememberState("autumnStart",autumnStart)
   rememberState("autumnStartText",autumnStartText)
   rememberState("autumnRunDays",autumnRunDays)
   rememberState("winterStart",winterStart)
   rememberState("winterStartText",winterStartText)
   rememberState("winterRunDays",winterRunDays)
}   

def water(value){
   def params = [:] 
   
   params.put("state", value)    
     
   def request = [:] 
   request.put("method", "Sprinkler.setManualWater")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)             
    
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("water(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {  
                if (value == "stop") {
                   rememberState("activezone", 0)
                   rememberState("activezonetotal", 0)
                   rememberState("activezoneleft", 0)
                   rememberState("level", 0)
                }
                
                lastResponse("Success")                
            } else {                
                if (notConnected(object)) { //Cannot connect to Device - YoLink bug? Device appears offline while state is changing
                   lastResponse("Cannot connect to Device")      
                } else {
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                }
            }                     
                
	    } else { 			               
            logDebug("water() failed")	
            lastResponse("water() failed")     
        }     		
	} catch (e) {	
        log.error "water() exception: $e"
        lastResponse("Error ${e}")     
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
                rememberState("online", "true") 
                lastResponse("Success") 
            } else {  //Error
               if (pollError(object) ) {  //Cannot connect to Device
                 rememberState("online", "false") 
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
   logDebug("parseDevice(${object})") 
   try {   
       def mode = object.data.state.mode
       def zoneSize = object.data.state.zoneSize
       def activezone
       def activezonetotal
       def activezoneleft
       def level
       
       def delay = object.data.state.delay
       
       activezone = object.data.state.watering.zone
       logDebug("Activezone=$activezone")
       if (activezone != null) {
           activezone = activezone + 1                           //Index of current watering zone
           activezonetotal = object.data.state.watering.total    //Time (minutes) of current watering
           activezoneleft = object.data.state.watering.left      //Left time (minutes) of current watering
     
           if (activezoneleft < activezonetotal) {activezoneleft = activezoneleft + 1}
           def completed = activezonetotal - activezoneleft 
       
           if (completed == 0) {
               level = 0
           } else {
               level = ((completed/activezonetotal) * 100).toDouble().round(0).toInteger()
           }    
       }    
       
       def maxWaterTime = object.data.setting.maxWaterTime   //Maximum watering time
       def zone1Mins = object.data.setting.manualWater[0]    //Water Time for Manual Mode
       def zone2Mins = object.data.setting.manualWater[1]
       def zone3Mins = object.data.setting.manualWater[2]
       def zone4Mins = object.data.setting.manualWater[3]
       def zone5Mins = object.data.setting.manualWater[4]
       def zone6Mins = object.data.setting.manualWater[5]
              
       if (zoneSize < 2) {zone2Mins = 0}
       if (zoneSize < 3) {zone3Mins = 0}
       if (zoneSize < 4) {zone4Mins = 0}
       if (zoneSize < 5) {zone5Mins = 0}
       if (zoneSize < 6) {zone6Mins = 0}
       
       def firmware = object.data.version.toUpperCase()
   
       def tzone = object.data.tz
       def rssi = object.data.loraInfo.signal            
                            
       rememberState("online", "true")
       rememberState("mode", mode)
       rememberState("delay", delay)
       rememberState("zoneSize", zoneSize)
       rememberState("maxWaterTime", maxWaterTime)
       rememberState("zone1Mins",zone1Mins)
       rememberState("zone2Mins",zone2Mins)
       rememberState("zone3Mins",zone3Mins)
       rememberState("zone4Mins",zone4Mins)
       rememberState("zone5Mins",zone5Mins)
       rememberState("zone6Mins",zone6Mins) 
       
       rememberState("firmware", firmware)
       rememberState("rssi", rssi) 
       
       if (activezone != null) {
           rememberState("activezone", activezone)
           rememberState("activezonetotal", activezonetotal)
           rememberState("activezoneleft", activezoneleft)
           rememberState("level", level)
           logDebug("Parsed: Mode=$mode, zoneSize=$zoneSize, delay=$delay, activezone=$activezone, activezonetotal=$activezonetotal, activezoneleft=$activezoneleft, activezonecomplete=$level%, maxWaterTime=$maxWaterTime, zone1Mins=$zone1Mins, zone2Mins=$zone2Mins, zone3Mins=$zone3Mins, zone4Mins=$zone4Mins, zone5Mins=$zone5Mins, zone6Mins=$zone6Mins, Firmware=$firmware, rssi=$rssi") 
       } else {    
           logDebug("Parsed: Mode=$mode, zoneSize=$zoneSize, delay=$delay, maxWaterTime=$maxWaterTime, zone1Mins=$zone1Mins, zone2Mins=$zone2Mins, zone3Mins=$zone3Mins, zone4Mins=$zone4Mins, zone5Mins=$zone5Mins, zone6Mins=$zone6Mins, Firmware=$firmware, rssi=$rssi") 
       }                                
      
   } catch (groovyx.net.http.HttpResponseException e) {	
        lastResponse("parseDevice() Exception - $e")                
        log.error "parseDevice() Exception - $e"

   }       
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
            case "getSchedules":           
            case "setManualWater":                           
		    	break;	
         
            case "Report":    
            case "StatusChange":           
            case "setState":               
            case "getState":   //data:[state:[mode:manual, zoneSize:6, delay:0, watering:[zone:0, total:10, left:8]], setting:[maxWaterTime:60, manualWater:[10, 20, 30, 40, 50, 60]], version:020a, time:2022-10-11T07:30:42.000Z, tz:-5, loraInfo:[signal:-13    
                parseDevice(object)
	    		break;	
      
            case "waterReport":             
                def evnt = object.data.event
            
                logDebug("Water event: ${evnt}")
            
                if (evnt == "start") {           
                  sendEvent(name:"valve", value: "open", isStateChange:true)                    
                } else {
                    if ((evnt == "stop") ||  (evnt == "end")) {    
                      sendEvent(name:"valve", value: "closed", isStateChange:true)                        
                    } else {
                      log.error "Undefined watering event received: $evnt"  
                      sendEvent(name:"valve", value: "unknown", isStateChange:true)   
                    }    
                }    
      			break;	  
 
            case "setSchedules":   
                parseSchedules(object)
      			break;	  
              
            case "setTimeZone":               
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
    state.remove("rssi") 
    state.remove("LastResponse")  
    state.remove("schedules") 
    
    state.remove("mode")
    state.remove("zoneSize")
    state.remove("delay")
    state.remove("activezone")
    state.remove("activezonetotal")
    state.remove("activezoneleft")
    state.remove("level")
    
    state.remove("zone1Mins")
    state.remove("zone2Mins")
    state.remove("zone3Mins")
    state.remove("zone4Mins")
    state.remove("zone5Mins")
    state.remove("zone6Mins")

    state.remove("springStart1")
    state.remove("summerStart1")
    state.remove("autumnStart1")
    state.remove("winterStart1")
    state.remove("springStart2")
    state.remove("summerStart2")
    state.remove("autumnStart2")
    state.remove("winterStart2")
    state.remove("springStart1Text")
    state.remove("summerStart1Text")
    state.remove("autumnStart1Text")
    state.remove("winterStart1Text")
    state.remove("springStart2Text")
    state.remove("summerStart2Text")
    state.remove("autumnStart2Text")
    state.remove("winterStart2Text")
    state.remove("springRun1")
    state.remove("summerRun1")
    state.remove("autumnRun1")
    state.remove("winterRun1")
    state.remove("springRun2")
    state.remove("summerRun2")
    state.remove("autumnRun2")
    state.remove("winterRun2")
    state.remove("springStart")
    state.remove("springStartText")
    state.remove("springRunDays")
    state.remove("summerStart")
    state.remove("summerStartText")
    state.remove("summerRunDays")
    state.remove("autumnStart")
    state.remove("autumnStartText")
    state.remove("autumnRunDays")
    state.remove("winterStart")
    state.remove("winterStartText")
    state.remove("winterRunDays")
      
    water("stop")
    sendEvent(name:"valve", value: "closed", isStateChange:true) 
    
    poll(true)    
       
    runIn(2, getSchedules)
   
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