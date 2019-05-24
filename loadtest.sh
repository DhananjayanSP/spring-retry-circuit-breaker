#!/bin/bash


for ((i=1;i<=5;i++)); do

START=$(date +%s)
RES=$(curl -s -X GET \
 http://localhost:8080/client/customer/name \
 )
END=$(date +%s)
echo $i $RES  

done
