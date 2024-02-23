/***
 *  YoLink Outlet - Allows individual outlet control of a MultiOutlet Device via a Dashboard 
 *  © (See copyright()) Steven Barcus. All rights reserved.
 *   
 *  DO NOT INSTALL THIS DEVICE MANUALLY - IT WILL NOT WORK. MUST BE INSTALLED USING THE YOLINK DEVICE SERVICE APP  
 *   
 *  Developer retains all rights, title, copyright, and interest, including patent rights and trade
 *  secrets in this software. Developer grants a non-exclusive perpetual license (License) to User to use
 *  this software under this Agreement. However, the User shall make no commercial use of the software without
 *  the Developer's written consent. Software Distribution is restricted and shall be done only with Developer's written approval.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *  
 *  2.0.0: Define as required driver (used as child for YoLink Devices)  
 *  2.0.1: Replace 'MultiOutlet Outlet' for naming consistency
 *  2.0.2: Copyright update and UI formatting, remove debugging option
 *  2.0.3: Minor updates
 *  2.0.4: Support "setDeviceToken()"
 *         - Update copyright
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.4"}
def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink Outlet v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink Outlet", namespace: "srbarcus", author: "Steven Barcus") {     	
        capability "Switch"
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

def installed() {
 }

void DeviceSetup(outletNumber) {  
    state.outlet = outletNumber      
 }

def updated() {
 }

def uninstalled() {
 }

def on (sync=true) {
  setSwitch("on",sync)
}
    
def off (sync=true) {
  setSwitch("off",sync)
}      

def setSwitch(status,sync) {
    if (sync) {
        switch(state.outlet) {
            case "outlet1":
                parent.outlet1(status)
		    	break;    
        
            case "outlet2":
                parent.outlet2(status)
			    break;    
        
            case "outlet3":
                parent.outlet3(status)
		    	break;    
        
            case "outlet4":
                parent.outlet4(status)
	    		break;    

            case "usbPorts":
                parent.usbPorts(status)
		    	break;    
	        }    
    }    

    rememberState("switch",status)    
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