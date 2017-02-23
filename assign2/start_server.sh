#!/bin/bash

java -jar VirtualNetwork.jar -v r1 -r rtable.r1 -a arp_cache &
#java -jar VirtualNetwork.jar -v r2 -r rtable.r2 -a arp_cache &
java -jar VirtualNetwork.jar -v s1  &

