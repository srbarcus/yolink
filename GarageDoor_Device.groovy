/***
 *  YoLink™ GarageDoor Device (YS4906-UC)
 *  © 2022 Steven Barcus
 *  THIS SOFTWARE IS NEITHER DEVELOPED, ENDORSED, OR ASSOCIATED WITH YoLink™ OR YoSmart, Inc.
 *   
 *  DO NOT INSTALL THIS DEVICE MANUALLY - IT WILL NOT WORK. MUST BE INSTALLED USING THE YOLINK DEVICE SERVICE APP  
 *
 *  Donations are appreciated and allow me to purchase more YoLink devices for development: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1
 *   
 *  Developer retains all rights, title, copyright, and interest, including patent rights and trade
 *  secrets in this software. Developer grants a non-exclusive perpetual license (License) to User to use
 *  this software under this Agreement. However, the User shall make no commercial use of the software without
 *  the Developer's written consent. Software Distribution is restricted and shall be done only with Developer's written approval.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * 
 */

import groovy.json.JsonSlurper

def clientVersion() {return "1.0.0"}

preferences {
    input title: "Driver Version", description: "YoLink™ GarageDoor Device (YS4906-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Date Format Template Specifications", description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"    
}

metadata {
    definition (name: "YoLink GarageDoor Device", namespace: "srbarcus", author: "Steven Barcus") {     	
        capability "Momentary"
        capability "ContactSensor"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]]
        command "connect"                       // Attempt to establish MQTT connection
        command "reset"     
        command "scan"    
        command "timestampFormat", [[name:"timestampFormat",type:"STRING", description:"Formatting template for event timestamp values. See Preferences below for details."]] 
        command "bind", [[name:"bind",type:"STRING", description:"Device ID (devId) of Garage Door Sensor to be bound to this controller. See 'sensors' under 'State Variables' below and copy & paste a Sensor here."]] 
        
        attribute "API", "String"         
        attribute "online", "String"
        attribute "signal", "String"
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
   interfaces.mqtt.disconnect() // Guarantee we're disconnected  
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def poll(force=null) { //NOT SUPPORTED - For APP compatibly only
}    

def connect() {
   establish_MQTT_connection(state.homeID,state.my_dni)                                               //Establish MQTT connection to YoLink API for this device
   if (state.bound_devId != null) {establish_MQTT_connection(state.bound_homeID, state.bound_devId)}  //Establish MQTT connection to YoLink API for bound device
 }

def temperatureScale(value) {}

