/**
 *  YoLink™ Device Service
 *  © 2022 Steven Barcus. All rights reserved.
 *  THIS SOFTWARE IS NEITHER DEVELOPED, ENDORSED, OR ASSOCIATED WITH YoLink™ OR YoSmart, Inc.
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
 */
import groovy.json.JsonSlurper

definition(
    name: "YoLink™ Device Service",
    namespace: "srbarcus",
    author: "Steven Barcus",
    description: "Connects your YoLink™ devices to Hubitat.",
    oauth: false,    
    category: "YoLink",
    singleInstance: true,
    iconUrl: "",
    iconX2Url: ""
    //iconUrl: "${getCustomImagePath()}yolink.png",
    //iconX2Url: "${getCustomImagePath()}yolink.png"
    //importUrl: "https://raw.githubusercontent.com/srbarcus/"
    //importUrl:'https://raw.githubusercontent.com/imnotbob/webCoRE/hubitat-patches/smartapps/ady624/webcore-piston.src/webcore-piston.groovy'
)

private def get_APP_VERSION() {
	return "1.0.0"
}

private def get_APP_NAME() {
	return "YoLink™ Device Service"
}

preferences {
	page(name: "about", title: "About", nextPage: "credentials")
    page(name: "credentials", title: "YoLink™ App User Access Credentials", content:"credentials", nextPage:"deviceList")
    page(name: "deviceList",title: "YoLink™ Devices", content:"deviceList",nextPage: "otherSettings")  
	page(name: "otherSettings", title: "Other Settings", content:"otherSettings", install:true, uninstall: false)
}

def about() {
 	dynamicPage(name: "about", title: pageTitle("About"), uninstall: true) {
 		section("") {	
			paragraph image:"${getCustomImagePath()}yolink.png", boldTitle("${get_APP_NAME()} - Version ${get_APP_VERSION()}")
			paragraph boldTitle("This app connects your YoLink™ devices to Hubitat via the cloud.")   
            paragraph blueTitle("The app is neither developed, endorsed, or associated with YoLink™ or YoSmart, Inc.") 
            paragraph boldTitle ("")
			paragraph "Donations are appreciated and allow me to purchase new YoLink™ devices for development. Please donate via PayPal by clicking on this Paypal button:"
                href url:"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1&currency_code=USD", title:"Paypal donation..."
			paragraph boldTitle ("© 2022 Steven Barcus. All rights reserved.")	                           
            paragraph boldTitle ("")
            paragraph boldTitle ("")
            if (removeDevices != "False"){
              paragraph boldRedTitle ("WARNING: Removing this application will delete all Hubitat devices created by the app! (This option can be disabled in the additional settings before removal)")
            }    
		}
	}        
}

def credentials() {    
  		dynamicPage(name: "credentials", title: pageTitle("YoLink™ Access Credentials"), uninstall: false) {
        section(""){
			paragraph "These values must match the YoLink™ mobile app values precisely. Take care to insure upper and lowercase letters, as well as similar looking characters such as '0' (zero) and 'O' (oh) and '1' (one) and 'l' (ell), are specified exactly as shown in the app."
		    }    
		section(sectionTitle("User Access Credentials from the YoLink™ mobile app")) {
		    input "UAID", "text", title: boldTitle("UAID:"), required: true	    	
            input "secret_key", "text", title: boldTitle("Secret Key:"), required: true	       
	    }
        section("") {	
			paragraph "Click here for information on obtaining your User Access Credentials from the YoLink™ mobile app" 
				href url:"https://github.com/srbarcus/yolink/blob/main/help/README.md",
	 				title:"How to obtaining your User Access Credentials (UAC)"			
		}
     } 
}

