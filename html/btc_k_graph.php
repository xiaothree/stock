<!DOCTYPE html>
<html lang="en">
<head>
        <meta http-equiv="Content-Type" content="text/html; charset=utf8" />
        <title>btc K线图</title>
        <link href="bootstrap/css/bootstrap.css" rel="stylesheet">
        <link href="bootstrap/css/bootstrap-responsive.css" rel="stylesheet">
        <script src="bootstrap/js/bootstrap.js"></script>

        <link rel="stylesheet" href="amstockcharts/amcharts/style.css" type="text/css">

	<?php
		require_once("btc_query.php");

		function usage() {
			echo "btc_k_graph.php?data_cycle=[60|120|300|600]&start=yyyymmddhhmmss&end=[yyyymmddhhmmss]\n";
		}

		if (!isset($_GET["data_cycle"]) || !isset($_GET["start"])) {
			usage();
			exit;
		}

		$data_cycle = $_GET["data_cycle"];
		if ($data_cycle != 60 && $data_cycle != 120 && $data_cycle != 300 && $data_cycle != 600) {
			usage();
			exit;
		}

		$start = $_GET["start"];
		$end   = $_GET["end"] ? $_GET["end"] : "";

		$k_daily = query_summary_daily($data_cycle, $start, $end);
		//var_dump($k_daily);
		//echo "count:".count($k_daily)."\n";
		$json_k_daily=json_encode($k_daily);
		//var_dump($json_k_daily);
		//echo "length:".strlen($json_k_daily)."\n";
		$fd = fopen("/tmp/test", "w");
		fwrite($fd, $json_k_daily, strlen($json_k_daily));
		fclose($fd);
	?>
	<input type="hidden" value='<?php echo $json_k_daily;?>' id="show"><br>
	<input type="hidden" value='<?php echo $data_cycle;?>' id="data_cycle"><br>
</head>

<body>
	<div class="row">
		<div class="span12 offset1">
			<!--<div id="chartdiv" style="width: 100%; height: 800px; background:#BCBCB0;"></div>-->
			<div id="chartdiv" style="width: 1200px; height: 700px; background:#BCBCB0;"></div>
		</div>
	</div>
</body>
        <script src="amstockcharts/amcharts/amcharts.js" type="text/javascript"></script>        
	<script src="amstockcharts/amcharts/serial.js" type="text/javascript"></script>
	<script src="amstockcharts/amcharts/amstock.js" type="text/javascript"></script>
        <script src="btc_k_graph.js" type="text/javascript"></script>        
</html>

