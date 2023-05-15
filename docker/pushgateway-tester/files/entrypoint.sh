#!/bin/bash
while true; do
    VALUE=$(($RANDOM % 100 + 1)) 
    MSG="pushgateway_test $VALUE"
    echo "-- $MSG sent to pushgateway "
    echo "$MSG" | curl --data-binary @- http://pushgateway:9091/metrics/job/pushgateway
    sleep 10
done