def deviceList() {
    if (!refreshAuthToken()){
        dynamicPage(name: "deviceList", title: pageTitle("Error obtaining access token"), nextPage:"credentials", uninstall: false) {
        section(boldTitle("The YoLink server returned the following error:")){
            paragraph "${state.token_error}"				
			}    
		section(""){
			paragraph "Click Next to respecify your User Access Credentials from the YoLink modile app."
		    }
	    }        
    } else {
 	    def devices=getDevices()
        int devicesCount=devices.size()    
        
        log.debug "deviceList(): $devicesCount YoLink devices found: ${devices}"	    
	    dynamicPage(name: "deviceList", title: sectionTitle("$devicesCount devices found. Select the devices you want available to Hubitat"), uninstall: false) {
    		section(""){
	    		paragraph "Click below to see the list of devices available in your YoLink home"
		    	input(name: "exposed", title:"", type: "enum", required:true,  description: "Click to choose", options:devices, multiple:true)
                paragraph boldTitle ("")                
                paragraph boldTitle ("")
                paragraph boldTitle ("Note: Clicking 'Next' will create the selected devices and/or delete the deselected devices. This could take awhile if many selections are changed, please be patient.") 
		    }
	    }
    }
}

def otherSettings() {    
    int devicesCount=exposed.size()  
    log.debug "$devicesCount devices were selected."
        
    getGeneralInfo()	
    
    def Keep_Hubitat_dni 
    
    exposed.each { dni ->                   
                    def devname = state.deviceName."${dni}"
                    def devtype = state.deviceType."${dni}"
                    def devtoken = state.deviceToken."${dni}"
                    def devId = state.deviceId."${dni}"
                    log.debug "Creating selected Device: ${devname}, ${devtype}, ${devtoken}, ${devId}"	
                    def Hubitat_dni = "yolink_${devtype}_${dni}"
                    Hubitat_dni = create_yolink_device(Hubitat_dni, devname, devtype, devtoken, devId)
                    Keep_Hubitat_dni = Keep_Hubitat_dni.plus(Hubitat_dni)  
				} 	 
    
    def devname = "YoLink Cloud Listener"
    def devtype = "MQTT Listener"
    def devtoken = state.UAID.plus("MQTT1")
    def devId = state.UAID.plus("MQTT1")
    log.debug "Creating debugging Device: ${devname}, ${devtype}, ${devtoken}, ${devId}"	
    def Hubitat_dni = "yolink_${devtype}_${devId}"
    Hubitat_dni = create_yolink_device(Hubitat_dni, devname, devtype, devtoken, devId)
    Keep_Hubitat_dni = Keep_Hubitat_dni.plus(Hubitat_dni)  
    
    def children= getChildDevices()
	children.each { 
        if (!Keep_Hubitat_dni.contains(it.deviceNetworkId)){
          log.warn "Device ${it} (${it.deviceNetworkId}) is no longer selected. Deleting the device."
          deleteChildDevice(it.deviceNetworkId)   
        }    
 	}
     
    if (!pollInterval){pollInterval = 2}
    if (!removeDevices){removeDevices = True}
    
	dynamicPage(name: "otherSettings", title: pageTitle("Other Settings"), install: true, uninstall: true) {
        section(smallTitle("Temperature Scale (Celsius or Fahrenheit)")) {
			input "temperatureScale", "enum", title:boldTitle("Select Temperature Scale"), required: true, options:["C","F"],defaultValue: "F"
		}
		section(smallTitle("Device polling interval in minutes")) {
			input "pollInterval", "enum", title:boldTitle("Select Polling Interval"), required: true, options:[1,2,5,10,15,30],defaultValue: 5
		}
        section(smallTitle("Remove associated Hubitat devices when this app is removed")) {
			input "removeDevices", "enum", title:boldTitle("Remove Devices"), required: true, options:["True","False"],defaultValue: "True"             
       	}
	}
}

