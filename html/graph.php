<!DOCTYPE html>
<html lang="en">
<head>
        <meta http-equiv="Content-Type" content="text/html; charset=utf8" />
        <title>系统趋势图</title>
        <link href="bootstrap/css/bootstrap.css" rel="stylesheet">
        <link href="bootstrap/css/bootstrap-responsive.css" rel="stylesheet">
        <script src="bootstrap/js/bootstrap.js"></script>
        <link rel="stylesheet" href="amcharts/samples/style.css" type="text/css">
        <script src="amcharts/amcharts/amcharts.js" type="text/javascript"></script>        
        <script src="graph.js" type="text/javascript"></script>        
	<?php
		require_once("query.php");

		$table_postfix = isset($_GET["table_postfix"]) ? $_GET["table_postfix"] : "";

		$summary_daily = query_summary_daily($table_postfix);
		$json_summary_daily=json_encode($summary_daily);
		//var_dump($json_summary_daily);
	?>
	<input type="hidden" value='<?php echo $json_summary_daily;?>' id="show"><br>
</head>

<body>
	<div class="row">
		<div class="span12 offset1">
			<div id="chartdiv" style="width: 100%; height: 600px;"></div>
		</div>
	</div>

</body>
</html>

