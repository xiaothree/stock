<?php

	define ("MYSQL_HOST", "127.0.0.1");
	define ("MYSQL_NAME", "latupa");
	define ("MYSQL_PASSWORD", "latupa");
	define ("MYSQL_DB", "btc");
	define ("TABLE_PRE", "btc_price__");

	function query_summary_daily($table_postfix) {

		$db = mysql_connect(MYSQL_HOST, MYSQL_NAME, MYSQL_PASSWORD);
		if (!$db) {
			die('Could not connect: '.mysql_error());
		}
		//echo 'Connected successfully';

		$table_name = TABLE_PRE.$table_postfix;

		mysql_query("use ".MYSQL_DB);
		mysql_query("set names utf8");

		//$sql = "select time, open, close, high, low from ".$table_name;
		$sql = "select time, open, close, high, low from ".$table_name." order by time desc limit 3600";
		//$sql = "select time, open, close, high, low from ".$table_name." limit 2700,10";
		//echo $sql."\n";
		$result = mysql_query($sql);
		if (!$result) {
		    die('Invalid query: '.mysql_error());
		}

		$k_daily = array();
		while ($row = mysql_fetch_array($result, MYSQL_ASSOC)) {
			$k_daily[] = $row;
		}

		//必须是正序
		$k_daily_asc = array_reverse($k_daily);

		mysql_close($db);
		
		return $k_daily_asc;
	}
?>