private create_yolink_device(Hubitat_dni,devname,devtype,devtoken,devId) { 	
    def newdni = Hubitat_dni
    def drivername = getYoLinkDriverName("$devtype")
    int countNewChildDevices = 0        
        
	def dev = allChildDevices.find {it.deviceNetworkId.contains(newdni)}	

	if(!dev) {
        def labelName = "YoLink $devtype - ${devname}"
            
		log.debug "Creating child device named ${devname} with id $newdni, Type = $devtype,  Device Token = $devtoken, "
        
        try {
           dev = addChildDevice("srbarcus", drivername, newdni, null, [label:"${labelName}"])
                         
           if (devtype == "SpeakerHub") {
              log.trace "A speaker Hub named '$devname' has been located." 
              state.speakerhub = newdni
           }  
            
		} catch (IllegalArgumentException e) {
    		log.error "An error occcurred while trying add child device '$labelName'"
                                
            if (e.message.contains("Please use a different DNI")){    //Could happen if user uninstalled app without deleting previous children                 
                  throw new IllegalArgumentException("A device with dni '$newdni' already exists. Delete the device and try running the " + get_APP_NAME() + " app again.")
            } else {    
                  throw new IllegalArgumentException(e.message)  
            }                  
        }
            
        log.trace "Calling child device setup: $newdni, $state.homeID, $devname, $devtype, $devtoken, $devId"
        dev.ServiceSetup(newdni,state.homeID,devname,devtype,devtoken,devId) 	// Initial setup of the Child Device             
		log.trace "Setup of ${dev.displayName} with id $newdni has been completed"
            
		countNewChildDevices++            
	} else {
		log.info "Child device ${dev.displayName} already exists with id $newdni, new device not created"
        if (devtype == "SpeakerHub") {
           log.trace "A speaker Hub name $devname has been located." 
           state.speakerhub = newdni
        }  
	}

	log.info "Created $countNewChildDevices of ${exposed.size()} selected devices."
    
    return newdni
}

def installed() {
    log.info "${get_APP_NAME()} app installed with settings: ${settings}"
   	pollDevices()   
    schedulePolling()
}

def updated() {
	log.info "${get_APP_NAME()} updated with settings: ${settings}"    
    pollDevices()   
    schedulePolling()
}

def uninstalled() {    
    unschedule()
    log.warn "Uninstalling ${get_APP_NAME()} app"
    if (removeDevices != "False"){
      delete_child_devices(getChildDevices()) 
    }   
}

def pollDevices() {
    def children= getChildDevices()
    children.each { 
       log.debug "Polling device ${it} (${it.deviceNetworkId})."
       it.poll()
    }             
}

def schedulePolling() {		
    //Integer interval = (pollInterval) ? pollInterval.toInteger(): 5 // Default: Poll every 5 minutes    
    def interval = (pollInterval) ? pollInterval.toString(): "5" // Default: Poll every 5 minutes    
        
    log.trace "Scheduling device polling for every ${interval} minutes"  
    
    //def seconds = 60 * interval
    unschedule()
    //runIn(seconds, pollDevices)  
    
    def pollIntervalCmd = interval.plus("Minutes")
    
    "runEvery${pollIntervalCmd}"(pollDevices)       
}

private SpeakerHub() {    
    def dev = allChildDevices.find {it.deviceNetworkId.contains(state.speakerhub)}    
    if(dev) {
       log.trace "SpeakerHub() = $dev.label"    
       return dev
    } else {    
       log.trace "SpeakerHub(): No speaker hub available"  
       return null
    }    
}  

private def delete_child_devices(delete=null) {	
	if (delete) {
       	delete.each {
     		log.warn "Deleting ${it.displayName}"
           	deleteChildDevice(it.deviceNetworkId)        
		}            
        	return        
    	}           
}

def getYoLinkDriverName(devtype) {
    def driver = "YoLink ${devtype} Device" 
    log.info "Driver name for type ${devtype} is '${driver}'"
    return driver
    }

private def apiURL() { return 'https://api.yosmart.com/open/yolink/v2/api'} 
private def mqqtURL() { return 'http://api.yosmart.com'} 
private def tokenURL() {return "https://api.yosmart.com/open/yolink/token"}
private def getStandardImagePath() {return "http://cdn.device-icons.smartthings.com/"}
private int SUCCESS() {return 200}
private int UNAUTHORIZED() {return 401}
private def getCustomImagePath() {return "https://raw.githubusercontent.com/srbarcus/yolink/main/icons/"}   

