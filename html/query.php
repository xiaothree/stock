<?php

	define ("MYSQL_HOST", "127.0.0.1");
	define ("MYSQL_NAME", "latupa");
	define ("MYSQL_PASSWORD", "latupa");
	define ("MYSQL_DB", "stock");
	define ("TABLE_NAME", "stock_summary_daily");

	function query_summary_daily($table_postfix) {

		$db = mysql_connect(MYSQL_HOST, MYSQL_NAME, MYSQL_PASSWORD);
		if (!$db) {
			die('Could not connect: '.mysql_error());
		}
		//echo 'Connected successfully';

		$table_name = ($table_postfix == "") ? TABLE_NAME : TABLE_NAME."__".$table_postfix;

		mysql_query("use ".MYSQL_DB);
		mysql_query("set names utf8");

		$sql = "select date, szzs, szcz, round(total,2) as total, round(position,2) as position, round(rate,2) as rate, round(profit,2) as profit from ".$table_name;
		$result = mysql_query($sql);
		if (!$result) {
		    die('Invalid query: '.mysql_error());
		}

		$summary_daily = array();
		while ($row = mysql_fetch_array($result, MYSQL_ASSOC)) {
			$summary_daily[] = $row;
		}

		mysql_close($db);
		
		return $summary_daily;
	}
?>
