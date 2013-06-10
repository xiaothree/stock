#!/bin/sh

if [ "$#" -ne "1" ]
then
	echo "usage: input the filename"
fi

file=$1
echo "start to proc $file"

java -cp .:src/main/resources/:target/stock-1.0-SNAPSHOT.jar:lib/mysql-connector-java-5.1.21.jar:lib/commons-logging-1.1.1.jar:lib/log4j-1.2.17.jar com.latupa.stock.TransDetail $file