def AuthToken() {state.access_token}  
def refreshAuthToken() {   
   
	log.info ("Refreshing access token")
	boolean rc=false    
            
    state?.UAID = UAID
    state?.secret_key = secret_key 
    
    state.remove("access_token")            // Remove any old token  
   	
    def query = [:]
    query.put("grant_type", "client_credentials")    
    query.put("client_id", "${state.UAID}")    
    query.put("client_secret", "${state.secret_key}")    
    
	def refreshParams = [
			uri   : tokenURL(),
            query : query
	]

    log.debug "Attempting to get access token using parameters: ${refreshParams}"   
    
	try {     
		httpPostJson(refreshParams) { resp ->                     
            
			if (resp.status == SUCCESS()) {	
                log.debug "API Response: SUCCESS"
				def object = resp.data

				if (object) {                    
					log.debug "Token Server Response: ${resp.data}"					
                    
                    def tokenState = object?.state
                    def tokenMsg = object?.msg                    
                    
                    if (tokenState == "error") {
                        state.token_error = tokenMsg 
                        log.error "Authorization token refresh failed: ${tokenMsg}"                     
                    } else {                                       
                        state.access_token = object?.access_token                    
                        state.access_token_ttl = object?.expires_in                    
                        log.info "New access token = ${state.access_token}"                     
                        rc = true
                    }
				}              
			} else { 				
				log.error "Refreshing access token failed ${resp.status} : ${resp.status.code}"				
			} 
		} 
	} catch (groovyx.net.http.HttpResponseException e) {	
            log.error ("Error refreshing access token, Exception: $e")
			if (e?.statusCode == UNAUTHORIZED()) { 
    			log.warn "Unauthorized Request. Insure your access credentials match those in your YoLink mobile app"
			}            
	}  
    
    log.info "refreshAuthToken() RC = ${rc}"   
    
	return rc
}

boolean getGeneralInfo() {	                      
    def body = [:]
        body.put("method", "Home.getGeneralInfo") 
                    
    log.debug "Polling API to retrieve general info..."   
    
	try {         
        def object = pollAPI(body,"YoLink™ Device Service","App")
         
        if (object) {
                log.debug "pollAPI() response: ${object}"                      
                
                def code = object.code               
                def time = object.time
                def msgid = object.msgid  
                def desc = object.desc 
                state.homeID = object.data.id    //ID of home
            
                log.info "Home ID: ${state.homeID}"                                                   
                    
                return true							
                
			} else { 			               
				log.error "Error getting GeneralInfo"	
                return false
			} 
    
		
	} catch (groovyx.net.http.HttpResponseException e) {	
            log.error "Error getting GeneralInfo, Exception: $e"
			if (e?.statusCode == UNAUTHORIZED()) { 
				log.warn "Unauthorized Request. Insure your access credentials match those in your YoLink mobile app"
			}            
	}
}    

String pageTitle 	(String txt) 	{ return '<h2>'+txt+'</h2>'}
String sectionTitle	(String txt) 	{ return '<h3>'+txt+'</h3>'}
String blueTitle	(String txt)	{ return '<span style="color:#0000ff">'+txt+'</span>'} 
String smallTitle	(String txt)	{ return '<h3 style="color:#8c8c8c"><b>'+txt+'</b></h3>'} 
String boldTitle	(String txt) 	{ return '<b>'+txt+'</b>'}
String boldRedTitle	(String txt) 	{ return '<span style="color:#ff0000"><b>'+txt+'</b></span>'}

