#!/bin/sh

date=`date +%Y%m%d%H%M%S`

/usr/bin/mysqldump -ulatupa -platupa stock_new > /home/latupa/exchange/mysql_bak/stock_new.sql.$date
