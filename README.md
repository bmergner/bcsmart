# bcsmart
Bucks County Smart Home
This repository contains groovy files for use on Hubitat to control IOT devices.  

File gogoGateGarage.groovy is a Hubitat driver to control the gogoGate2 Garage/Gate opener.  This file should be added via the drivers code option.  Once added, you can create a virtual device and make it User Type gogoGate Garage Opener.  After saving, complete the required fields, then click initialize.  You should be ready to go!

The On/Off buttons do not control the opening and closing of the door.  They are there for the purpose of rules.  If you select the device type Switch in the Rules Machine, your garage door will be available to select.  You can then control other devices based on the fact that the garage door has been opened.  The Door Open and Door Close buttons actually open and close the door.  When one of these operations has been completed, the status of the On/Off will reflect the doorOpen status.  

Device refresh is currently done every 1 minute.  I'm considering taking this down to 15 seconds so that the On/Off status gets updated as quickly as possible to provide good automation.  The refresh doesn't have any discernable impact on any of the involved resources, so I'll likey add it to the next release as an optional configuration setting.
