#!/bin/sh

for table in `mysql -uroot -Dstock -e "show tables"|grep -v Table`; do echo $table; mysql -uroot -Dstock -e "repair table $table";done

