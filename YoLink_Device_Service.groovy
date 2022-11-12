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
 *  1.0.1: Add support to determine correct driver for Temperature only sensors. Removed some superfluous log messages.
 *  1.0.2: Return converted temperature as a String value.
 *  1.0.3: Reduce number of messages by adding debug setting, misc. fixes
 *  1.0.4: Fix "No such property: statusCode for class: java.net.SocketTimeoutException" error, improved polling error diagnostic messages
 *  1.0.5: Return temperatures as a Number rounded to 1 decimal place, return battery level as a Number.
 *  1.0.6: Remove any possible leading and/or trailing spaces in credentials.
 *  1.0.7: - Add temperature scale function
 *         - Sync temperature scale changes with all devices - NOTE: YoLink mobile app settings will override!
 *  1.0.8: Fix donation URL
 *  2.0.0: - Reengineer app and drivers to use centralized MQTT listener due to new YoLink service restrictions
 *         - Update API return code translations  
 *  
 *  2.1.0: Add support for Smart Outdoor Plug (YS6802-UC/SH-18A)
 *  2.1.1: Speed up execution
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
    iconUrl: "${getImagePath()}yolink.png",
    iconX2Url: "${getImagePath()}yolink.png",
    importUrl: "https://github.com/srbarcus/yolink/edit/main/YoLink_Device_Service.groovy"
)

private def get_APP_VERSION() {return "2.1.1"}
private def get_APP_NAME() {return "YoLink™ Device Service"}

preferences {
	page(name: "about", title: "About", nextPage: "credentials")
    page(name: "credentials", title: "YoLink™ App User Access Credentials", content:"credentials", nextPage:"deviceList")
    page(name: "deviceList",title: "YoLink™ Devices", content:"deviceList",nextPage: "otherSettings")  
	page(name: "otherSettings", title: "Other Settings", content:"otherSettings", uninstall: false)
}

def about() {
 	dynamicPage(name: "about", title: pageTitle("About"), uninstall: true) {
 		section("") {	
			paragraph image:"${getImagePath()}yolink.png", boldTitle("${get_APP_NAME()} - Version ${get_APP_VERSION()}")
			paragraph boldTitle("This app connects your YoLink™ devices to Hubitat via the cloud.")   
            paragraph blueTitle("The app is neither developed, endorsed, or associated with YoLink™ or YoSmart, Inc.") 
	        paragraph blueTitle("Provided 'AS IS', without warranties or conditions of any kind, either express or implied.") 		
            paragraph boldTitle ("")
			paragraph "Please donate and support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. Please donate via PayPal by clicking on this Paypal button:"
                href url:"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1&currency_code=USD", title:"Paypal donation..."
			paragraph boldTitle ("© 2022 Steven Barcus. All rights reserved.")	                           
            paragraph boldTitle ("")
            paragraph boldTitle ("")
            if (removeDevices != "False"){
              paragraph boldRedTitle ("WARNING: Removing this application will delete all Hubitat devices created by the app! (This option can be disabled in the additional settings before removal)")
            }    
		}
      section(smallTitle("Debug")) {
			input "debugging", "enum", title:boldTitle("Enable Debugging"), required: true, options:["True","False"],defaultValue: "False"  
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
				href url:"https://github.com/srbarcus/yolink/blob/main/Help/Obtaining_your_Personal_Access_Credentials.pdf",
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
        
        log.info "$devicesCount YoLink devices were found"	    
	    dynamicPage(name: "deviceList", title: sectionTitle("$devicesCount devices found. Select the devices you want available to Hubitat"), uninstall: false) {
    		section(""){
	    		paragraph "Click below to see the list of devices available in your YoLink home"
		    	input(name: "exposed", title:"", type: "enum", required:true,  description: "Click to choose", options:devices, multiple:true)
                paragraph boldTitle ("")                
                paragraph boldTitle ("")
                paragraph boldTitle ("Note: Clicking 'Next' will create the selected devices and/or delete the deselected devices. This will take awhile if you have many devices, please be patient.") 
		    }
	    }
    }
}

def otherSettings() {    
    int devicesCount=exposed.size()  
    log.info "$devicesCount devices were selected"
        
    getGeneralInfo()	
    
    def Keep_Hubitat_dni 
    
    int countNewChildDevices = 0   
    
    exposed.each { dni ->                   
                    def devname = state.deviceName."${dni}"
                    def devtype = state.deviceType."${dni}"
                    def devtoken = state.deviceToken."${dni}"
                    def devId = state.deviceId."${dni}"
                    log.info "Creating selected Device: ${devtype} - ${devname}"	
                    def Hubitat_dni = "yolink_${devtype}_${dni}"
                    Hubitat_dni = create_yolink_device(Hubitat_dni, devname, devtype, devtoken, devId)
                    if (Hubitat_dni != null) {
                        Keep_Hubitat_dni = Keep_Hubitat_dni.plus(Hubitat_dni)
                        countNewChildDevices++     
                        logDebug("Created $countNewChildDevices of ${exposed.size()} selected devices.")    
                    }
				} 	 
    
    def devname = "YoLink Cloud Listener"
    def devtype = "MQTT Listener"
    def devtoken = state.UAID.plus("MQTT1")
    def devId = state.UAID.plus("MQTT1")    
    def Hubitat_dni = "yolink_${devtype}_${devId}"
    Hubitat_dni = create_yolink_device(Hubitat_dni, devname, devtype, devtoken, devId)
    if (Hubitat_dni != null) {Keep_Hubitat_dni = Keep_Hubitat_dni.plus(Hubitat_dni)}  
       
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
            paragraph boldTitle ("")                
            paragraph boldTitle ("")
            paragraph boldRedTitle ("Click 'Next' to complete device setup and exit the app. Exiting this page any other way may cause devices to work improperly or not at all.") 
       	}
        
	}
}

