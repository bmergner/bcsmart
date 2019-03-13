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
 *
 *
 */

def version() {"v0.1.20190311"}

import hubitat.helper.InterfaceUtils

metadata {
    definition (name: "gogoGate Garage Opener", namespace: "bcsmart", author: "Bob Mergner") {
        capability "Initialize"
        capability "Refresh"
		capability "Switch"
		
		command "DoorOpen"
		command "DoorClose"
		command "GetDoorStatus"
		
		attribute "DoorStatus", "string"
    }
}

preferences {
    input("ip", "text", title: "IP Address", description: "IP Address", required: true)
	input("user", "text", title: "gogoGate Garage User", description: "gogoGate Garage User", required: true)
	input("pass", "password", title: "gogoGate Garage Password", description: "gogoGate Garage Password", required: true)
	input("door", "text", title: "Garage Door Number", description: "Garage Door Number", required: true)
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def refresh() {
    log.info "refresh() called"
	//initialize()
	GetDoorStatus()
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
    
    runEvery1Minute(refresh)
    refresh()
}

def LogOnAndGetCookie(){
	def allcookie
	def cookie
	
	httpPost("http://${ip}", "login=${user}&pass=${pass}&send-login=Sign+In") { resp ->
		allcookie = resp.headers['Set-Cookie']
		log.debug "SetCookieValue: ${allcookie}"
		
		cookie = allcookie.toString().replaceAll("; path=/","").replaceAll("Set-Cookie: ","")
    }
	
	return cookie
	
}

def GetDoorStatus() {
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
		   	log.debug "test: ${status}"
		   	doorStatus = status
		   
			if ( status.contains("0") ) {
				sendEvent(name: "doorStatus", value: "Closed")
			   	sendEvent(name: "switch", value: "off")
		   	}
		   	else
		   	{
			   	sendEvent(name: "doorStatus", value: "Open")
			   	sendEvent(name: "switch", value: "on")
		   	}
			log.debug "State:  ${status}"
	   	}
	
		return cookie
}

def DoorOpen() {
	log.info "dooropen() called"
	
	//is door closed?
	def cookie = GetDoorStatus()
	
	//now see if door is closed
	if ( doorStatus.contains("0") )
		{
			toggleDoor(cookie)
			doorStatus = "2"
	   	}
}

def DoorClose() {
	log.info "dooropen() called"
	
	//Then check to see if the door is open
	def cookie = GetDoorStatus()
	
	log.debug "door len: ${doorStatus.length()}"
	log.debug "door state: ${doorStatus}"
	
	//now see if door is closed
	if ( doorStatus.contains("2") )
		{
			toggleDoor(cookie)
	 		doorStatus = "0"
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
            log.debug resp.contentType
            log.debug resp.status
			log.debug resp.data}
	
	//sometimes it takes a bit for the door to close so check a few times so you can react with other activities ASAP
	runIn(10, refresh)
	runIn(15, refresh)
	runIn(20, refresh)
	runIn(25, refresh)
}
	
def initialize() {
	runEvery1Minute(refresh)
    state.version = version()
    log.info "initialize() called"
    
    if (!ip || !user || !pass || !door) {
        log.warn "gogoGate Garage Opener required fields not completed.  Please complete for proper operation."
        return
    }
	
    refresh()
}
