/***
 *  YoLink™ Finger Device (YS4908-UC)
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
 *  1.0.1: Support diagnostics, correct various errors, make singleThreaded
 *  1.0.2: - Corrented problem with parsing of device's MQTT vs bound device's MQTT
 *         - Replaced "Signal" with "RSSI" per standards and added capability "SignalStrength"
 *         - Added capability "Momentary"
 *         - Added warnings for "open" and "close" if no bound device
 *  1.0.3: Added formatted "signal" attribute as rssi & " dBm"
 *  1.0.4: Prevent Service app from waiting on device polling completion
 *  1.0.5: Updated driver to recognize actions being initiated on the YoLink app
 *  1.0.6: Updated driver version on poll
 *  1.0.7: Add capability "Battery"
 *         - Added states: "bound_battery", bound_firmware
 *         - Fix null StateChangedAt
 *  1.1.0: Remove MQTT processsing for bound device
 *         - Add "setBoundState()" for direct processing by bound device
 *         - Remove polling as device doesn't support it
 *         - Added "Unbind" command
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.net.URLEncoder
import groovy.transform.Field

def clientVersion() {return "1.1.0"}
def copyright() {return "<br>© 2022, 2023 Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ Finger Device (YS4908-UC) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Date Format Template Specifications"), description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"    
}

metadata {
    definition (name: "YoLink Finger Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) { 
        capability "Initialize"
        capability "Momentary"
        capability "ContactSensor"
        capability "GarageDoorControl"
        capability "SignalStrength"   
        capability "Battery"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]] 
        command "reset"     
        command "scan"    
        command "timestampFormat", [[name:"timestampFormat",type:"STRING", description:"Formatting template for event timestamp values. See Preferences below for details."]] 
        command "bind", [[name:"bind",type:"STRING", description:"Device ID (devId) of Garage Door Sensor to be bound to this controller. See 'sensors' under 'State Variables' below and copy & paste a Sensor here."]] 
        command "unbind"
        
        attribute "API", "String"
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "stateChangedAt", "String"
        attribute "lastResponse", "String"         
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

def initialize() {
}

def uninstalled() {
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def pollDevice(delay) {
   def date = new Date() 
   sendEvent(name:"lastPoll", value: date.format("MM/dd/yyyy hh:mm:ss a"), isStateChange:true) 
}

def temperatureScale(value) {}

def scan() {
  def myid = "YoLink " + state.type + " - " + state.name   
    
  def devices=parent.getChildDevices()
  int devicesCount=devices.size()      
  logDebug("Located $devicesCount devices: $devices")
 
  def dev = "<br>Copy and Paste one of these into the 'Bind' value above then click 'Bind' to bind Sensor with Controller:"
    
  devices.each { dni ->  
     def id = dni.toString() 
      
     logDebug("Checking Device: ${id}") 
                    
     if (id != myid) { 
       def setup = dni?.getSetup()   
         
       if (setup?.type == "DoorSensor") {  
         def name = setup?.name
         def devId = setup?.devId         
              
          id = devId + "=" + name
           
          logDebug("Found Door Sensor: ${id}") 
           
          dev = dev + "<br>" + id
       }    
     }          
  }    
    
  state.sensors = dev
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

def debug(value) { 
   rememberState("debug",value)
   if (value == "true") {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

def parse(topic) {
    log.info "Parse($topic)"
    processStateData(topic.payload)
}

def void processStateData(payload) {
    rememberState("online","true") 
    
    def object = new JsonSlurper().parseText(payload)  
    def devId = object.deviceId       
    
    logDebug("processStateData(${payload})")
        
    def child = parent.getChildDevice(state.my_dni)
    def name = child.getLabel()                
    def event = object.event.replace("${state.type}.","")
    logDebug("Received Message Type: ${event} for: $name")
        
    switch(event) {
      case "Report":
      case "setState":  
            def devstate = object.data.state          
            def battery = parent.batterylevel(object.data.battery)    // Value = 0-4    
            def firmware = object.data.version.toUpperCase()    
            def rssi = object.data.loraInfo.signal              
            
            rememberState("battery",battery,"%")
            rememberState("firmware",firmware)
            fmtSignal(rssi)
        
            //if (devstate == "stop") {} //User pressed "Set" on Finger
        
            logDebug("Parse Finger MQTT: State=${devstate}, Battery=${battery}, Firmware=${firmware}, RSSI=${rssi}")
		    break;              
              
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
}

def setBoundState(doorstate,battery,firmware,signal,stateChangedAt) {
   logDebug("setBoundState(${doorstate},${battery},${firmare},${signal},${stateChangedAt})")
   rememberState("door",doorstate)
   rememberState("contact",doorstate) 
   rememberState("bound_battery",battery,"%")
   rememberState("bound_firmware",firmware)
   rememberState("bound_signal",signal)  
   rememberState("stateChangedAt",stateChangedAt)
}  

def open() {
   logDebug("open()")  
   toggle("open")
}

def close() {
   logDebug("close()")
   toggle("close")
}

def push() {
   logDebug("push()")
   toggle()
}

def toggle(func="toggle") {
   logDebug("toggle(): Door ".plus(state.door)) 
   if (!boundToDevice()) { 
     rememberState("door","not bound")
     }   
    
   switch(func) {
      case "open":
         if (boundToDevice()) { 
            if (state.door == "closed") {  
               rememberState("door","opening")  
            } else {
               if (state.door != "open") {  
                 rememberState("door",state.door)
               }
               return                  
            }    
         }  
         break;
       
      case "close":  
         if (boundToDevice()) { 
            if (state.door == "open") {  
               rememberState("door","closing")  
            } else {
               if (state.door != "closed") {  
                 rememberState("door",state.door)
               }    
               return   
            }    
         }         
		 break;              
              
      default:
         if (boundToDevice()) { 
            if (state.door == "open") {  
               rememberState("door","closing")  
            } else {
               if (state.door == "closed") {  
                 rememberState("door","opening")
               } else {
                 rememberState("door",state.door)  
               }    
            }    
         }         
		 break; 
   } 
    
   def request = [:]    
   request.put("method", "${state.type}.toggle")   
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("toggle(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {        
                  def rssi = object.data.loraInfo.signal       
                
                  logDebug("Parsed: RSSI=$rssi")

                  fmtSignal(rssi)
                  rememberState("online","true")                    
                  lastResponse("Success")     
                               
            } else {
                  if (notConnected(object)) {  //Cannot connect to Device
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
               }
            }                     
                                        
            return
                
	    } else { 			               
            logDebug("toggle() failed")	
            lastResponse("toggle() failed")     
        }     		
	} catch (e) {	
        log.error "toggle() exception: $e"
        lastResponse("Error ${e}")      
	} 
}   

def bind(sensorid) {
  if (!sensorid) {  
    log.error "Device ID to be bound was not specified"
    return  
  }     
  
  if (boundToDevice) {  
      log.error "Binding failed. Already bound to '${state.bound_name}'."
      lastResponse("Binding failed. Already bound.")
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
    return 
  } else {
      def setup    
      setup = dev.getSetup()   
              
      my_dni = setup.my_dni    
      homeID = setup.homeID
      name = setup.name
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

def boundToDevice() {(state.bound_devId != null)}

def unbind() {
    if (!boundToDevice) {  
      log.error "Unbind failed: No device is currently bound."
      return  
    }
    
    log.warn "Unbinding '${state.bound_name}', DNI=${state.bound_dni}"
    
    def dev = parent.findChild(state.bound_devId)	
    if (!dev) {
        log.error "Unable to unbind device: Could not locate device ID '${state.bound_devId}'"
    } else {
      dev.bind(null,null)
    }    
    
    lastResponse("Unbound from contact sensor: ${state.bound_name}") 
    
    state.remove("bound_dni")  
    state.remove("bound_homeID")  
    state.remove("bound_name")  
    state.remove("bound_type")  
    state.remove("bound_token")  
    state.remove("bound_devId") 
    state.remove("bound_battery")  
    state.remove("bound_firmware")
    state.remove("bound_signal") 
    state.remove("contact")
    
    rememberState("door","unknown")
    rememberState("contact","unknown") 
    interfaces.mqtt.disconnect() // Guarantee we're disconnected - hold over from bound device MQTT processing (delete in future)
}

def formatTimestamp(timestamp){    
    if ((state.timestampFormat != null) && (timestamp != null)) {
      def date = new Date( timestamp as long )    
      date = date.format(state.timestampFormat)
      //logDebug("formatTimestamp(): '$state.timestampFormat' = '$date'")
      return date  
    } else {
      return timestamp  
    }    
}

def reset(){    
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("API")
    state.remove("firmware")    
    state.remove("rssi")     
    state.remove("signal") 
    state.remove("contact")
    rememberState("door","unknown") 
    state.remove("stateChangedAt")
    state.remove("LastResponse")  
    state.remove("sensors")  
    state.remove("battery") 
    
    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a" 
    
    unbind()
        
    scan()
    
    interfaces.mqtt.disconnect() // Guarantee we're disconnected - hold over from bound device MQTT processing (delete in future)
    
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

def logDebug(msg) {
  if (state.debug == "true") {log.debug msg}
} 

def fmtSignal(rssi) {
   rememberState("rssi",rssi) 
   rememberState("signal",rssi.plus(" dBm")) 
}    