def installed() {
    log.info "${get_APP_NAME()} app installed."    
    runIn(2, refresh)
}

def updated() {
	log.info "${get_APP_NAME()} app updated."    
    runIn(2, refresh)
}

def refresh() {
    syncTempScale()
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

private create_yolink_device(Hubitat_dni,devname,devtype,devtoken,devId) { 	
    if (devtype == "THSensor") {
      log.info "$devname is a THSensor, determining device's capabilities..."  
      devtype = getTHSensorDriver(devname,devtype,devtoken,devId) 	
    }        
    
    if (devtype == "MultiOutlet") {
      log.info "$devname is a MultiOutlet, determining device's capabilities..."  
      devtype = getMultiOutletDriver(devname,devtype,devtoken,devId) 	
    }   
   
    def newdni = Hubitat_dni
    def drivername = getYoLinkDriverName("$devtype")     
        
	def dev = allChildDevices.find {it.deviceNetworkId.contains(newdni)}	
    
    def failed = false

	if(!dev) {
        def labelName = "YoLink $devtype - ${devname}"
            
		logDebug("Creating child device named ${devname} with id $newdni, Type = $devtype,  Device Token = $devtoken")
        
        try {
           dev = addChildDevice("srbarcus", drivername, newdni, null, [label:"${labelName}"])
                         
           if (devtype == "SpeakerHub") {
              log.trace "A speaker Hub named '$devname' has been located." 
              state.speakerhub = newdni
           }  
            
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
                  log.error "Unable to create device '$devname' because driver '$drivername' is not installed. Either the device is not currently supported by the YoLink™ Device Service, or you need to install the driver using the 'Modify' option in the 'Hubitat Package Manager' app."
            } else {    
                  log.error "Exception: '$e'"
                  throw new IllegalArgumentException(e.message)  
            }                      
        } finally {
            if (failed) {return null}
        }    
            
        logDebug("Calling child device setup: $newdni, $state.homeID, $devname, $devtype, $devtoken, $devId")
        dev.ServiceSetup(newdni,state.homeID,devname,devtype,devtoken,devId) 	// Initial setup of the Child Device             
		logDebug("Setup of ${dev.displayName} with id $newdni has been completed")            
	   
	} else {
		log.info "Child device ${dev.displayName} already exists with id $newdni, new device not created"
        if (devtype == "SpeakerHub") {
           log.trace "A speaker Hub named '$devname' has been located." 
           state.speakerhub = newdni
        }  
	}
    
    return newdni
}

def pollDevices() {
    def children= getChildDevices()
    children.each { 
       logDebug("Polling device ${it} (${it.deviceNetworkId})")
       it.poll()
    }             
}