def getDevices() {
    def body = [:]
        body.put("method", "Home.getDeviceList") 

    log.info "Polling API to retrieve devices..."   
    
    state.speakerhub = null    
    
	try {         
        def object = pollAPI(body,"YoLink™ Device Service","App")
         
        if (object) {
                def code = object.code
                def curtime = object.time                    
                def msgid = object.msgid
                def method = object.method    
                def desc = object.desc 
           
                state.deviceName = [:]
                state.deviceType = [:]    
                state.deviceToken = [:]  
                state.deviceId = [:]  
	            
	            def responseValues=[]              
                    
                if (object.data.devices instanceof Collection) { 
					responseValues=object.data.devices           
                    log.debug "Parsing multiple devices: ${responseValues}"
				} else {
					responseValues[0]=object.data.devices                    
                    log.debug "Parsing single device: ${responseValues}"
				}                
                           
				responseValues.each { device ->
					if (device.name) {
						log.info "Found ${device.type}, '${device.name}' ..."                        
                        
                        def dni = device.deviceId
                                      
						state.deviceName[dni] = device.name                        
                        state.deviceType[dni] = device.type
                        state.deviceToken[dni] = device.token
                        state.deviceId[dni] = device.deviceId                          
					}
				}                   
			} else { 			               
				log.error "getDeviceList failed"	                
			} 
    
		
	} catch (groovyx.net.http.HttpResponseException e) {	
            log.error "Error executing getDeviceList, Exception: $e"
			if (e?.statusCode == UNAUTHORIZED()) { 
				log.warn "Unauthorized Request. Insure your access credentials match those in your YoLink mobile app"
			}         
	}
    
    return state.deviceName
}     

def pollAPI(body, name=null, type=null){
    def rc=null	
    def retry=0
        
    while ((rc == null) && (retry>=0)) {      
        def headers = [:]
        headers.put("Authorization", "Bearer ${state.access_token}") 
                    
        def Params = [
	      	 uri     : apiURL(),
             headers : headers,
             body    : body 
             ]     
        
        log.trace "Attempting to poll API using parameters: ${Params}"          
        
	    try {     
		     httpPostJson(Params) { resp ->
			 	if (resp.data) {                    
					log.trace "API Response: ${resp.data}"	                                  
                                 
                   	def object = resp.data
                    
                    def code = object.code                  
                    def desc = object.desc  
                    
                    if ((!desc) || (code==desc)) {desc = translateCode(code)}  
                    
                    switch (desc) {
                    case "Success":
                         log.trace "Polling of API completed successfully"                                               
                 	     rc = object
                         break;
                        
                    case "Cannot connect to Device":
                         log.warn "Device '${name}' (Type=${type}) is offline"  
                         break;
                        
                   default:
                         log.error "Polling of API failed: $desc" 
                         rc = object
                         break;
                   }
                } else {
                     log.error "Polling of API failed ${resp.status} : ${resp.status.code}" 
                     rc = object                     
                }  
		    } /* end http post */
	    } catch (groovyx.net.http.HttpResponseException | java.net.SocketTimeoutException e) {	
            log.error "pollAPI() Exception: $e"
			if (e?.statusCode == UNAUTHORIZED()) { 
                if (retry==0) {
				  log.warn "Request was unauthorized. Attempting to refreshing access token and re-poll API."
                  refreshAuthToken()                   
                  retry = 1  // Retry once
                } else {          
                  log.error "Retry failed, final error status code: $e.statusCode"
                  retry = -1 // Don't retry
                } 
            } else {    
                log.error "pollAPI() error status code: $e.statusCode"  
                retry = -1 // Don't retry
			}            
	    }
    }  //End While 
    
	return rc
}


def translateCode(code) {
    def codes = '{"000000":"Success",' + 
     '"000101":"Cannot connect to Hub",' +         
     '"000102":"Hub cannot response to this command",' +
     '"000103":"Token not valid",' +
     '"000201":"Cannot connect to Device",' +
     '"000202":"Device cannot response to this command",' +
     '"000203":"Cannot connect to Device",' +
     '"010000":"Connection not available, try again",' +
     '"010101":"Header Error! Customer ID Error!",' +
     '"010201":"Body Error! Time can not be null",' +
     '"010203":"Unsupported Request",' +                         //Was returned trying to set WiFi - not documented, but seems to mean "Unsupported"  
     '"020102":"Device mask error",' +
     '"020201":"Not any device searched",' + 
     '"030101":"No Data found",'+
     '"999999":"UnKnown Error,Please email to service@YoSmart.com"' +
     '}'
        
    def jsonSlurper = new JsonSlurper()
    def object = jsonSlurper.parseText(codes)
    
    def translation = object."${code}"
    
    if (translation) {
        return translation
    } else {
        log.error" ${code} is an undefined return code"
        return " ${code} is an undefined return code"
    } 
}  