def scan() {
  def myid = "YoLink " + state.type + " - " + state.name   
    
  def devices=parent.getChildDevices()
  int devicesCount=devices.size()      
  logDebug("Located $devicesCount devices: $devices")
 
  def dev = "<br>Copy and Paste one of these into the 'Bind' value above, then click 'Bind'; to bind Sensor with Controller:"
    
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

def bind(sensorid) {
  if (!sensorid) {  
      if (state.bound_devId == null) {
          log.error "Device ID to be bound was not specified"
      } else {    
          unbind()    
          interfaces.mqtt.disconnect()      // Guarantee we're disconnected            
          connect()                         // Reconnect to API Cloud  
      }    
    return  
  }     
    
  def myid = "YoLink " + state.type + " - " + state.name  
      
  def devices= parent.getChildDevices()
  int devicesCount=devices.size()      
  logDebug("Located " + devicesCount + " devices: " + devices)  
  
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
    
  devices.each { dni ->                   
    if (!bound) {
      def setup    

      try {
          if (dni.toString() != myid) {
           logDebug("Checking device " + dni.toString())
              
           setup = dni.getSetup()   
              
           my_dni = setup.my_dni    
           homeID = setup.homeID
           name = setup.name
           type = setup.type
           token = setup.token
           devId = setup.devId

           log.info "Device's Service Setup(Hubitat dni=${my_dni}, Home ID=${homeID}, Name=${name}, Type=${type}, Token=${token}, Device Id=${devId})"          
              
                          
        if (devId?.contains(sensorid)) {
           logDebug("Located device to be bound in parent: ${dni} ${devId}") 
           bound = dni
        }
        }
      } catch (Exception e) {         
      }    
    }    
  }
    
  if (!bound) { 
    log.error "Unable to bind device: Could not locate device ID '$sensorid'"
    return
  }       
  
  logDebug ("Attempting to bind device '$bound'")  
    
  state.bound_dni = my_dni  
  state.bound_homeID = homeID
  state.bound_name = name
  state.bound_type = type
  state.bound_token = token
  state.bound_devId = devId
     
  establish_MQTT_connection(state.bound_homeID, state.bound_devId)
   
  if (state.API == "connected") {  
     msg = "Controller bound to device '$bound'"
     lastResponse(msg)
     logDebug(msg) 
  } else {
     def msg = "Controller failed to bind to device '$bound'" 
     lastResponse(msg)
     log.error msg
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

def check_MQTT_Connection() {
  def MQTT = interfaces.mqtt.isConnected()  
  logDebug("MQTT connection is ${MQTT}")  
  if (MQTT) {  
     rememberState("API", "connected")     
  } else {    
     establish_MQTT_connection(state.homeID,state.my_dni)                                               //Establish MQTT connection to YoLink API for this device
     if (state.bound_devId != null) {establish_MQTT_connection(state.bound_homeID, state.bound_devId)}  //Establish MQTT connection to YoLink API for bound device
  }
}    

def establish_MQTT_connection(homeID, devId) {
    parent.refreshAuthToken()
    def authToken = parent.AuthToken() 
      
    def MQTT = "disconnected"
    
    def topic = "yl-home/${homeID}/${devId}/report"
    
    try {  	
        mqtt_ID =  "${mqtt_ID}_${homeID}"
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
            
    if (devId == state.bound_devId) {  // Only handle if message is from bound device
        logDebug("processStateData(${payload})")
        
        def child = parent.getChildDevice(state.my_dni)
        def name = child.getLabel()                
        def event = object.event.replace("DoorSensor.","")
        logDebug("Received Message Type: ${event} for: $name")
        
        switch(event) {
		case "Alert":            
			def devstate = object.data.state           
            def battery = parent.batterylevel(object.data.battery)    // Value = 0-4    
            def firmware = object.data.version    
            def signal = object.data.loraInfo.signal  
            
            def stateChangedAt = object.data.stateChangedAt
                         
            stateChangedAt = formatTimestamp(stateChangedAt)
                
            def contact = devstate 
             
            rememberState("contact",contact)
            rememberState("battery",battery)
            rememberState("firmware",firmware)
            rememberState("signal",signal)      
            rememberState("stateChangedAt",stateChangedAt)
            
            lastResponse("Garage door is ${contact}")
		    break;    
            
      case "Report":
            def devstate = object.data.state          
            def battery = parent.batterylevel(object.data.battery)    // Value = 0-4    
            def firmware = object.data.version    
            def openRemindDelay = object.data.openRemindDelay   
            def alertInterval = object.data.alertInterval                             
            def signal = object.data.loraInfo.signal  
                                        
            def contact = devstate 
            
            rememberState("contact",contact)
            rememberState("battery",battery)
            rememberState("delay",delay)               
            rememberState("firmware",firmware)
            rememberState("openRemindDelay",openRemindDelay) 
            rememberState("alertInterval",alertInterval)
            rememberState("signal",signal)      
		    break;              
            
        case "setOpenRemind":    
            def openRemindDelay = object.data.openRemindDelay   
            def alertInterval = object.data.alertInterval                             
            def signal = object.data.loraInfo.signal  
    
            rememberState("openRemindDelay",openRemindDelay) 
            rememberState("alertInterval",alertInterval)
            rememberState("signal",signal)                  
            break;	    
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
}

def push() {
   def request = [:]    
   request.put("method", "${state.type}.toggle")   
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("push(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {        
                  def stateChangedAt = object.data.stateChangedAt
                  def signal = object.data.loraInfo.signal       
                
                  stateChangedAt = formatTimestamp(stateChangedAt)
                
                  logDebug("Parsed: stateChangedAt=$stateChangedAt, Signal=$signal")
                  rememberState("stateChangedAt",stateChangedAt)
                  rememberState("signal",signal)
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
            logDebug("push() failed")	
            lastResponse("push() failed")     
        }     		
	} catch (e) {	
        log.error "push() exception: $e"
        lastResponse("Error ${e}")      
	} 
}   

def unbind() {
    log.warn "Unbinding contact sensor: ${state.bound_name} "
    state.remove("bound_dni")  
    state.remove("bound_homeID")  
    state.remove("bound_name")  
    state.remove("bound_type")  
    state.remove("bound_token")  
    state.remove("bound_devId") 
    state.remove("contact")
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
    state.remove("API")
    state.remove("online")  
    state.remove("signal")     
    state.remove("contact")
    state.remove("stateChangedAt")
    state.remove("LastResponse")  
    state.remove("sensors")  
    
    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a" 
    
    unbind()
        
    interfaces.mqtt.disconnect()      // Guarantee we're disconnected  
    connect()                         // Reconnect to API Cloud  
   
    scan()
    
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