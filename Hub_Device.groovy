/***
 *  YoLink™ Hub (YS1603-UC)
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
 *  1.0.1: Fixed errors in poll()
 *  1.0.2: Add constraints to debug command
 *  1.0.3: def temperatureScale()
 *  1.0.4: Fix donation URL
 *  2.0.0: Sync version number with reengineered app due to new YoLink service restrictions
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.0"}

preferences {
    input title: "Driver Version", description: "YoLink™ Hub (YS1603-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

/* As of 03-06-2022, was not supported by API  
preferences {
	input("SSID", "text", title: "WiFi SSID", description:
		"The SSID of your wireless network", required: true)
	input("WiFiPassword", "text", title: "WiFi SSID", description:
		"The password of the wireless network with the SSID specified above", required: true)
}
*/

metadata {
    definition (name: "YoLink Hub Device", namespace: "srbarcus", author: "Steven Barcus") {     		
		capability "Polling"						
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]] 
        command "reset" 
        
      //command "setWiFi"                       // As of 03-06-2022, was not supported by API    
        
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "signal", "String" 
        attribute "lastResponse", "String"
      //attribute "reportAt", "String"    
        
        attribute "wifi_ssid", "String"  
        attribute "wifi_enabled", "String"  
        attribute "wifi_ip", "String"  
        attribute "wifi_gateway", "String"  
        attribute "wifi_mask", "String"
        attribute "ethernet_enabled", "String"
        attribute "ethernet_ip", "String"
        attribute "ethernet_gateway", "String"
        attribute "ethernet_mask", "String"        
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
 //interfaces.mqtt.disconnect() // Guarantee we're disconnected Removed v1.0.7
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

def temperatureScale(value) {}

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
            logDebug("getDevicestate(): pollAPI() response: ${object}")                          
         
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
    def firmware = object.data.version
    def wifi_ssid = object.data.wifi.ssid                    
    def wifi_enabled = object.data.wifi.enable                             
    def wifi_ip = object.data.wifi.ip  
    def wifi_gateway = object.data.wifi.gateway 
    def wifi_mask = object.data.wifi.mask       
    def ethernet_enabled = object.data.eth.enable
    def ethernet_ip = object.data.eth.ip
    def ethernet_gateway = object.data.eth.gateway
    def ethernet_mask = object.data.eth.mask                        
            
    logDebug("Hub Firmware Version (${firmware}), " +
             "WiFi SSID(${wifi_ssid}), " +
             "WiFi Enabled(${wifi_enabled}), " +
             "WiFi IP(${wifi_ip}), " +
             "WiFi Gateway(${wifi_gateway}), " +
             "WiFi Mask(${wifi_mask}), " +
             "Ethernet Enabled(${ethernet_enabled}), " +       
             "Ethernet IP(${ethernet_ip}), " +
             "Ethernet Gateway(${ethernet_gateway}), " +
             "Ethernet Mask(${ethernet_mask})")
               
     rememberState("firmware", firmware)
     rememberState("wifi_ssid", wifi_ssid)
     rememberState("wifi_enabled", wifi_enabled)
     rememberState("wifi_ip", wifi_ip)
     rememberState("wifi_gateway", wifi_gateway)
     rememberState("wifi_mask", wifi_mask)
     rememberState("ethernet_enabled", ethernet_enabled)
     rememberState("ethernet_ip", ethernet_ip)
     rememberState("ethernet_gateway", ethernet_gateway)
     rememberState("ethernet_mask", ethernet_mask)
    
     rememberState("online", "true")         
}                

/* Currently not supported by API as of 02/27/2022
void setWiFi() { 
   logDebug "setting WiFi: SSID=${settings.SSID}, Password=${settings.WiFiPassword}"

   def params = [:] 
   params.put("ssid", settings.SSID)    
   params.put("password", settings.WiFiPassword) 
    
   def request = [:] 
   request.put("method", "Hub.setWiFi")                
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("setWiFi()> pollAPI() response: ${object}")                                            
            return 							
                
	    } else { 			               
            logDebug("setWiFi() failed")	
        }     		
	} catch (e) {	
        log.error "setWiFi() exception: $e"
	} 
}   
*/

def reset(){          
    state.debug = false  
    state.remove("firmware") 
    state.remove("swState")
    state.remove("door")
    state.remove("alertType")  
    state.remove("battery")     
    state.remove("signal")  
    state.remove("online")
    state.remove("reportAt")
    state.remove("alertInterval")
    state.remove("delay")           
    state.remove("openRemindDelay")

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
