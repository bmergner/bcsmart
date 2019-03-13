/**
 *
 *  File: gogoGateGarageDoordriver.groovy
 *  Platform: Hubitat
 *
 *
 *  Requirements:
 *     1) gogoGate Garage Door opener connected to same LAN as your Hubitat Hub.  Use router
 *        DHCP Reservation to prevent IP address from changing.
 *     2) Authentication Credentials for gogoGate Garage Door Open.  This is the credentials 
 *        that are used to log on to the opener at it's index.php
 *
 *  Copyright 2019 Robert B. Mergner 
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2019-03-11  Bob Mergner  Original Creation
 *    2019-03-13  Bob Mergner  Cleaned up and refactored.  Added every two second polling for more reactive automation chains in rules
 *
 *
 */

def version() {"v0.1.20190313"}

import hubitat.helper.InterfaceUtils

metadata {
    definition (name: "gogoGate2 Garage Controller", namespace: "bcsmart", author: "Bob Mergner") {
        capability "Initialize"
        capability "Refresh"
		capability "DoorControl"
		
		//command "DoorOpen"
		//command "DoorClose"
		//command "GetDoorStatus"
		
		attribute "door", "string"
    }
}

preferences {
    input("ip", "text", title: "IP Address", description: "[IP Address of your gogoGate Device]", required: true)
	input("user", "text", title: "gogoGate Garage User", description: "[gogoGate Username (usually admin)]", required: true)
	input("pass", "password", title: "gogoGate Garage Password", description: "[Your gogoGate user's Password]", required: true)
	input("door", "text", title: "Garage Door Number", description: "[Enter 1, 2 or 3]", required: true)
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}



def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def refresh() {
    //log.info "refresh() called"
	//initialize()
	GetDoorStatus()
}

def doorPollStatus() {
	 for(int i = 0;i<30;i++) {
         refresh()
		 pauseExecution(2000)
      }
}

def installed() {
    log.info "installed() called"
    updated()
}

def updated() {
    log.info "updated() called"
    //Unschedule any existing schedules
    unschedule()
    
    //Create a 30 minute timer for debug logging
    if (logEnable) runIn(1800,logsOff)
    
    currentState = "99"
	runEvery1Minute(doorPollStatus)
    refresh()
}

def LogOnAndGetCookie(){
	def allcookie
	def cookie
	
	httpPost("http://${ip}", "login=${user}&pass=${pass}&send-login=Sign+In") { resp ->
		allcookie = resp.headers['Set-Cookie']
		//log.debug "SetCookieValue: ${allcookie}"
		
		cookie = allcookie.toString().replaceAll("; path=/","").replaceAll("Set-Cookie: ","")
    }
	
	return cookie
	
}

def GetDoorStatus() {
	if (currentState == null) {
		currentState = "99"
	}
	//def doorStatus
	def cookie = LogOnAndGetCookie()
	
	def params = [uri: "http://${ip}/isg/statusDoorAll.php?status1=10",
				  headers: ["Cookie": """${cookie}""",
							"Referer": "http://${ip}/index.php",
							"Host": """${ip}""",
                            "Connection": "keep-alive"],
                  requestContentType: "application/json; charset=UTF-8"]
	
       httpGet(params) { resp ->
		 	doorStatus = resp.data.toString().substring(1)
		   	doorStatus = doorStatus.substring(0, doorStatus.length() - 1)
		   	int whichdoor = Integer.parseInt(door,16) - 1
		   	status = doorStatus.split(",")[whichdoor]
		   	//log.debug "test: ${status}"
		   	doorStatus = status
		   
			if ( status.contains("0") && !currentState.contains("0") ) {
				//sendEvent(name: "doorStatus", value: "Closed")
			   	sendEvent(name: "door", value: "closed")
				currentState = "0"
		   	}
		   	else if ( status.contains("2") && !currentState.contains("2") ) 
		   	{
			   	//sendEvent(name: "doorStatus", value: "Open")
			   	sendEvent(name: "door", value: "open")
				currentState = "2"
		   	}
			//log.debug "State:  ${status}"
	   	}
	
		return cookie
}

def open() {
	log.info "Door ${door} received open command from Hubitat Elevation"
	
	//is door closed?
	def cookie = GetDoorStatus()
	
	//now see if door is closed
	if ( doorStatus.contains("0") )
		{
			toggleDoor(cookie)
			doorStatus = "2"
			log.info "Open command sent to Door ${door}"
	   	}
	else
		{
			log.info "Door ${door} already open"
		}
}

def close() {
	log.info "Door ${door} received Close command from Hubitat Elevation"
	
	//Then check to see if the door is open
	def cookie = GetDoorStatus()
	
	//now see if door is closed
	if ( doorStatus.contains("2") )
		{
			toggleDoor(cookie)
	 		doorStatus = "0"
			log.info "Close command sent to Door ${door}"
	   	}
	else
		{
			log.info "Door ${door} already closed"
		}
}

def toggleDoor(cookie){
	def params = [uri: "http://${ip}/isg/opendoor.php?numdoor=${door}&status=0&login=${user}",
		headers: ["Cookie": """${cookie}""",
				  "Referer": "http://${ip}/index.php",
				  "Host": """${ip}""",
                  "Connection": "keep-alive"],
                  requestContentType: "application/json; charset=UTF-8"]
	
      	httpGet(params) { resp ->
            //log.debug resp.contentType
            //log.debug resp.status
			//log.debug resp.data
		}
}
	
def initialize() {
    state.version = version()
	currentState = "99"
	runEvery1Minute(doorPollStatus)
    log.info "initialize() called"
    
    if (!ip || !user || !pass || !door) {
        log.warn "gogoGate Garage Opener required fields not completed.  Please complete for proper operation."
        return
    }
	
    refresh()
}
