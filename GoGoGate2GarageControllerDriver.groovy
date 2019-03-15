/**
 *
 *  File: GoGoGate2GarageControllerDriver.groovy
 *  Platform: Hubitat
 *
 *
 *  Requirements:
 *     1) GoGoGate2 Garage Controller connected to same LAN as your Hubitat Hub.  Use router
 *        DHCP Reservation to prevent IP address from changing.
 *     2) Authentication Credentials for GoGoGate2 Garage Door Open.  This is the credentials 
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
 *    2019-03-14  Bob Mergner  More cleanup
 *    2019-03-15  Bob Mergner  Added Temperature Sensor and Battery Level from Door Sensor, added preference for temperature units
 *
 */

def version() {"v0.1.20190315"}

import hubitat.helper.InterfaceUtils

metadata {
    definition (name: "GoGoGate2 Garage Controller", namespace: "bcsmart", author: "Bob Mergner") {
        capability "Initialize"
        capability "Refresh"
		capability "DoorControl"
		capability "TemperatureMeasurement"
		capability "Battery"
		
		attribute "door", "string"
		attribute "battery", "string"
		attribute "temperature","string"
    }
}

preferences {
    input("ip", "text", title: "IP Address", description: "[IP Address of your GoGoGate2 Device]", required: true)
	input("user", "text", title: "GoGoGate2 Garage User", description: "[GoGoGate2 Username (usually admin)]", required: true)
	input("pass", "password", title: "GoGoGate2 Garage Password", description: "[Your GoGoGate2 user's Password]", required: true)
	input("door", "text", title: "Garage Door Number", description: "[Enter 1, 2 or 3]", required: true)
    input name: "useFahrenheit", type: "bool", title: "Use Fahrenheit", defaultValue: true
	input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}



def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def refresh() {
	GetDoorStatus()
}

def doorPollStatus() {
	//this function runs every one minute.  It polls and then sleeps for 2 seconds.  Since the GoGoGate doesn't "push" state changes, we
	//have to go get them, and quickly, if we want responsive automations.
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
	currentTemp = 1000
	currentBattery = "unknown"
	
	runEvery1Minute(doorPollStatus)
    refresh()
}

def LogOnAndGetCookie(){
	def allcookie
	def cookie
	
	httpPost("http://${ip}", "login=${user}&pass=${pass}&send-login=Sign+In") { resp ->
		allcookie = resp.headers['Set-Cookie']
		cookie = allcookie.toString().replaceAll("; path=/","").replaceAll("Set-Cookie: ","")
    }
	
	return cookie
}

def GetDoorStatus() {
	if (currentState == null) {
		currentState = "99"
		currentTemp = 1000
		currentBattery = "unknown"
	}
	
	def cookie = LogOnAndGetCookie()
	
	def params = [uri: "http://${ip}/isg/statusDoorAll.php?status1=10",
		headers: ["Cookie": """${cookie}""",
			"Referer": "http://${ip}/index.php",
			"Host": """${ip}""",
            "Connection": "keep-alive"],
       	requestContentType: "application/json; charset=UTF-8"]
	
    httpGet(params) { resp ->
		int whichdoor = Integer.parseInt(door,16)
		status = parse(resp.data.toString(),whichdoor - 1)
		doorStatus = status
		   
		if ( status.contains("0") && !currentState.contains("0") ) {
		   		sendEvent(name: "door", value: "closed")
				currentState = "0"
		   	}
		else if ( status.contains("2") && !currentState.contains("2") ) {
			   	sendEvent(name: "door", value: "open")
				currentState = "2"
		   	}
	   	}
	
	params = [uri: "http://${ip}/isg/temperature.php?door=${door}",
		headers: ["Cookie": """${cookie}""",
			"Referer": "http://${ip}/index.php",
			"Host": """${ip}""",
            "Connection": "keep-alive"],
        requestContentType: "application/json; charset=UTF-8"]
	
    httpGet(params) { resp ->
		temp = parse(resp.data.toString(),0)
		battery = parse(resp.data.toString(),1)
   
		itemp = temp.toInteger()
		   
	   	//standard temperature sensor calculation divide result by 1000 to get centigrade, then multiply by 1.8 and add 32 for fahrenheit
	   	c = itemp/1000
	    f = (c * 1.8) + 32
		
		//quick simple rounding
		c = c.toInteger()
		f = f.toInteger()
		   
		//regardless of user selection for display, internally, temperature is maintained in rounded fahrenheit
		//only send the event if temp changes or you fill the event log with junk
		if ( f != currentTemp ) {
			currentTemp = f
			if ( useFahrenheit ) {	
			   	sendEvent(name: "temperature", value: "${f} ºF")
			}
			else {
			   	sendEvent(name: "temperature", value: "${c} ºC")
			}
		}
				
		if ( battery != currentBattery ) {
		   	sendEvent(name: "battery", value: "${battery}")
		}
	}
	
	return cookie
}

def parse(String jsonText, int i) {
	//the data being passed in is in json format, but lacks description labels, so we parse it positionally
    def json = null;
    try{
        json = new groovy.json.JsonSlurper().parseText(jsonText)
          
        if(json == null){
            log.warn "Data not parsed"
            return
        }
    }  catch(e) {
        log.error("Failed to parse json e = ${e}")
        return
    }
    
	return json[i]
}

def open() {
	log.info "Door ${door} received open command from Hubitat Elevation"

	def cookie = GetDoorStatus()
	
	//now see if door is open already
	if ( doorStatus.contains("0") )	{
		toggleDoor(cookie)
		doorStatus = "2"
		log.info "Open command sent to Door ${door}"
	}
	else {
		log.info "Door ${door} already open"
	}
}

def close() {
	log.info "Door ${door} received Close command from Hubitat Elevation"
	
	def cookie = GetDoorStatus()
	
	//now see if door is closed already
	if ( doorStatus.contains("2") ) {
		toggleDoor(cookie)
	 	doorStatus = "0"
		log.info "Close command sent to Door ${door}"
	}
	else {
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
    	//nothing to do
	}
}
	
def initialize() {
    state.version = version()
	currentState = "99"
	currentTemp = 1000
	currentBattery = "unknown"
	runEvery1Minute(doorPollStatus)
    log.info "initialize() called"
    
    if (!ip || !user || !pass || !door) {
        log.warn "GoGoGate2 Garage Door Controller required fields not completed.  Please complete for proper operation."
        return
    }
	
    refresh()
}

