/***
 *  YoLink™ GarageDoor Device (YS4906-UC)
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
 *  1.0.1: Remove MQTT Connection to this device - device has no callbacks defined
 *  2.0.0: Sync version with reengineered app
 *  2.0.1: Added 'GarageDoorControl' capability to 'GarageDoor Device' driver. For this capabilityto work properly, the controller must be bound to the garage door sensor!
 *  2.0.2: Support diagnostics, correct various errors, make singleThreaded
 *  2.0.3: - Corrented problem with parsing of device's MQTT vs bound device's MQTT
 *         - Replaced "Signal" with "RSSI" per standards and added capability "SignalStrength"
 *         - Added warnings for "open" and "close" if no bound device
 *  2.0.4: Added formatted "signal" attribute as rssi & " dBm"
 *  2.0.5: Fixed MQTT message processing
 *  2.0.6: Prevent Service app from waiting on device polling completion
 *  2.0.7: Updated driver version on poll
 *  2.0.8: Remove MQTT processsing for bound device, remove "connect" command and API attribute
 *         - Add "setBoundState()" for direct processing by bound device
 *         - Add "unbind" command
 *         - Remove polling capability
 *         - Support "setDeviceToken()"
 *         - Update copyright
 *  2.0.9:  Fix parse()
 *  2.0.10: Fix initialize() errors
 *         - Added garage door timeout command and setting state to 'unknown'
 *  2.0.11: Fix default timeout - set to 45 seconds
 *  2.0.12: Add option to ignore garage state for open and close
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.12"}
def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ GarageDoor Device (YS4906-UC) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Date Format Template Specifications"), description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"    
}

metadata {
    definition (name: "YoLink GarageDoor Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) { 
        capability "Initialize"
        capability "Momentary"
        capability "ContactSensor"
        capability "GarageDoorControl"
        capability "SignalStrength"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]]
        command "reset"     
        command "scan"    
        command "timestampFormat", [[name:"timestampFormat",type:"STRING", description:"Formatting template for event timestamp values. See Preferences below for details."]] 
        command "bind", [[name:"bind",type:"STRING", description:"Device ID (devId) of Garage Door Sensor to be bound to this controller. See 'sensors' under 'State Variables' below and copy & paste a Sensor here."]] 
        command "unbind"
        command "doorTimeout", [[name:"doortimeout",type:"NUMBER", description:"Time in seconds to allow for door to open or close before changing door state to 'unknown'. Must be between 15 and 120. Default=45 seconds"]]
        command "ignoreDoorState", [[name:"debug",type:"ENUM", description:"Always toggle controller regardless of indicated door state", constraints:["true", "false"]]]
        
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "stateChangedAt", "String"
        attribute "lastResponse", "String" 
        attribute "doorTimeout", "Boolean"
        attribute "ignoreDoorState", "Number"
        
        attribute "bound_battery", "String"
        attribute "bound_firmware", "String"
        attribute "bound_signal", "String"
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
   rememberState("driver", clientVersion())  
 }

def updated() {
   rememberState("driver", clientVersion()) 
 }

def initialize() {
   rememberState("driver", clientVersion())
   refreshSensor()    
}

def uninstalled() {
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def pollDevice(delay=1) {
    rememberState("driver", clientVersion())
    def date = new Date()
    sendEvent(name:"lastPoll", value: date.format("MM/dd/yyyy hh:mm:ss a"), isStateChange:true)
 }

def temperatureScale(value) {}

def open() {
   toggle("open")
}

def close() {
   toggle("close")
}

def push() {
   toggle("push")
}

def toggle(func) {
   def currentState =  doorState()
   boolean ignoreDoor = (state."ignoreDoorState" == true) 
    
   logDebug("toggle(${func}): Door " + currentState + ", IgnoreDoorState = $ignoreDoor") 
   if (!boundToDevice()) { 
     rememberState("door","not bound")
     rememberState("contact","not bound")   
   }   
    
   switch(func) {
      case "open":
           if (boundToDevice()) { 
             if (((currentState != "closed") && (currentState != "closing") && (currentState != "unknown")) && (!ignoreDoor) ) {
                 logDebug("Open ignored. Door state(${currentState}) Ignored state(${ignoreDoor})")
                 return}
           }  
         break;
       
      case "close":  
           if (boundToDevice()) { 
             if (((currentState != "open") && (currentState != "opening") && (currentState != "unknown")) && (!ignoreDoor) ) {
                 logDebug("Close ignored. Door state(${currentState}) Ignored state(${ignoreDoor})")
                 return}
           }
		 break;              
              
      default:         
		 break; 
   } 
   
   if (boundToDevice()) { 
      def contact 
      def dev = parent.findChild(state.bound_devId)	
      if (!dev) {
         log.error "Unable to refresh contact sensor device: Could not locate device ID '${state.bound_devId}'"
      } else {
         contact = dev.contactState()
      }      
      
      logDebug("Current sensor state: ${contact}")     
       
      if (contact == "open") {  
         rememberState("door","closing")
      } else {
         if (contact == "closed") {  
            rememberState("door","opening")
         }    
      }    
       
   } else { 
      log.warn "No contact sensor is bound to this device. Unable to determine current door state."
   }      
    
   def request = [:]    
   request.put("method", "${state.type}.toggle")   
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")       
 
   try {         
       state.moving = true 
       
       def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("toggle(): pollAPI() response: ${object}")    
                              
            if (successful(object)) {        
                  def rssi = object.data.loraInfo.signal       
                
                  fmtSignal(rssi)
                  rememberState("online","true")                    
                  lastResponse("Success")   
                  def delay = state.doorTimeout
                  if (!delay) {delay=45}
                
                  logDebug("Door timeout is '${delay}'") 
                
                  runIn(delay,checkDoorState)
                               
            } else {
               if (notConnected(object)) {  //Cannot connect to Device
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                   if (boundToDevice()) {checkDoorState()} 
               }
            }                     
                                        
            return
                
	    } else { 			               
            logDebug("toggle(${func}) failed")	
            lastResponse("push() failed")     
        }     		
	} catch (e) {	
        log.error "toggle(${func}) exception: $e"
        lastResponse("Error ${e}")      
	} 
}

def checkDoorState() {
   logDebug("Checking door state, moving= ${state.moving}") 
    
   if (!state.moving) {return}
  
   def curstate = device.currentValue("door",true)  
   boolean ignoreDoor = (state."ignoreDoorState" == "true")    
    
   logDebug("-->Timed out. Door state = $curstate, Ignore=$ignoreDoor")  
    
   if (boundToDevice()) { 
      if (($curstate != "open") && ($curstate != "closed")) {  
         rememberState("door","unknown")
         rememberState("contact","unknown")  
 
         lastResponse("Garage door is in unknown state")
         if (ignoreDoor) {   
           log.warn("Garage door is in unknown state but ignore Door State = true. Running sensor check in 10 seconds.") 
           runIn(10,checkSensor)   
    
         } else {
           log.warn("Garage door is in unknown state and Ignore Door State = false. Door must be moved manually or using 'Push'.")
         }    
      } else {  
         checkSensor() 
      }    
   } else {
       rememberState("door","unknown")
       rememberState("contact","unknown")
   }
}

def checkSensor() {
   if (!state.moving) {return} 
   refreshSensor() 
} 

def refreshSensor() {
   if (boundToDevice()) {  
      def dev = parent.findChild(state.bound_devId)	
      if (!dev) {
         log.error "Unable to refresh contact sensor device: Could not locate device ID '${state.bound_devId}'"
      } else {
         logDebug("Updating values from sensor")
         dev.setControllerState()
      }        
    } else {
        rememberState("door","not bound")
        rememberState("contact","not bound") 
        state.remove("stateChangedAt")
    }    
}   

def scan() {
  def myid = "YoLink " + state.type + " - " + state.name   
    
  def devices=parent.getChildDevices()
  int devicesCount=devices.size()      
  logDebug("Located $devicesCount devices: $devices")
 
  def dev = "<br>Copy and Paste one of these into the 'Bind' value above then click 'Bind' to bind Sensor with Controller:"
    
  def boundTo
    
  devices.each { dni ->  
     def id = dni.toString() 
      
     logDebug("Checking Device: ${id}") 
                    
     if (id != myid) { 
       def setup = dni?.getSetup()   
         
       if (setup?.type == "DoorSensor") {  
         def name = setup?.name
         def devId = setup?.devId         
              
         id = devId + "=" + name
           
         if (dni?.boundToDevice()) {
            boundTo = dni?.boundDevice()
            boundTo = "  (Currently bound to '${boundTo}')"
         } else {
            boundTo=""
         }
           
         logDebug("Found Door Sensor: ${id} ${boundTo}") 
           
         dev = dev + "<br>" + id + boundTo
       }    
     }          
  } 
    
  state.sensors = dev
}

def boundToDevice() {(state.bound_devId != null)}

def doorState() {
    def curstate = state."door"
    logDebug("Current door state = $curstate")
    return curstate
}     

def bind(sensorid) {
  if (!sensorid) {  
    log.error "Device ID to be bound was not specified"
    return  
  }        
    
  if (boundToDevice()) {  
      log.error "Binding failed. Already bound to '${state.bound_name}'."
      lastResponse("Binding failed. Already bound to '${state.bound_name}'.")
      return  
  }  
    
  def ndx = sensorid.indexOf('=')   
    
  if (ndx != -1) {  
    sensorid = sensorid.substring(0, ndx)        
  }  
      
  if (sensorid.length() != 16) {                      //DevIds are 16 characters
    log.error "Unable to bind device:  DevId '$sensorid' is not 16 characters long"
    return
  }      
    
  logDebug ("Attempting to locate device '$sensorid'")    
     
  def bound
  def my_dni
  def homeID
  def name
  def type
  def token
  def devId
    
  def dev = parent.findChild(sensorid)	
  if (!dev) {
    log.error "Unable to bind device: Could not locate device ID '$sensorid'"
    lastResponse("Bind failed: Could not locate device ID '$sensorid'")   
    return 
  } else {
      def setup    
      setup = dev.getSetup()   
              
      name = setup.name
      
      if (dev.boundToDevice()) {
        def boundTo
        boundTo = dev.boundDevice()  
        log.error "Unable to bind '${name}', it is already bound to '${boundTo}'"
        lastResponse("Bind failed. '${name}' already bound to '${boundTo}'")   
        return
      }
      
      my_dni = setup.my_dni    
      homeID = setup.homeID
      type = setup.type
      token = setup.token
      devId = setup.devId
      
      state.bound_dni = my_dni  
      state.bound_homeID = homeID
      state.bound_name = name
      state.bound_type = type
      state.bound_token = token
      state.bound_devId = devId
    
      dev.bind(state.my_dni,state.name)      
      
      logDebug("Bound Device: dni=${my_dni}, Home ID=${homeID}, Name=${name}, Type=${type}, Token=${token}, Device Id=${devId})")
      log.info "Bound to '${name}', DNI=${my_dni})"
      lastResponse("Bound to contact sensor: ${name}") 
  }  
}

def unbind() {
    if (!boundToDevice()) {  
      log.error "Unbind failed: No device is currently bound."
      lastResponse("Unbind failed: Not currently bound.")  
      return  
    }
    
    def dev = parent.findChild(state.bound_devId)	
    if (!dev) {
        log.error "Unable to unbind device: Could not locate device ID '${state.bound_devId}'"
    } else {
        dev.bind(null,null)
    }    
    
    log.warn "Unbinding contact sensor: ${state.bound_name} "
    state.remove("bound_dni")  
    state.remove("bound_homeID")  
    state.remove("bound_name")  
    state.remove("bound_type")  
    state.remove("bound_token")  
    state.remove("bound_devId") 
        
    rememberState("door","not bound")
    rememberState("contact","not bound") 
}

def setControllerState(contact,battery,firmware,signal,stateChangedAt) {
   logDebug("Sensor called setControllerState($contact,$battery,$firmware,$signal,$stateChangedAt)")  
 
   rememberState("door",contact) 
   rememberState("contact",contact) 
   rememberState("bound_battery",battery,"%")
   rememberState("bound_firmware",firmware)
   rememberState("bound_signal",signal)  
   rememberState("stateChangedAt",stateChangedAt)

   logDebug("Pre Door " + device.currentValue("door",true))    
   device.updateDataValue("door", contact)        // For some reason sendEvent doesn't update state.door?!
   logDebug("Post Door " + device.currentValue("door",true))  
    
   rememberState("moving",false) 
}

def parse(message) {        
    logDebug("parse(${message})")
    processStateData(message.payload) 
}

def void processStateData(payload) {
    rememberState("online","true") 
    
    def object = new JsonSlurper().parseText(payload)    
    def devId = object.deviceId   
            
    if (devId == state.devId) {  // Only handle if message is for me    
        logDebug("processStateData(${payload})")
        
        def child = parent.getChildDevice(state.my_dni)
        def name = child.getLabel()                
        def event = object.event.replace("GarageDoor.","")
        logDebug("Received Message Type: ${event} for: $name")
        
        switch(event) {
		case "Alert":            
			def contact = object.data.state           
            def battery = parent.batterylevel(object.data.battery)    // Value = 0-4    
            def firmware = object.data.version.toUpperCase()    
            def rssi = object.data.loraInfo.signal  
            
            def stateChangedAt = object.data.stateChangedAt
                         
            stateChangedAt = formatTimestamp(stateChangedAt)
             
            rememberState("contact",contact)
            rememberState("door",contact)
            rememberState("battery",battery)
            rememberState("firmware",firmware)
            fmtSignal(rssi)     
            rememberState("stateChangedAt",stateChangedAt)
            
            lastResponse("Garage door is ${contact}")
		    break;    
            
      case "Report":
            def firmware = object.data.version.toUpperCase()    
            def rssi = object.data.loraInfo.signal  
            
            rememberState("firmware",firmware)
            fmtSignal(rssi)      
		    break;        
            
       case "setState":
            if (!boundToDevice()) {        //If bound, let bound device update state
              def contact = object.data.state          
              rememberState("contact",contact)
              rememberState("door",contact)
            }    

            def rssi = object.data.loraInfo.signal    
            fmtSignal(rssi)      
		    break;             
            
        case "setOpenRemind":    
            def openRemindDelay = object.data.openRemindDelay   
            def alertInterval = object.data.alertInterval                             
            def rssi = object.data.loraInfo.signal  
    
            rememberState("openRemindDelay",openRemindDelay) 
            rememberState("alertInterval",alertInterval)
            fmtSignal(rssi)                  
            break;	  
            
          case "StatusChange":    
            def rssi = object.data.loraInfo.signal  
            fmtSignal(rssi)                  
            break;	     
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
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

def doorTimeout(value) {
    value = value ?: 45 // No value, reset to default
    if ((value < 15) || (value > 120)) {
      log.error "Door timeout of '${value}' is invalid. Value must be between 15 and 120." 
      lastResponse("Door timeout of '${value}' is invalid. Value must be between 15 and 120.")
      return  
    }    
    
    rememberState("doorTimeout",value)
    logDebug("Door timeout set to '${value}'") 
    lastResponse("Door timeout set to '${value}'")  
 }

def ignoreDoorState(value) {
    rememberState("ignoreDoorState",value)
    if (value != "true") {
      logDebug("Ignore door state set to '${value}'") 
      lastResponse("Ignore door state set to '${value}'")
    } else {
      log.warn "Ignore door state set to '${value}'. Door movement will be initiated regardless of its position." 
      lastResponse("WARNING! Ignore door state set to '${value}'. Door movement will be initiated regardless of its position.")
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
    rememberState("driver", clientVersion()) 
    
    state.remove("online")
    state.remove("API")              //No long applicable as of v1.1.0 - Remove in future
    state.remove("firmware")    
    state.remove("rssi")     
    state.remove("signal")
    state.remove("sensors")
    state.remove("battery")
    state.remove("doorTimeout")
    state.remove("ignoreDoorState")
    
    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a" 
    
    rememberState("doorTimeout",45)
    rememberState("ignoreDoorState",false)
    
    scan()
    
    rememberState("door","unknown")
    rememberState("contact","unknown")
    refreshSensor()
    
    log.warn "Device reset to default values"
    lastResponse("Device reset to default values") 
}

def lastResponse(value) {
   sendEvent(name:"lastResponse", value: "$value", isStateChange:true)   
}

def rememberState(name,value,unit=null) {
   def curValue = state."$name"
    
   logDebug("-->rememberState() name='${name}', value='${value}', Unit='${unit}'. Current state:'" +  curValue + "'")  
          
   if (curValue.toString() != value.toString()) {
     state."$name" = value  
     value=value.toString() 
       
     if (name == "door") {
        device.updateDataValue("door", value) 
     }
         
     logDebug("<>rememberState() Sending Event(name='${name}, value='${value}')")    
       
     if (unit==null) {  
         device.sendEvent(name:"$name", value: "$value", isStateChange:true)
     } else {        
         device.sendEvent(name:"$name", value: "$value", unit: "$unit", isStateChange:true)      
     }           
   }  
       
   logDebug("<--rememberState() Result:'${name}' = '" + state."$name" + "'")  
}     

def successful(object) {
  return (object.code  == "000000")     
}    

def notConnected(object) {
  return (object.code == "000201")
}

def logDebug(msg) {
  if (state.debug == "true") {log.debug msg}
}

def fmtSignal(rssi) {
   rememberState("rssi",rssi) 
   rememberState("signal",rssi.plus(" dBm")) 
}    