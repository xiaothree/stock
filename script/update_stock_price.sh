#!/bin/sh

JAVA_HOME=/usr/java/jdk1.7.0_13
PATH=$JAVA_HOME/bin:$PATH
CLASSPATH=.:$JAVA_HOME/lib/tools.jar:$JAVA_HOME/lib/dt.jar
export JAVA_HOME PATH CLASSPATH

cd /home/latupa/myspace/git_code/stock
$JAVA_HOME/bin/java -cp .:src/main/resources/:target/stock-1.0-update_rt.jar:lib/mysql-connector-java-5.1.21.jar:lib/commons-logging-1.1.1.jar:lib/log4j-1.2.17.jar com.latupa.stock.StockPrice -t 1 -m 0
