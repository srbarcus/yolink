/***
 *  YoLink™ MQTT Listener Device
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
 *  2.0.1: Added temperatureScale(value) for service app compatabilty
 *  2.0.2: Correct MQTT message error reporting
 *  2.0.3: Reduce time returning from Poll()
 *  2.0.4: Added "driver" attribute and isSetup() for diagnostics
 *  2.0.5: Support diagnostics, correct various errors, make singleThreaded
 *  2.0.6: Copyright update and UI formatting
 *  2.0.7: Updated driver version on poll
 *  2.0.8: Improve connectivity to YoLink cloud
 *  2.0.9: Support "setDeviceToken()"
 *         - Update copyright
 *  2.0.10: Added cleanSession to MQTT.Connect, improve connectivity
 *         - Insure topic is for current Home ID (Mismatch happening for some users)
 *         - Unsubscribe from unexpected Home ID (Mismatch happening for some users)
 *         - Remove state.message
 *  2.0.11: Housekeeping
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.11"}
def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ MQTT Listener v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink MQTT Listener Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"				
        capability "Initialize"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]]
        command "connect"                       // Attempt to establish MQTT connection 
        command "reset"
        
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"          
        attribute "firmware", "String"
        attribute "lastPoll", "String"
        attribute "MQTT", "String" 
        attribute "lastResponse", "String"
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

// Called by YoLinkService App on initial creation of a child Device
void ServiceSetup(Hubitat_dni,homeID,devname,devtype,devtoken,devId) {  //dev.ServiceSetup(Hubitat_dni,state.homeID,devname,devtype,devtoken,devId) 
	settings.trace=true	
    
    state.my_dni = Hubitat_dni      
    state.homeID = homeID    
    state.name = devname
    state.type = devtype
    state.token = devtoken
    rememberState("devId", devId)   
    	
    log.debug "ServiceSetup(Hubitat dni=${state.my_dni}, Home ID=${state.homeID}, Name=${state.name}, Type=${state.type}, Token=${state.token}, Device Id=${state.devId})"
	    
    connect()      //Establish MQTT connection to YoLink API 
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
   log.trace "Device initializing. Establishing MQTT connection to YoLink API" 
   state.remove("message")   // Remove old state. DELETE IN FUTURE VERSIONS
   state.remove("errant")   
   connect()           //Establish MQTT connection to YoLink API 
 }

def uninstalled() {
   interfaces.mqtt.disconnect() // Guarantee we're disconnected 
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def poll() {
  def MQTT = interfaces.mqtt.isConnected()  
  rememberState("online",MQTT) 
  rememberState("driver", clientVersion())     
  logDebug("MQTT connection is ${MQTT}")  
  if (MQTT) {  
     rememberState("MQTT", "connected")     
  } else {   
     runIn(1,connect) //Establish MQTT connection to YoLink API
  }
 }

def pollDevice(delay=1) {
    rememberState("driver", clientVersion())
    poll()
    def date = new Date()
    sendEvent(name:"lastPoll", value: date.format("MM/dd/yyyy hh:mm:ss a"), isStateChange:true)
 }

def temperatureScale(value) {}

def debug(value) { 
   rememberState("debug",value)
   if (value == "true") {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

def connect() {  
    unsubscribe_MQTT()
    
    interfaces.mqtt.disconnect()              // Guarantee we're disconnected  
    
    def mqtt_ID = location.hub.zigbeeId
    
    def date = new Date()
    mqtt_ID = mqtt_ID + date.format("YYdddhhmmss")
    
    establish_MQTT_connection(mqtt_ID)
 }

//>>>>>>>>>>>>>>>>>>>><<<<<<<<<<<<<<<<<<</
//>>>>>>>>>>>> MQTT ROUTINES <<<<<<<<<<<</
//>>>>>>>>>>>>>>>>>>>><<<<<<<<<<<<<<<<<<</

def establish_MQTT_connection(mqtt_ID) {
    parent.refreshAuthToken()
    def authToken = parent.AuthToken() 
      
    def MQTT = "disconnected"
    
    def topic = "yl-home/${state.homeID}/+/+"
   
    try {  	
        
       mqtt_ID =  "${mqtt_ID}_${state.homeID}"
        
       logDebug("Connecting to MQTT with ID '${mqtt_ID}', Topic:'${topic}, Token:'${authToken}")          
       interfaces.mqtt.connect("tcp://api.yosmart.com:8003","${mqtt_ID}",authToken,null,cleanSession: 1)            
        
       logDebug("Subscribing to MQTT topic '${topic}'") 
       interfaces.mqtt.subscribe("${topic}", 1) 
         
       MQTT = "connected" 
        
       logDebug("MQTT connection to YoLink successful")
		
	  } catch (e) {	
          log.error ("establish_MQTT_connection() Exception: $e",)			
    }
        
    rememberState("MQTT", MQTT)    
    rememberState("online",interfaces.mqtt.isConnected())  
    lastResponse("API MQTT ${MQTT}")  
}    

def unsubscribe_MQTT() {
    def topic = "yl-home/${state.homeID}/+/+"
   
    try {  	
       logDebug("Unsubscribing to MQTT topic '${topic}'") 
       interfaces.mqtt.unsubscribe("${topic}") 
               
       logDebug("Unsubscribing to MQTT topic '${topic}' successful")
       lastResponse("Unsubscribing to MQTT topic '${topic}' successful")  
		
	  } catch (e) {	
          log.error ("unsubscribe_MQTT(${topic}) Exception: $e",)
          lastResponse("unsubscribe_MQTT(${topic}) Exception: $e")  
    }
        
    rememberState("online",interfaces.mqtt.isConnected())  
}    

def mqttClientStatus(String message) {                          
    logDebug("mqttClientStatus(${message})")

    if (message.startsWith("Error:")) {
        log.error "MQTT Error: ${message}"

        try {
            log.info "Disconnecting from MQTT"    
            interfaces.mqtt.disconnect() // Guarantee we're disconnected            
            if (state.MQTT != "disconnected") {sendEvent(name:"MQTT", value: "disconnected", isStateChange:true)}   
            state.MQTT = "disconnected"
        }
        catch (e) {
        } 
    }
}

def parse(message) {  //CALLED BY MQTT       
    def topic = interfaces.mqtt.parseMessage(message)
    logDebug("Received MQTT topic: ${topic}")
    
    def msgHome = topic.topic
    msgHome = msgHome.substring(msgHome.indexOf("yl-home/") + 8, msgHome.indexOf("/",msgHome.indexOf("yl-home/") + 8))
    logDebug("Message Home ID: ${msgHome}, Monitoring Home ID: ${state.homeID}")
    
    def HomeOK = (msgHome == state.homeID)
            
    if (HomeOK) {
        logDebug("Passing message to parent for distribution.")
   	 	def device = parent.passMQTT(topic)
    
    	if (device != null) {
        	logDebug("MQTT message passed successfully to device ${device}")
    	} else {
	        log.error "MQTT message ${topic} - Failed passing to device driver"
	    }
    } else {  //Unsubscribe from non-monitored home        
        if ((state.errant?.indexOf(msgHome) == -1) || (state.errant == null)) {
            log.error("Message Home ID: ${msgHome} doesn't match current Home ID: ${state.homeID}")
            
        	topic = "yl-home/${msgHome}/+/+"
        	try {  	
    			log.trace("Unsubscribing from errant MQTT topic '${topic}'") 
       			interfaces.mqtt.unsubscribe("${topic}") 
       	   		log.trace("Unsubscribe successful")		
	  		} catch (e) {	
	        	log.error("Unsubscribe Exception: $e")
    		}
	        
            if (state.errant == null) {state.errant = ""}

            state.errant = state.errant.concat(" ${msgHome}")  // Track errant houses
        } else {
            logDebug("MQTT Unsubscribe was already attempted")
        }
    }  
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

def reset(){    
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("MQTT")    
    state.remove("firmware")
    state.remove("lastResponse")     
    state.remove("errant")   
    
    rememberState("firmware","N/A")
    
    connect()
    
    log.warn "Device reset to default values"
}

def lastResponse(value) {
   sendEvent(name:"lastResponse", value: "$value", isStateChange:true)   
}

def logDebug(msg) {
   if (state.debug == "true") {log.debug msg}
}