def batterylevel(level) {
    def levels = '{"0":"0",' + 
     '"1":"25",' +         
     '"2":"50",' +
     '"3":"75",' +
     '"4":"100"' +     
     '}'
        
    def jsonSlurper = new JsonSlurper()
    def object = jsonSlurper.parseText(levels)
    
    def translation = object."${level}" 
    
    if (translation) {
        return translation
    } else {
        log.error" ${level} is an undefined battery level"
        return "-1"
    } 
    
}  

def relayState(value) {    
    switch (value) {
       case "open":
         return "on"
         break;
       case "closed":
         return "off" 
         break;
       case "close":
         return "off" 
         break; 
       default:
         return value
         break;
    }
}  

def convertTemperature(temperature) {
    if (settings.temperatureScale == "F") {
        return celsiustofahrenheit(temperature)  
    } else {    
        return temperature  
    }    
}
    
def celsiustofahrenheit(celsius) {return (celsius * 1.8) + 32}

def scheduledDays(weekdays) {
   def days    
    
   def daysB = Integer.toBinaryString(weekdays.toInteger())                   
   def ndx = daysB.length()    
    
   log.debug "Parsing Binary Scheduled Days: ${daysB}(${ndx})"
   
   while (ndx >0) {
     def bit = daysB.substring(ndx-1,ndx)   
     log.debug "Bit ${ndx}: ${bit}"   
     
     if (bit == "1") {  
        switch(daysB.length()-ndx) {
		  case "0":
            days = commaConcat(days,"Sun")
            break;
          case "1":
            days = commaConcat(days,"Mon")
            break; 
          case "2":
            days = commaConcat(days,"Tue")
            break; 
          case "3":
            days = commaConcat(days,"Wed")
            break; 
          case "4":
            days = commaConcat(days,"Thu")
            break; 
          case "5":
            days = commaConcat(days,"Fri")
            break; 
          case "6":
            days = commaConcat(days,"Sat")
            break;   
        }    
     }    
       
     ndx--  
   }    
    
   log.debug "Scheduled Days: ${days}" 
   return days 
}

def scheduledDay(weekday) {
    switch(weekday) {
		  case "0":
            weekday = "Sun"
            break;
          case "1":
            weekday = "Mon"
            break; 
          case "2":
            weekday = "Tue"
            break; 
          case "3":
            weekday = "Wed"
            break; 
          case "4":
            weekday = "Thu"
            break; 
          case "5":
            weekday = "Fri"
            break; 
          case "6":
            weekday = "Sat"
            break;   
        }    

   return weekday 
}


def commaConcat(oldvalue,newvalue) {
    if (!oldvalue) {
      oldvalue = newvalue  
    } else {
      oldvalue = oldvalue + "," + newvalue    
    }    
    return oldvalue
}

boolean validBoolean(setting,value) {            // Allow any one of ON, OFF, TRUE, FALSE, YES, NO
   value = value ?: ""   
   value=value.toUpperCase()
   def rc = null
            
   if (("${value}" == "TRUE") || ("${value}" == "ON") || ("${value}" == "YES")) {
       rc = true 
   } else {     
       if (("${value}" == "FALSE") || ("${value}" == "OFF") || ("${value}" == "NO")) {
         rc = false
       } else {    
         log.error "${setting}(${value}) is invalid. Use 'FALSE', 'TRUE', 'OFF', 'ON', 'NO' or 'YES'"         
       }    
   } 
   return rc
}