def passMQTT(topic) {
    logDebug("passMQTT(${topic})")
        
    def payload = new JsonSlurper().parseText(topic.payload)
    logDebug("payload (${payload})")
    
    def deviceDNI = payload.deviceId
    
    logDebug("Searching for deviceID ${deviceDNI}")
    
    def dev = allChildDevices.find {it.deviceNetworkId.contains(deviceDNI)}
    
    if(dev) {
        logDebug("Passing MQTT message ${topic} to device ${dev} (${dev.deviceNetworkId})")
        dev.parse(topic)
        return true     
    } else {             
        return false   
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
       logDebug("SpeakerHub() = $dev.label")
       return dev
    } else {    
       log.info "SpeakerHub(): No speaker hub available"  
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
private def getImagePath() {return "https://raw.githubusercontent.com/srbarcus/yolink/main/icons/"}   

def AuthToken() {state.access_token}  
def refreshAuthToken() {   
	logDebug("Refreshing access token")
	boolean rc=false    
            
    state?.UAID = UAID.trim()
    state?.secret_key = secret_key.trim()
    
    state.remove("access_token")            // Remove any old token  
   	
    def query = [:]
    query.put("grant_type", "client_credentials")    
    query.put("client_id", "${state.UAID}")    
    query.put("client_secret", "${state.secret_key}")    
    
	def refreshParams = [
			uri   : tokenURL(),
            query : query
	]

    logDebug("Attempting to get access token using parameters: ${refreshParams}")
    
	try {     
		httpPostJson(refreshParams) { resp ->                     
            
			if (resp.status == SUCCESS()) {	
                logDebug("API Response: SUCCESS")
				def object = resp.data

				if (object) {                    
					logDebug("Token Server Response: ${resp.data}")
                    
                    def tokenState = object?.state
                    def tokenMsg = object?.msg                    
                    
                    if (tokenState == "error") {
                        state.token_error = tokenMsg 
                        log.error "Authorization token refresh failed: ${tokenMsg}"                     
                    } else {                                       
                        state.access_token = object?.access_token                    
                        state.access_token_ttl = object?.expires_in                    
                        logDebug("New access token = ${state.access_token}")
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
    
    logDebug("refreshAuthToken() RC = ${rc}")
    
	return rc
}

boolean getGeneralInfo() {	                      
    def body = [:]
        body.put("method", "Home.getGeneralInfo") 
                    
    logDebug("Polling API to retrieve general info...")
    
	try {         
        def object = pollAPI(body,"YoLink™ Device Service","App")
         
        if (object) {
                logDebug("pollAPI() response: ${object}")
                
                def code = object.code               
                def time = object.time
                def msgid = object.msgid  
                def desc = object.desc 
                state.homeID = object.data.id    //ID of home
            
                logDebug("Home ID: ${state.homeID}")
                    
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
                    logDebug("Parsing multiple devices: ${responseValues}")
				} else {
					responseValues[0]=object.data.devices                    
                    logDebug("Parsing single device: ${responseValues}")
				}                
                           
				responseValues.each { device ->
					if (device.name) {
						log.info "Found '${device.name}', type=${device.type}"                        
                        
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
        
        logDebug("Attempting to poll API using parameters: ${Params}")
        
	    try {     
		     httpPostJson(Params) { resp ->
			 	if (resp.data) {                    
					logDebug("API Response: ${resp.data}")
                                 
                   	def object = resp.data
                    
                    def code = object.code                  
                    def desc = object.desc  
                    
                    if ((!desc) || (code==desc)) {desc = translateCode(code)}  
                    
                    switch (desc) {
                    case "Success":
                         logDebug("Polling of API completed successfully")
                 	     rc = object
                         break;
                    
                    case "Can't connect to Device":    
                    case "Cannot connect to Device":
                         log.warn "Device '${name}' (Type=${type}) is offline"  
                         rc = object
                         break;
                        
                   default:
                         log.error "Polling of API failed: $desc"
                         log.error "Request: $Params" 
                         rc = object
                         break;
                   }
                } else {
                     log.error "Polling of API failed ${resp.status} : ${resp.status.code}" 
                     log.error "Request: $Params" 
                     rc = object                     
                }  
		    } /* end http post */
	    } catch (groovyx.net.http.HttpResponseException e) {	            
            if (e?.statusCode) { 
			    if (e?.statusCode == UNAUTHORIZED()) { 
                    if (retry==0) {
				      log.warn "Request was unauthorized. Attempting to refreshing access token and re-poll API."
                      refreshAuthToken()                   
                      retry = 1  // Retry once
                    } else {          
                      log.error "Retry failed, final error status code: $e.statusCode"
                      log.error "Request: $Params"  
                      retry = -1 // Don't retry
                    } 
                } else {    
                    log.error "pollAPI() error status code: $e.statusCode"
                    log.error "Request: $Params"
                    retry = -1 // Don't retry
	    		}            
            }
        } catch (java.net.SocketTimeoutException e) {	                     
            log.error "pollAPI() HTTP request timed out"
            log.error "Request: $Params"
	    } catch(Exception ex) {
            log.error "pollAPI() Exception: $e"
            log.error "Request: $Params"
        }
    }  //End While 
    
	return rc
}


def translateCode(code) {
    def codes = '{"000000":"Success",' + 
     '"000101":"Cannot connect to the Hub",' +         
     '"000102":"Hub cannot respond to this command",' +
     '"000103":"Token is invalid",' +
     '"000201":"Cannot connect to the Device",' +
     '"000202":"Device cannot response to this command",' +
     '"000203":"Cannot connect to Device",' +
     '"010000":"Connection not available, try again",' +
     '"010101":"Header Error! Customer ID Error!",' +
     '"010201":"Body Error! Time can not be null",' +
     '"010203":"Unsupported Request",' +                         //Was returned trying to set WiFi - not documented, but seems to mean "Unsupported"  
     '"020102":"Device mask error",' +
     '"020201":"No device searched",' + 
     '"030101":"No Data found",'+
     '"999999":"UnKnown Error, Please email to support@YoSmart.com"' +
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
    def levels = '{"0":0,' + 
     '"1":25,' +         
     '"2":50,' +
     '"3":75,' +
     '"4":100' +     
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
        return celsiustofahrenheit(temperature).toDouble().round(1)        
    } else {    
        return temperature
    }    
}

def temperatureScale() {
    return settings.temperatureScale
}

def syncTempScale() {
    def children= getChildDevices()
    children.each { 
       logDebug("Syncing temperature scale on device ${it}")
       try {                                                //Not all devices support temperature
            it.temperatureScale(settings.temperatureScale)  
	   } catch (Exception e) {                 
            log.warn ("Temperature scale not supported on device ${it}")                  
       }             
    }
}
    
def celsiustofahrenheit(celsius) {return ((celsius * 9 / 5) + 32)} 

def scheduledDays(weekdays) {
   def days    
    
   def daysB = Integer.toBinaryString(weekdays.toInteger())                   
   def ndx = daysB.length()    
   
   while (ndx >0) {
     def bit = daysB.substring(ndx-1,ndx)     
     
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
    
   logDebug("Scheduled Days: ${days}")
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

// Temperature/Humidity and Temperature Devices are both reported as "THSensor"
// If device doesn't return humidity values, assume is doesn't have that capability
def getTHSensorDriver(name,type,token,devId) {
    def driver = "THSensor"
	try {  
        def request = [:]
            request.put("method", "THSensor.getState")                   
            request.put("targetDevice", "${devId}") 
            request.put("token", "${token}") 
        
        def object = pollAPI(request, name, type)
         
        if (object) {
            logDebug("getTHSensorDriver()> pollAPI() response: ${object}")     
            
            if (object.code == "000000") {             
                def lowHumidity = object.data.state.alarm.lowHumidity                             
                def highHumidity = object.data.state.alarm.highHumidity
                def humidity = object.data.state.humidity            
                def humidityCorrection = object.data.state.humidityCorrection        
                def humidityLimitMax = object.data.state.humidityLimit.max
                def humidityLimitMin = object.data.state.humidityLimit.min 
                
                if (lowHumidity == false && highHumidity == false && humidity == 0 && humidityCorrection == 0 && humidityLimitMax == 0 && humidityLimitMin == 0) {   //Assume device doesn't have humidity sensor
                    log.info "$name appears to be a temperature only sensor."
                    driver = "Temperature Sensor"
                } else {
                    log.info "$name appears to be a temperature and humidity sensor."
                }    
            } else {  //Error
                log.error "API polling returned error: $object.code - " + translateCode(object.code)               
            }     
        } else {
            log.error "No response from API request"
        } 
	} catch (groovyx.net.http.HttpResponseException e) {	
            if (e?.statusCode == UNAUTHORIZED_CODE) { 
                log.error("getTHSensorDriver() - Unauthorized Exception")
            } else {
				log.error("getTHSensorDriver() - Exception $e")
			}                 
	}
    
	return driver
}    

// YoLink™ MultiOutlet (YS6801-UC) and Smart Outdoor Plug (YS6802-UC/SH-18A) are both reported as "MultiOutlet"
// If device only returns delays on 2 channels (0 and 1), assume it's a Smart Outdoor Plug 
def getMultiOutletDriver(name,type,token,devId) {
    def driver = "MultiOutlet"
	try {  
        def request = [:]
            request.put("method", "MultiOutlet.getState")                   
            request.put("targetDevice", "${devId}") 
            request.put("token", "${token}") 
        
        def object = pollAPI(request, name, type)
         
        if (object) {
            logDebug("getMultiOutletDriver() - pollAPI() response: ${object}")     
            
            if (object.code == "000000") {             
                def delay = object.data?.delays[2]                                            
                
                if (delay==null) { 
                    log.info "$name appears to be a Smart Outdoor Plug."
                    driver = "Smart Outdoor Plug"
                } else {
                    log.info "$name appears to be a MultiOutlet Device."
                }    
            } else {  //Error
                log.error "API polling returned error: $object.code - " + translateCode(object.code)               
            }     
        } else {
            log.error "No response from API request"
        } 
	} catch (groovyx.net.http.HttpResponseException e) {	
            if (e?.statusCode == UNAUTHORIZED_CODE) { 
                log.error("getMultiOutletDriver() - Unauthorized Exception")
            } else {
				log.error("getMultiOutletDriver() - Exception $e")
			}                 
	}
    
	return driver
}   


def logDebug(msg) {
    if (debugging == "True"){
       log.debug msg
    }   
}    