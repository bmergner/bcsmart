# bcsmart
Bucks County Smart Home

This repository contains groovy files for use on Hubitat Elevation to integrate IOT devices.  

1)  File GoGoGate2GarageControllerDriver.groovy is a Hubitat Elevation driver to integrate the gogoGate2 Garage/Gate Controller.  This file should be added via the drivers code option.  Once added, you can create a virtual device and make it User Type gogoGate2 Garage Controller.  After saving, complete the required fields, then click initialize.  You should be ready to go!

This driver polls the gogoGate2 at a 2 second interval, so the door's status gets updated fairly quickly upon completion of a door activity (i.e. open or close).  This status will update regardless of whether the door is opened via the HE.  This allows you to develop rules that will run on Door Open or Close events even if the door is activated via it's hardwired button, Homelink, or some other method.

To use with rules, choose the Door category and select your Garage Door from the list.  

CHANGE LIST

2019.03.14:  Initial BETA Release v0.1.20190314

2019.03.15:  Update Release v0.1.20190315

             Added Sensor Data from Temperature Sensor and Battery Monitor.  

             Added Additional Setting for temperature Units Preference (C or F)
             
             Added json parsing of data
             
             More refactoring
             
