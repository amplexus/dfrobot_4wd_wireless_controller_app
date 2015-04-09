#!/bin/bash

java -Djava.library.path=/usr/lib/jni/ -cp bin/classes/:/home/craig/xbee-api-0.9/lib/log4j.jar:/home/craig/xbee-api-0.9/lib/RXTXcomm.jar:/home/craig/xbee-api-0.9/xbee-api-0.9.jar org.amplexus.dfrobot.app.DFRobot4WDPlatformController
