/***
 *  YoLink™ Flex Fob (YS3604-UC), Flex Fob V2 (YS3604-UC), On/Off Fob (YS3605-UC), Dimmer Fob (YS3606-UC), Siren Fob (YS3607-UC)
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
 *  1.0.1: Fixed errors in poll()
 *  1.0.2: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *  1.0.3: Fix syncing of Temperature scale with YoLink™ Device Service app
 *  1.0.4: Fix donation URL
 *  1.0.5: Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions 
 *  2.0.1: - Fix problem with multiple actions on same button being ignored: Added Double-Tap and Tap Delay attributes
 *         - Clean up code
 *         - Remove temperature as it never changed
 *  2.0.2: Support diagnostics, correct various errors, make singleThreaded
 *  2.0.3: Support event "SmartRemoter.Report"
 *         - Add "DoubleTapableButton" capability
 *         - Add unit value to battery 
 *         - Add formatted "signal" attribute as rssi & " dBm"
 *         - Add capability "SignalStrength"  
 *  2.0.4: Support for Flex Fob V2, On/Off Fob, Dimmer Fob, Siren Fob
 *  2.0.5: Default to Flex Fob
 *  2.0.6: Report buttons as numeric value, report rssi as number, fix multiple events not being reported
 *         - Switch to Preferences vs Commands. Display Perferences based on Fob Model chosen
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.6"}
def copyright() {return "<br>© 2022, 2023 Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}
def redTitle(text) 	{ return '<span style="color:#ff0000">'+text+'</span>'}

preferences {
    section("Settings") {
        input "fobModel", "enum", title: bold("Fob Device Model"), description:redTitle("Must be set to the correct Fob model for this device to operate correctly."),
              options: ["Flex Fob","On/Off Fob", "Dimmer Fob", "Siren Fob"], required: true, defaultValue: "Flex Fob"
        }
    
    if (fobModel?.contains("Flex Fob")) {        
    section("Flex Fob Options") {
        input "allowRepeat", "enum", title: bold("Allow Repetitive Button Presses"), description:"Allow pressing or holding of the same button without using a different button first.", options:["True", "False"], required: true, defaultValue: "False"
        input "allowDoubleTap", "enum", title: bold("Allow Double-Tap"), description:"Allow pressing of the same button within the 'Double-Tap Interval' to be recognized as a Double-Tap event. " + 
              redTitle("Note: Double-tap is a software event, not hardware. As such, when a button is first pressed it will always cause a 'Press' event. Program rules accordingly."), 
              options:["True", "False"], required: true, defaultValue: "False"
        input "tapInterval", "enum", title: bold("Double-Tap Interval"), description:"Maximum number of seconds between pressing of the same button to be considered as a double-tap if double-tapping is enabled.",
              options:["0", "0.5", "1", "2", "5"], required: true, defaultValue: "0"
        }                
    }

    if (fobModel?.contains("Dimmer Fob")) {
    section("Dimmer Fob Options") {
        input "allowRepeat", "enum", title: bold("Enable Multifunctional 'On' Button"), description:"Allow repetitive pressing of the On button to switch dimmer level from current%, to 100%, and back to current%.", options:["True", "False"], required: true, defaultValue: "False"
        input "dimAtOn", "enum", title: bold("Dimmed On"), description:"Set at previously dimmed level when turned on, otherwise set at 100%.", options:["True", "False"], required: true, defaultValue: "True"
        input "levelChange", "enum", title: bold("Amount of Level Change"), description:"Amount of level change caused by Up and Down buttons. " + redTitle("Only works if device is bound to a Dimmer device."),
              options:["5", "10", "15", "20", "25"], required: true, defaultValue: "10"  
        }                
    }

    section("Driver Details") {
        input title: bold("Driver Version"), description: "YoLink™ Flex Fob (YS3604-UC), Flex Fob V2 (YS3604-UC), On/Off Fob (YS3605-UC), Dimmer Fob (YS3606-UC), Siren Fob (YS3607-UC) v${clientVersion()}${copyright()}", type: "paragraph", element: "paragraph"
        input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", type: "paragraph", element: "paragraph"
        }
}

metadata {
    definition (name: "YoLink SmartRemoter Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {
        capability "Initialize"
        capability "Polling"	
        capability "Battery"
        capability "SignalStrength"             //rssi   
        capability "PushableButton"
        capability "SwitchLevel"
        capability "Switch"
     
        capability "DoubleTapableButton"
        capability "HoldableButton"    
                
        command "debug", [[name:"Enable debugging",type:"ENUM", description:"Display debugging messages", constraints:[true, false]]] 
        command "connect"                       // Attempt to establish MQTT connection
        command "reset"
        command "scan"    
        command "bind", [[name:"bind",type:"STRING", description:"Device ID (devId) of Dimmer device to be bound to this controller. See 'sensors' under 'State Variables' below and copy & paste a Device here."]] 
        command "setLevel", [[name:"Brightness Level",type:"NUMBER", description:"Brightness level (0-100)"]]
                         
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"  
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "lastResponse", "String" 
        attribute "fobModel", "String"         
        attribute "reportAt", "String"      
        attribute "action", "String"
        attribute "beep", "String"
        attribute "defaultLevel", "Number"
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
    
    setModel("Flex Fob")          
    
    log.warn "All Fobs models appear the same to Hubitat. This device has been set to the default Fob model of 'Flex Fob'. If this is incorrect, edit the Device definition and select the correct model otherwise the device will not behave correctly."         
    
	log.info "ServiceSetup(Hubitat dni=${state.my_dni}, Home ID=${state.homeID}, Name=${state.name}, Type=${state.type}, Token=${state.token}, Device Id=${state.devId}, Model=${state.fobModel})"	 
    
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
    rememberState("numberOfButtons",4)         
    
    log.info "Settings: $settings"
    log.info "Device Installed"
 }

def updated() {
    log.info "Device Updated"
    
    rememberState("driver", clientVersion())
    
    if (state.fobmodel != fobModel) {
        if (state.fobmodel == "Dimmer Fob") {
            rememberState("levelChange", 10, "%")   
        } else {
            state.remove("levelChange") 
        }    
    }    
    
    setModel(fobModel)
    
    if (levelChange != null) {rememberState("levelChange", levelChange.toInteger())}
    
    log.info "Settings: $settings"
 }

def initialize() {
   connect()
}

def setModel(model) {
    rememberState("fobModel", model)
    
    device.updateSetting("ignoreButton",[value:0,type:"number"])
    device.updateSetting("DoubleButton",[value:0,type:"number"])
    
    log.info "Settings: $settings"
    log.info "Fob Model set to $model"  
 }

def uninstalled() {
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def poll(force=null) {
   pollDevice()
}

def pollDevice(delay=1) {
   if (boundToDevice()) {
     runIn(delay,check_MQTT_Connection)  
   }
   def date = new Date()
   sendEvent(name:"lastPoll", value: date.format("MM/dd/yyyy hh:mm:ss a"), isStateChange:true) 
 }

def tapDelay(value) {    
   logDebug("tapDelay(${value})")  
   rememberState("tapDelay",value)                
 }

def allowDoubleTap(value) {    
   logDebug("allowDoubleTap(${value})")  
   if (state.fobModel == "Flex Fob") { 
     rememberState("allowDoubleTap",value)
   } else {
     log.warn "Double-tapping is only allowed on a Flex Fob"  
     rememberState("allowDoubleTap",false)
   }    
 }

def on() {
   if (state.fobModel != "Dimmer Fob") {
    log.warn "Turn device on is only applicable to a Dimmer Flex Fob"
    return  
   } 
    
   logDebug("on(): Switch(${state.switch}), Start Dimmed(${dimAtOn})")   
   
   if (boundToDevice(true)) { 
       def level
       if (state.switch == "on") {
          def oldlevel = dimmerLevel()
          if (oldlevel < 100) {
             level = 100 
          } else {
             level = state.defaultLevel
          }
       } else {
           if (dimAtOn != "True") {
             level = 100
           } else {
             level = state.defaultLevel
          }    
       }  
    
       setLevel(level) 
    
       boundDeviceCmd("on") 
   }    
 }

def off() {    
   if (state.fobModel != "Dimmer Fob") {
    log.warn "Turn device off is only applicable to a Dimmer Flex Fob"
    return  
   } 
   if (boundToDevice(true)) {boundDeviceCmd("off")}
 }

def boundDeviceCmd(command) { 
   def dev 
   if (boundToDevice(true)) {
     dev = parent.getChildDevice(state.bound_dni)  
     if (dev==null) {
       log.error "Unable to locate child device with ID '${state.bound_dni}'"
       return
     } 
   } else {          
     return
   }
    
   logDebug("Turning ${command} bound ${dev.name}")
   switch(command) {
      case "on":     
        dev.on()
    	break;     
      case "off":     
        dev.off()
    	break;
   }   
   rememberState("switch",command) 
}    

def push(button) {    
   pushAction(button, "Pushed ${button}")
 }

def pushAction(button, func) {
   button = button.toBigDecimal() 
   logDebug("Pushed(${button})")
   rememberState("pushed",button,null,true)              
   rememberState("action",func,null,true)
   state.lastTap = now() 
   state.lastButton = button 
 }

def hold(button) {   
   logDebug("hold(${button})")
   if (state.fobModel == "Flex Fob") { 
     rememberState("held",button,null,true)           
     rememberState("action","Held $button",null,true)
   } else {
     log.warn "Button holding is only supported on a Flex Fob"  
   }     
 }

boolean doubleTap(button) {  
    if (state.fobModel != "Flex Fob") { 
     log.warn "Double-tapping is only supported on a Flex Fob"
     return false   
   }      
   if (honorTap(button)) {   
     logDebug("doubleTap(${button})")     
     rememberState("doubleTapped",button,null,true)         
     rememberState("action","DoubleTapped $button",null,true)
     return true
   } else {    
     return false
   }     
 }

boolean honorTap(button) {
    boolean rc = false
    logDebug("honorTap($button) Double-Tap button = " + settings.DoubleButton) 
    
    def secsPassed = ((now()/1000) - (state.lastTap/1000))
    def secsDelay = settings.tapInterval    
    if ((secsPassed.toBigDecimal() < secsDelay.toBigDecimal()) || (secsDelay.toBigDecimal()==0)) {
       rc = true
    }
    
    logDebug("Seconds between last press=$secsPassed, Maximum=$secsDelay, Allow=$rc") 
    
    return rc
}   

def connect() {
   if (state.bound_devId != null) {establish_MQTT_connection(bound_dni, state.bound_homeID, state.bound_devId)}  //Establish MQTT connection to YoLink API for bound device
 }

def temperatureScale(value) {}

def scan() {
  if (state.fobModel != "Dimmer Fob") {
    log.warn "Scanning is only applicable to a Dimmer Flex Fob"
    return  
  }
    
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
         
       logDebug("${id} is a " + setup?.type)   
         
       if (setup?.type == "Dimmer") {  
         def name = setup?.name
         def devId = setup?.devId         
              
         id = devId + "=" + name
           
         logDebug("Found Dimmer Device: ${id}") 
           
         dev = dev + "<br>" + id
       }    
     }          
  }    
    
  state.dimmers = dev
}

def setLevel(newlevel) {
  if (state.fobModel != "Dimmer Fob") { 
     log.warn "Level setting is only supported on a Dimmer Fob."
     return  
  } else { 
     logDebug("setLevel(${newlevel})" ) 
  }    
      
  def dev 
  if (boundToDevice(true)) {
     dev = parent.getChildDevice(state.bound_dni)  
     if (dev==null) {
       log.error "Service unable to locate child device with ID '${state.bound_dni}'"
       return
     }
     
     int level = dimmerLevel()
      
     if (newlevel > 100) {newlevel = 100}
     if (newlevel < 0) {newlevel = 0}     
 
     if (level != newlevel) { 
        logDebug("Current level of ${dev.name} is ${level}% requesting ${newlevel}%. Setting bound device.")   
        dev.setLevel(newlevel)
        dev.getDevicestate() 
     }
     
     rememberState("level",dimmerLevel()) 
      
     if (newlevel != 100) {rememberState("defaultLevel", newlevel)}
  }    
}      

def dimmerLevel() {
   dev = parent.getChildDevice(state.bound_dni)  
   if (dev==null) {
     log.error "Service unable to locate child device with ID '${state.bound_dni}'"
     return
   }
     
   int level = dev.currentValue("level")
   return level
}    

def bind(sensorid) {
  if (state.fobModel != "Dimmer Fob") { 
     log.warn "Device Binding is only supported on a Dimmer Fob."
     if (boundToDevice()) {  
          unbind()                          // Unbind current switch sensor
          interfaces.mqtt.disconnect()      // Guarantee we're disconnected
     }    
     return  
   }      
      
  if (!sensorid) {  
    if (state.bound_devId == null) {
         log.error "Device ID to be bound was not specified"
    } else {    
         unbind()                          // Unbind current switch sensor
         interfaces.mqtt.disconnect()      // Guarantee we're disconnected
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
          log.error ("establish_MQTT_connection() Exception: $e")	 
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
     
  establish_MQTT_connection(state.bound_dni, state.bound_homeID, state.bound_devId)
   
  if (state.API == "connected") {  
     msg = "Fob bound to device '$bound'"
     lastResponse(msg)
     log.info msg  
  } else {
     def msg = "Fob failed to bind to device '$bound'" 
     lastResponse(msg)
     log.error msg
  }    
}

def unbind() {
    log.warn "Unbinding dimmer: ${state.bound_name} "
    lastResponse("Dimmer ${state.bound_name} unbound")
    state.remove("bound_dni")  
    state.remove("bound_homeID")  
    state.remove("bound_name")  
    state.remove("bound_type")  
    state.remove("bound_token")  
    state.remove("bound_devId") 
    state.remove("contact")
    
    interfaces.mqtt.disconnect()      // Guarantee we're disconnected  
    rememberState("API", "not bound")  
}

def boundToDevice(err=null) {
    def bound = (state.bound_devId != null)
    if (!bound && err != null) {
       log.warn "You must bind a Dimmer device to the Fob to control it using the Fob." 
    }    
    
    return bound
}

def check_MQTT_Connection() {
  if (boundToDevice()) {  
      def MQTT = interfaces.mqtt.isConnected()  
      logDebug("MQTT connection is ${MQTT}")  
      if (MQTT) {  
         rememberState("API", "connected")     
      } else {    
         connect()
      }
  }    
}    

def establish_MQTT_connection(mqtt_ID, homeID, devId) {
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
    } finally {    
        rememberState("API", MQTT)    
        lastResponse("API MQTT ${MQTT}")    
    }
}    

def mqttClientStatus(String message) {                          
    logDebug("mqttClientStatus(${message})")

    if (message.startsWith("Error:")) {
        log.error "MQTT ${message}"

        try {
            log.warn "Disconnecting from MQTT"    
            interfaces.mqtt.disconnect()           // Guarantee we're disconnected            
            rememberState("API","disconnected") 
        }
        catch (e) {
            log.error ("mqttClientStatus() Exception: $e")	
        } 
    }
}

def parse(message) {        
    logDebug("parse(${message})")
    boolean skipError = false
    
    try {     
		  def topic = interfaces.mqtt.parseMessage(message)                 // Internal MQTT parsing of bound device
          def object = new JsonSlurper().parseText(topic.payload)
          logDebug("Processing Bound Device MQTT message: $object")
          skipError = true
          parseTopic(object)
	    } catch (Exception e) {	
            if (!skipError) {
              logDebug("Processing YoLink MQTT message: $message")
              processStateData(message.payload) 
            } else {
              log.error "Error $e"
            }  
	    }  
}

def parseTopic(object) {
    logDebug("parseTopic(${object})")
    
    rememberState("online","true")     
       
    def devId = object.deviceId   
            
    if (devId == state.bound_devId) {  // Only handle if message is from bound device
        def child = parent.getChildDevice(state.bound_dni)
        def name = child.getLabel()                
        def event = object.event.replace("Dimmer.","")
        logDebug("Received Message Type: ${event} for bound device: $name")           
        
        switch(event) {
        case "setSchedules":
        case "getSchedules":
        case "setDelay":     
        case "setDeviceAttributes":            
		case "setInitState":
		case "setTimeZone":
  			break;
        
        case "StatusChange":    
        case "setState":
        case "getState":           
		case "Report":
            def swState = parent.relayState(object.data.state)   
            def level = object.data.brightness
    
            logDebug("Parsed: Switch=$swState, level=$level")
            
            rememberState("switch",swState)
            rememberState("level", level, "%") 
			break;  
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
}

def void processStateData(payload) {
    logDebug("processStateData(${payload})")
    rememberState("online","true")
    
    def object = new JsonSlurper().parseText(payload)    
    def devId = object.deviceId   
    
    logDebug("My ID ${devId}. Message for ${state.devId}")
            
    if (devId == state.devId) {  // Only handle if message is for me
        def child = parent.getChildDevice(state.my_dni)
        def name = child.getLabel()                
        def event = object.event.replace("${state.type}.","")
        logDebug("Received Message Type: ${event} for: $name")       
        
        switch(event) {
         case "setSettings":    
            def beep = object.data.beep            
            def rssi = object.data.loraInfo.signal  
    
            logDebug("Parsed: Beep=$beep, RSSI=$rssi")
                
            rememberState("beep", beep)
            fmtSignal(rssi) 
		    break;      
            
		case "StatusChange": //ata":{"event":{"keyMask":2},"battery":4,"version":"0601","devTemperature":24,"beep":true,"loraInfo":{"signal":-22,"gatewayId":"d88b4c160400012d","gateways":2}}
        case "Report":    
            def button = object.data.event.keyMask.toInteger() 
            def action = object.data.event.type 
            if (action==null) {action="Press"}
            def battery = parent.batterylevel(object.data.battery)
            def firmware = object.data.version.toUpperCase() 
            def beep = object.data.beep
            def rssi = object.data.loraInfo.signal  
    
            logDebug("Parsed: DeviceId=$devId, Button=$button, Action=$action, Battery=$battery, Firmware=$firmware, RSSI=$rssi")
            
            switch(state.fobModel) {
              case "Flex Fob":
                 switch(button) {
		            case 4:                      
                        button = 3                    
                        break;
                    case 8:          
                        button = 4                    
                        break;                  
	             }
                
                 logDebug("Flex Fob event: Button $button, Action: $action, Last Button: " + ignoreButton + ", Allow DoubleTap: " + settings.allowDoubleTap + " on button: " + settings.DoubleButton)
                
                 if ((settings.allowDoubleTap == "True") && (action == "Press")) {
                   if (settings.DoubleButton == 0) {                     
                      device.updateSetting("DoubleButton",[value:button,type:"number"])
                      state.lastTap = now() 
                      state.lastButton = button  
                   } else {    
                      if (settings.DoubleButton == button) {                          
                          if (doubleTap(button)) {                            
                            state.lastTap = now() 
                            state.lastButton = button    
                            return
                          }
                      } else {    
                          device.updateSetting("DoubleButton",[value:button,type:"number"])  
                      }
                   }    
                 } 
                
                 if ((button == ignoreButton) && (allowRepeat != "True")) {
                   logDebug("Button $button ignored because 'Allow Repeat' parameter is not set to 'True' on the device.")
                   state.lastTap = now() 
                   state.lastButton = button   
                   return
                 }
                     
                 switch(action) {
                    case "Press":   
                        pushAction(button,"Pushed $button")
                        break;
                    case "LongPress":    
                        hold(button)
                        break;                  
	             }
                
                 if (allowRepeat != "True") {
                    device.updateSetting("ignoreButton",[value:button,type:"number"])
                 } else {
                    device.updateSetting("ignoreButton",[value:0,type:"number"])
                 }    
                 break; 
                
              case "On/Off Fob":
                 def func
                 switch(button) {
		            case 1:                      
                       switch(action) {
                           case "Press":   
                                func = "Aon"
                                break;
                           case "LongPress": 
                                button = 2 
                                func = "Aoff"
                                break;                  
	                       }
                        break;
                    case 2:          
                       switch(action) {
                           case "Press":   
                                button = 3 
                                func = "Bon"
                                break;
                           case "LongPress": 
                                button = 4 
                                func = "Boff"
                                break;                  
	                       }
	             }
                 logDebug("On/Off Fob event: Button $button, Action: $action")
                 pushAction(button,func)
                 break; 
                
              case "Dimmer Fob": 
                 def func
                 switch(button) {
		            case 4:                      
                        button = 3                    
                        break;
                    case 8:          
                        button = 4                    
                        break;                  
	             }
                
                if (button == ignoreButton) {   
                   if (((button == 2) || (button == 3)) || (allowRepeat=="True")) {//Only allow multiple pushes on Up and Down buttons unless overridden
                     logDebug("Subsequent press on button ${button}")
                     device.updateSetting("ignoreButton",[value:0,type:"number"])
                   } else {    
                     logDebug("Press on button ${button} ignored")
                     return                       
                   }    
                 }    

                 switch(button) {
		            case 1:                      
                        func = "On"
                        if (boundToDevice()) {on()}
                        break;
                    case 2:          
                        func = "Up"
                        if (boundToDevice()) {
                          def level = dimmerLevel()
                          level = level + state.levelChange
                          setLevel(level)
                        }
                        break;                  
                    case 3:          
                        func = "Down"
                        if (boundToDevice()) {
                          def level = dimmerLevel()
                          level = level + (state.levelChange * -1)
                          setLevel(level)
                        }                       
                        break;                  
                    case 4:         
                        func = "Off"
                        if (boundToDevice()) {off()}
                        break;                   
	             }
                
                 logDebug("Dimmer Fob event: Button $button, Action: $action")
                
                 if (button == ignoreButton) {
                   return                       
                 }    
                  
                 pushAction(button,func)
                
                 device.updateSetting("ignoreButton",[value:button,type:"number"])
                        
                 break;                
                
              case "Siren Fob":
                 def func
                 switch(button) {
		            case 1:                      
                        func = "Siren"
                        break;
                    case 2:          
                        func = "Silence"          
                        break;                  
                    case 3:          
                        func = "Unlock"
                        break;                  
                    case 4:         
                        func = "Lock"      
                        break;                   
	             }
                 logDebug("Siren Fob event: Button $button, Action: $action")
                 pushAction(button,func)
                 break; 
            }
                
            rememberState("battery", battery, "%")
            rememberState("firmware",firmware)  
            rememberState("beep",beep)  
            fmtSignal(rssi) 
		    break;           
		                
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
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
    
	boolean rc=false
    
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
   logDebug("parseDevice(): ${object}")     
   def devId = object.data.deviceId  
   def reportAt = object.data.reportAt
    
   def online = object.data.online
    
   def battery = object.data.state.battery?:null  
   if (battery) {battery = parent.batterylevel(object.data.state.battery)}
    
   def firmware = object.data.state.version.toUpperCase()   
    
   logDebug("Parsed: DeviceId=$devId, Battery=$battery, Report At=$reportAt, Firmware=$firmware, Online=$online")   
                
   rememberState("online", "true")
   rememberState("battery", battery, "%")
   rememberState("reportAt", reportAt)  
   rememberState("firmware", firmware)   
}   
                           
def reset(){          
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("firmware")
    state.remove("rssi")
    state.remove("signal")
    state.remove("battery")  
    state.remove("pushed")
    state.remove("held")
    state.remove("doubleTapped")
    state.remove("action")
    state.remove("beep")
    state.remove("defaultLevel")
    
    state.lastButton = 0    

    poll(true)    
    
    log.warn "Device reset to default values"
}

def lastResponse(value) {
   sendEvent(name:"lastResponse", value: "$value", isStateChange:true)   
}

def rememberState(name, value, unit=null, boolean force=false) {
   if ((state."$name" != value) || (force)) {    
     state."$name" = value       
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
  if (state.debug == "true") {log.debug msg}
}

def fmtSignal(rssi) {
   rememberState("rssi",rssi.toInteger()) 
   rememberState("signal",rssi.plus(" dBm")) 
}    