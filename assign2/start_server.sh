#!/bin/bash

pkill java

for var in "$@"
do
    if [[ $var =~ r[1-9]+ ]] ; then 
        echo Starting router: $var\n 
        java -jar VirtualNetwork.jar -v $var -r rtable.$var -a arp_cache &
        sleep 2
    fi

    if [[ $var =~ s[1-9]+ ]] ; then
        echo Starting switch: $var
        java -jar VirtualNetwork.jar -v $var &
        sleep 2
    fi  
done


echo Setup complete!
