/***
 *  YoLink™ MultiOutlet (YS6801-UC)
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
 *  1.0.1: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *  1.0.2: Set switch state to "unknown" if unable to connect to device and remove "error" message in log.
 *  1.0.3: Added "Switch" capability
 *  1.0.4: def temperatureScale()
 *  1.0.5: Fix donation URL
 *  1.0.6: Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions 
 *  2.1.0: Add child devices for each outlet and USB ports
 *  2.1.1: Add preference "AllowMixed" to allow 'Mixed' state if some outlets are on and some are off (original behavior). Default is "false": If one or more outlets "on", then switch = "on", else switch = "off"  
 *  2.1.2: Support diagnostics, correct various errors, make singleThreaded
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.1.2"}

preferences {
    input title: "Allow 'Mixed' switch state if some outlets are on and some are off", name: "AllowMixed", type: "bool", required: true, defaultValue: "false" 
    input title: "Driver Version", description: "YoLink™ MultiOutlet (YS6801-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink MultiOutlet Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"	
        capability "Outlet"
        capability "Switch"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:[true, false]]] 
        command "reset" 
        
        command "usbPorts",[[name:"On or Off", type:"ENUM", description:"Desired state of USB ports", constraints:["on", "off"]]]
        command "outlet1", [[name:"On or Off", type:"ENUM", description:"Desired state of outlet 1", constraints:["on", "off"]]]
        command "outlet2", [[name:"On or Off", type:"ENUM", description:"Desired state of outlet 2", constraints:["on", "off"]]]
        command "outlet3", [[name:"On or Off", type:"ENUM", description:"Desired state of outlet 3", constraints:["on", "off"]]]
        command "outlet4", [[name:"On or Off", type:"ENUM", description:"Desired state of outlet 4", constraints:["on", "off"]]]    
        
        command "outlet1TimerOn", ["integer"]  
        command "outlet1TimerOff", ["integer"]  
        command "outlet2TimerOn", ["integer"]   
        command "outlet2TimerOff", ["integer"]  
        command "outlet3TimerOn", ["integer"]   
        command "outlet3TimerOff", ["integer"]  
        command "outlet4TimerOn", ["integer"]   
        command "outlet4TimerOff", ["integer"]  
        
        command "outlet1Delays", ["String"]    
        command "outlet2Delays", ["String"]   
        command "outlet3Delays", ["String"]   
        command "outlet4Delays", ["String"]                   
        
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"
        attribute "firmware", "String"  
        attribute "signal", "String"
        attribute "lastResponse", "String"         

        attribute "delay_on", "String"  
        attribute "delay_off", "String"  
        attribute "time", "String"
        attribute "tzone", "String"        
        attribute "powerOnState", "String"  
        attribute "schedules", "integer"
        attribute "schedule1", "String"
        attribute "schedule2", "String"
        attribute "schedule3", "String"
        attribute "schedule4", "String"
        attribute "schedule5", "String"
        attribute "schedule6", "String"
        attribute "schedule7", "String"
        attribute "schedule8", "String"
        attribute "schedule9", "String"
        
        attribute "USBports", "String"    
        attribute "outlet1", "String" 
        attribute "outlet2", "String" 
        attribute "outlet3", "String" 
        attribute "outlet4", "String" 

        
        attribute "timer1on", "integer"
        attribute "timer1off", "integer"
        attribute "timer2on", "integer"
        attribute "timer2off", "integer"
        attribute "timer3on", "integer"
        attribute "timer3off", "integer"
        attribute "timer4on", "integer"
        attribute "timer4off", "integer"
        
        attribute "timer1onHM", "String"
        attribute "timer1offHM", "String"
        attribute "timer2onHM", "String"
        attribute "timer2offHM", "String" 
        attribute "timer3onHM", "String"
        attribute "timer3offHM", "String"
        attribute "timer4onHM", "String"
        attribute "timer4offHM", "String"     
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
   logDebug("Allow Mixed: ${settings.AllowMixed}")
   rememberState("driver", clientVersion())  
   setSwitchState()    
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
   if (value==true) {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

private create_children() { 
   def dev = create_child("outlet1","Outlet1") 
   if (dev) {state.outlet1DNI = dev.deviceNetworkId}
    
   dev = create_child("outlet2","Outlet2") 
   if (dev) {state.outlet2DNI = dev.deviceNetworkId}
    
   dev = create_child("outlet3","Outlet3") 
   if (dev) {state.outlet3DNI = dev.deviceNetworkId}
    
   dev = create_child("outlet4","Outlet4") 
   if (dev) {state.outlet4DNI = dev.deviceNetworkId}
    
   dev = create_child("usbPorts","USBports")  
   if (dev) {state.USBportsDNI = dev.deviceNetworkId} 
    
   syncOutlets()         
}    

private create_child(devname, label) { 	       
    def newdni = state.my_dni + "_" + devname
            
	def dev = allChildDevices.find {it.deviceNetworkId.contains(newdni)}	
    
    def failed = false

	if (!dev) {
        def labelName = state.name + " " + label
            
		logDebug("Creating child device named ${labelName} with id $newdni")
        
        try {
           dev = addChildDevice("srbarcus", "MultiOutlet Outlet", newdni, [label:"${labelName}",isComponent:false])              
           dev.DeviceSetup(devname)            
            
		} catch (IllegalArgumentException e) {
            failed = true
    		log.error "An error occcurred while trying add child device '$labelName'"
            log.error "Exception: '$e'"
                                
            if (e.message.contains("Please use a different DNI")){    //Could happen if user uninstalled app without deleting previous children                 
                  throw new IllegalArgumentException("A device with dni '$newdni' already exists. Delete the device and try running the " + get_APP_NAME() + " app again.")
            } else {    
                  throw new IllegalArgumentException(e.message)  
            }                  
        } catch (Exception e) {
            failed = true
    		                                
            if (e.message.contains("not found")) {                
                  log.error "Unable to create device '$devname' because driver 'MultiOutlet Outlet' is not installed. You need to install the driver using the 'Modify' option in the 'Hubitat Package Manager' app."
            } else {    
                  log.error "Exception: '$e'"
                  throw new IllegalArgumentException(e.message)  
            }                      
        } finally {
            if (failed) {return null}
        }              
            
	} else {
		log.info "Child device ${dev.displayName} already exists with id $newdni, new device not created"
	}
    
    return dev
}

def on () {
   setSwitch(255,"open") 
}
    
def off () {
   setSwitch(255,"close")  
}    

def usbPorts (state) {
    if (state == "on") { 
       setSwitch(1,"open")  
    } else {    
       setSwitch(1,"close")      
    }     
}

def outlet1 (state) {
    if (state == "on") { 
       setSwitch(2,"open")  
    } else {    
       setSwitch(2,"close")      
    }     
}

def outlet2 (state) {
    if (state == "on") { 
       setSwitch(4,"open") 
    } else {    
       setSwitch(4,"close")      
    }     
}

def outlet3 (state) {
    if (state == "on") { 
       setSwitch(8,"open")  
    } else {    
       setSwitch(8,"close")      
    }     
}

def outlet4 (state) {
    if (state == "on") { 
       setSwitch(16,"open") 
    } else {    
       setSwitch(16,"close")      
    }     
}

def outlet1TimerOn(minutes) {
  setDelay(1, minutes,state.timer1off)
} 

def outlet1TimerOff(minutes) {
  setDelay(1, state.timer1on,minutes)
} 

def outlet2TimerOn(minutes) {
  setDelay(2, minutes,state.timer2off)
} 

def outlet2TimerOff(minutes) {
  setDelay(2, state.timer2on,minutes)
} 

def outlet3TimerOn(minutes) {
  setDelay(3, minutes,state.timer3off)
} 

def outlet3TimerOff(minutes) {
  setDelay(3, state.timer3on,minutes)
} 

def outlet4TimerOn(minutes) {
  setDelay(4, minutes,state.timer4off)
} 

def outlet4TimerOff(minutes) {
  setDelay(4, state.timer4on,minutes)
} 

def outlet1Delays(onoff) {
  outletDelays(1,onoff)
}

def outlet2Delays(onoff) {
  outletDelays(2,onoff)
}

def outlet3Delays(onoff) {
  outletDelays(3,onoff)
}

def outlet4Delays(onoff) {
  outletDelays(4,onoff)
}

def outletDelays(outlet,onoff) {
  def err  
  def mins = onoff.split(',') 
  
  if (mins.size() != 2) {
      err="Expected two parameters: $onoff = " + mins.size() 
      lastResponse(err) 
      log.error "$err"
      return
  }  
    
  def onmins = mins[0]
  def offmins = mins[1]
    
  if (!onmins.isNumber()) {
      err="Non-numeric parameter error: $onmins"
      lastResponse(err) 
      log.error "$err"
      return
  }    
  
  if (!offmins.isNumber()) {
      err="Non-numeric parameter error: $offmins"
      lastResponse(err) 
      log.error "$err"
      return
  } 
    
  if (onmins.toDouble() != Math.rint(onmins.toDouble())) {
      err="Non-integer parameter error: $onmins"
      lastResponse(err) 
      log.error "$err"
      return
  }     
    
  if (offmins.toDouble() != Math.rint(offmins.toDouble())) {
      err="Non-integer parameter error: $offmins"
      lastResponse(err) 
      log.error "$err"
      return
  }         
  
  setDelay(outlet,onmins,offmins)  
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
   def firmware = object.data.version.toUpperCase()
   def signal = object.data.loraInfo.signal          
   
   def USB      = outletSwitch(object.data.state[0])
   def outlet1  = outletSwitch(object.data.state[1])
   def outlet2  = outletSwitch(object.data.state[2])
   def outlet3  = outletSwitch(object.data.state[3])
   def outlet4  = outletSwitch(object.data.state[4])
    
   syncOutlets() 
      
   def timer1on   = object.data.delays[0].on 
   def timer1off  = object.data.delays[0].off  
   def timer2on   = object.data.delays[1].on 
   def timer2off  = object.data.delays[1].off
   def timer3on   = object.data.delays[2].on 
   def timer3off  = object.data.delays[2].off  
   def timer4on   = object.data.delays[3].on 
   def timer4off  = object.data.delays[3].off   
    
   def timer1onHM  = (timer1on/60).toInteger() + ":" +  (timer1on-((timer1on/60).toInteger()*60)) 
   def timer1offHM = (timer1off/60).toInteger() + ":" + (timer1off-((timer1off/60).toInteger()*60))     
   def timer2onHM  = (timer2on/60).toInteger() + ":" +  (timer2on-((timer2on/60).toInteger()*60)) 
   def timer2offHM = (timer2off/60).toInteger() + ":" + (timer2off-((timer2off/60).toInteger()*60))     
   def timer3onHM  = (timer3on/60).toInteger() + ":" +  (timer3on-((timer3on/60).toInteger()*60)) 
   def timer3offHM = (timer3off/60).toInteger() + ":" + (timer3off-((timer3off/60).toInteger()*60))     
   def timer4onHM  = (timer4on/60).toInteger() + ":" +  (timer4on-((timer4on/60).toInteger()*60)) 
   def timer4offHM = (timer4off/60).toInteger() + ":" + (timer4off-((timer4off/60).toInteger()*60))      
          
   logDebug("USB=$USB, Outlet1=$outlet1, Outlet2=$outlet2, Outlet3=$outlet3, Outlet4=$outlet4, Firmware=$firmware, Signal=$signal")  
   logDebug("Timer4 on=$timer4on($timer4onHM), Timer4 off=$timer4off($timer4offHM)")   
   logDebug("Timer3 on=$timer3on($timer3onHM), Timer3 off=$timer3off($timer3offHM)")   
   logDebug("Timer2 on=$timer2on($timer2onHM), Timer2 off=$timer2off($timer2offHM)")   
   logDebug("Timer1 on=$timer1on($timer1onHM), Timer1 off=$timer1off($timer1offHM)")       
    
   rememberState("firmware",firmware)
   rememberState("signal",signal)
   rememberState("USBports",USB)
   rememberState("outlet1",outlet1)
   rememberState("outlet2",outlet2)
   rememberState("outlet3",outlet3)
   rememberState("outlet4",outlet4)
   rememberState("timer1on",timer1on)
   rememberState("timer1onHM",timer1onHM) 
   rememberState("timer1off",timer1off) 
   rememberState("timer1offHM",timer1offHM)  
   rememberState("timer2on",timer2on)
   rememberState("timer2onHM",timer2onHM) 
   rememberState("timer2off",timer2off) 
   rememberState("timer2offHM",timer2offHM)   
   rememberState("timer3on",timer3on)
   rememberState("timer3onHM",timer3onHM) 
   rememberState("timer3off",timer3off) 
   rememberState("timer3offHM",timer3offHM)   
   rememberState("timer4on",timer4on)
   rememberState("timer4onHM",timer4onHM)     
   rememberState("timer4off",timer4off) 
   rememberState("timer4offHM",timer4offHM) 
    
   setSwitchState()   
}   

def setSwitchState() {
    def USB = state.USBports
    def outlet1 = state.outlet1
    def outlet2 = state.outlet2
    def outlet3 = state.outlet3
    def outlet4 = state.outlet4
    
    logDebug("Setting switch state: USB(${USB}), Outlet1(${outlet1}), Outlet2(${outlet2}), Outlet3(${outlet3}), Outlet4(${outlet4})")
    
    if (settings.AllowMixed) { 
      if (USB == "off" && outlet1 == "off" && outlet2 == "off" && outlet3 == "off" && outlet4 == "off") {
         rememberState("switch","off")
      } else {
           if (USB == "on" && outlet1 == "on" && outlet2 == "on" && outlet3 == "on" && outlet4 == "on") {
                rememberState("switch","on")
           } else {
               rememberState("switch","mixed")
           }    
      }       
    } else {
      if (USB == "off" && outlet1 == "off" && outlet2 == "off" && outlet3 == "off" && outlet4 == "off") {
         rememberState("switch","off")
      } else {
         rememberState("switch","on")
      }           
    }    
    
    logDebug("Switch state: ${state.switch}")
}    

def outletSwitch(state) {
    def status
    if (state == "open") {
        status = "on"
    } else { 
        if (state == "closed" || state == "close") {
            status = "off"
        } else {     
            status = "unknown"
        }   
    }    
    return status                        
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
        case "setDelay":
            sendEvent(name:"delayChanged", value: "true", isStateChange:true)
			break;    
            
		case "setInitState":
            def powerOnState = parent.relayState(object.data.initState) 
            rememberState("powerOnState",powerOnState)                 
			break;
            
		case "setTimeZone":
            def tzone = object.data.tz
            logDebug("Parsed: Timezone=$tzone")
            rememberState("tzone",tzone)                          
			break;
            
        case "setState":
        case "StatusChange":
            def signal = object.data.loraInfo.signal          
            def USB      = outletSwitch(object.data.state[0])
            def outlet1  = outletSwitch(object.data.state[1])
            def outlet2  = outletSwitch(object.data.state[2])
            def outlet3  = outletSwitch(object.data.state[3])
            def outlet4  = outletSwitch(object.data.state[4])
            
            rememberState("signal",signal)
            rememberState("USBports",USB)
            rememberState("outlet1",outlet1)
            rememberState("outlet2",outlet2)
            rememberState("outlet3",outlet3)
            rememberState("outlet4",outlet4)
            
            setSwitchState() 
            
            syncOutlets()
            
            logDebug("$event: USBports=$USB, Outlet1=$outlet1, Outlet2=$outlet2, Outlet3=$outlet3, Outlet4=$outlet4, Signal=$signal") 
   
			break;       
            
        case "getState":
        case "Report":    
            parseDevice(object)
			break;	

        case "getSchedules":    
        case "setSchedules":
            def schedules = object.data
                
            logDebug("Parsed: Schedules=$schedules")
            
            removeSchedules()
            
            def schedNum = 0
            def scheds = 0
            while (schedNum < 9){        
                if (object.data."${schedNum}" != null) { 
                  def schedule  =  object.data."${schedNum}".toString() 
                  
                  def weekhex =  object.data."${schedNum}".week 
                    
                  def weekdays = parent.scheduledDays(weekhex)
                    
                  logDebug("Days of week: ${weekdays}")
                  
                  logDebug("schedule ${schedule}")
                                                   
                  schedule = schedule.replaceAll(" week=${weekhex},"," days=[${weekdays}],")                                          
                  schedule = schedule.replaceAll(" index=${schedNum},","")                       
                  schedule = schedule.replaceAll("isValid=","enabled=")  
                  schedule = schedule.replaceAll("=25:0","=never") 
                  schedule = schedule.replaceAll("ch=","outlet=")  
                  
                  scheds++  
                  logDebug("Schedule ${scheds}: ${schedule}")
                  rememberState("schedule${scheds}",schedule)
                } 
                schedNum++
            }  
            
            rememberState("schedules",scheds)
            
            scheds++  
            while (scheds <= 6){        
                sendEvent(name:"schedule${scheds}", value: " ", isStateChange:false)
                scheds++
            }              
           
			break; 
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
}

def setSwitch(mask,SWstate) {
   logDebug("setSwitch($mask, $SWstate)")  
    
   def params = [:] 
   params.put("chs", mask)  
   params.put("state", SWstate)    
    
   def request = [:] 
   request.put("method", "${state.type}.setState")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("setSwitch(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {                               
                def signal   = object.data.loraInfo.signal       
                def USB      = outletSwitch(object.data.state[0])
                def outlet1  = outletSwitch(object.data.state[1])
                def outlet2  = outletSwitch(object.data.state[2])
                def outlet3  = outletSwitch(object.data.state[3])
                def outlet4  = outletSwitch(object.data.state[4])
                            
                rememberState("signal",signal)  
                rememberState("USBports",USB)
                rememberState("outlet1",outlet1)
                rememberState("outlet2",outlet2)
                rememberState("outlet3",outlet3)
                rememberState("outlet4",outlet4)
                
                setSwitchState() 
                
                syncOutlets()               
                
                if (mask == 255) {
                   lastResponse("All outlets set to " + outletSwitch(SWstate))                                    
                } else {
                   lastResponse("Outlet set to " + outletSwitch(SWstate))                                    
                }    
                 
            } else {                
                     log.error "setSwitch() failed: ${object}"  
                     lastResponse("setSwitch() failed")     
            }                     
                
	    } else { 			               
            logDebug("setSwitch() failed")	
            lastResponse("setSwitch() failed")     
        }     		
	} catch (e) {	
        log.error "setSwitch() exception: $e"
        lastResponse("setSwitch() exception: ${e}")     
	} 
}  

def syncOutlets() {
     def dev = getChildDevice(state.outlet1DNI)
     if (state.outlet1 == "on") {dev.on(false)} else {dev.off(false)}   
     dev = getChildDevice(state.outlet2DNI)
     if (state.outlet2 == "on") {dev.on(false)} else {dev.off(false)}  
     dev = getChildDevice(state.outlet3DNI)
     if (state.outlet3 == "on") {dev.on(false)} else {dev.off(false)}  
     dev = getChildDevice(state.outlet4DNI)
     if (state.outlet4 == "on") {dev.on(false)} else {dev.off(false)}  
     dev = getChildDevice(state.USBportsDNI)
     if (state.USBports == "on") {dev.on(false)} else {dev.off(false)}  
}

def setDelay(outlet, minuteson, minutesoff) {
   logDebug("setDelay($outlet, $minuteson, $minutesoff)")  
    
   def params = [:]    
   def delays = [:] 
   def delay = []     
  
   minuteson  = minuteson.toInteger()
   minutesoff = minutesoff.toInteger()  
    
   if (minuteson == 1) {log.warn "Time on delay of 1 minute will not be reflected in device values because countdown resolution is in minutes. Delay will still be honored."} 
   if (minutesoff == 1) {log.warn "Time off delay of 1 minute will not be reflected in device values because countdown resolution is in minutes. Delay will still be honored."}
     
   //Max value is 1439 minutes (23:59)
   if (minuteson > 1439 || minutesoff > 1439) {
      lastResponse("Minute value exceeds 1439 (23:59)")      
      return
   }    
    
   delay[1] = [:]  
    
   delay[1].put("ch", outlet)    
   delay[1].put("on", minuteson)    
   delay[1].put("off", minutesoff)     
    
   params.put("delays", delay)             
   params.put("state", delays)            
    
   def request = [:] 
   request.put("method", "${state.type}.setDelay")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("setDelay(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {       
                getDevicestate()  
                lastResponse("Delay on outlet ${outlet} set - On:${minuteson}, Off:${minutesoff}")
            } else {                
                log.warn "setDelay() failed: ${object}'"  
                lastResponse("setDelay() failed")     
            }                     
	    } else { 			               
            log.warn "setDelay() failed"  
            lastResponse("setDelay() failed")     
        }     		
	} catch (e) {	
        log.error "setDelay() exception: $e"
        lastResponse("Error ${e}")     
	} 
}    

def reset(){    
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
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
    state.remove("powerOnState")
    
    state.remove("LastResponse")      
    state.remove("USBports")
    state.remove("outlet1")
    state.remove("outlet2")
    state.remove("outlet3")
    state.remove("outlet4")
    
    state.remove("outlet1DNI")
    state.remove("outlet2DNI")
    state.remove("outlet3DNI")
    state.remove("outlet4DNI")
    state.remove("USBportsDNI")
      
    state.remove("timer1on")
    state.remove("timer1off")
    state.remove("timer2on")
    state.remove("timer2off")
    state.remove("timer3on")
    state.remove("timer3off")
    state.remove("timer4on")
    state.remove("timer4off")
    state.remove("timer1onHM")
    state.remove("timer1offHM")
    state.remove("timer2onHM")
    state.remove("timer2offHM")
    state.remove("timer3onHM")
    state.remove("timer3offHM")
    state.remove("timer4onHM")
    state.remove("timer4offHM")
    
    def children= getChildDevices()
	children.each {deleteChildDevice(it.deviceNetworkId)}    
    create_children()     
    
    removeSchedules()
              
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
   if (state.debug==true) {log.debug msg}
}

def removeSchedules() {
    state.remove("schedules")
    state.remove("schedule1")
    state.remove("schedule2")
    state.remove("schedule3")
    state.remove("schedule4")
    state.remove("schedule5")
    state.remove("schedule6")
    state.remove("schedule7")
    state.remove("schedule8")
    state.remove("schedule9") 
}    
