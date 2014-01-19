    var chart;
    var chartData = [];
    var data_cycle;

    AmCharts.ready(function () {
	// generate some random data first
	generateChartData();

	// SERIAL CHART    
	chart = new AmCharts.AmStockChart();
	chart.pathToImages = "amstockcharts/amcharts/images/";
	chart.balloon.horizontalPadding = 13;

	// As we have minutely data, we should set minPeriod to "mm"
	var categoryAxesSettings = new AmCharts.CategoryAxesSettings();
	categoryAxesSettings.minPeriod = "mm"; //指定数据的最小时间间隔是分钟
	categoryAxesSettings.groupToPeriods = ["mm"];//指定按照分钟聚合，也就是说不聚合
	categoryAxesSettings.position = "bottom"; 
	//底部时间轴静态显示格式
	categoryAxesSettings.dateFormats = [{period:'fff',format:'JJ:NN:SS'},
		{period:'ss',format:'JJ:NN:SS'},
		{period:'mm',format:'DD JJ:NN'},
		{period:'hh',format:'JJ:NN'},
		{period:'DD',format:'MMM DD'},
		{period:'WW',format:'MMM DD'},
		{period:'MM',format:'MMM'},
		{period:'YYYY',format:'YYYY'}];
	chart.categoryAxesSettings = categoryAxesSettings;

	// DATASET //////////////////////////////////////////
	var dataSet = new AmCharts.DataSet();
	dataSet.fieldMappings = [{
		fromField: "open",
		toField: "open"
	}, {
		fromField: "close",
		toField: "close"
	}, {
		fromField: "high",
		toField: "high"
	}, {
		fromField: "low",
		toField: "low"
	}, {
		fromField: "volume",
		toField: "volume"
/*
	}, {
		fromField: "value",
		toField: "value"
*/
	}];
	dataSet.color = "#7f8da9";
	dataSet.dataProvider = chartData;
	dataSet.categoryField = "date";

	chart.dataSets = [dataSet];

	// PANELS ///////////////////////////////////////////
	stockPanel = new AmCharts.StockPanel();
	stockPanel.title = "Value";

	// graph of first stock panel
	var graph = new AmCharts.StockGraph();
	graph.type = "candlestick";
	graph.openField = "open";
	graph.closeField = "close";
	graph.highField = "high";
	graph.lowField = "low";
	graph.valueField = "close";
	graph.lineColor = "#7f8da9";
	graph.fillColors = "#7f8da9";
	graph.negativeLineColor = "#db4c3c";
	graph.negativeFillColors = "#db4c3c";
	graph.fillAlphas = 1;
	graph.balloonText = "open:<b>[[open]]</b><br>close:<b>[[close]]</b><br>low:<b>[[low]]</b><br>high:<b>[[high]]</b>";
	graph.useDataSetColors = false;
	stockPanel.addStockGraph(graph);

	var stockLegend = new AmCharts.StockLegend();
	stockLegend.markerType = "none";
	stockLegend.markerSize = 0;
	stockLegend.valueTextRegular = undefined;
	stockLegend.valueWidth = 250;
	stockPanel.stockLegend = stockLegend;

	chart.panels = [stockPanel];


	// OTHER SETTINGS ////////////////////////////////////
	var sbsettings = new AmCharts.ChartScrollbarSettings();
	sbsettings.graph = graph;
	sbsettings.graphType = "line";
	sbsettings.usePeriod = "mm"; //聚合周期，指定为分钟，即不聚合
	chart.chartScrollbarSettings = sbsettings;

	// Enable pan events
	var panelsSettings = new AmCharts.PanelsSettings();
	panelsSettings.panEventsEnabled = true;
	chart.panelsSettings = panelsSettings;

	// CURSOR
	var cursorSettings = new AmCharts.ChartCursorSettings();
	cursorSettings.valueBalloonsEnabled = true;
	chart.chartCursorSettings = cursorSettings;

	// PERIOD SELECTOR ///////////////////////////////////
	var periodSelector = new AmCharts.PeriodSelector();
	periodSelector.position = "bottom";   //在底部显示时间周期控件

	//设置时间窗口的大小和显示格式
	periodSelector.dateFormat = "YYYY-MM-DD JJ:NN";
	periodSelector.inputFieldWidth = 150;

	periodSelector.periods = [{
		period: "mm",
		selected: true,
		count: 1,
		label: "1 minute"
	}];
	chart.periodSelector = periodSelector;

	chart.write('chartdiv');
	
    });

    // generate some random data, quite different range
    function generateChartData() {
	var json=document.getElementById('show').value;
	var obj = eval(json);  

	data_cycle=document.getElementById('data_cycle').value;

	//必须是正序
	for(var i=0;i<obj.length;i++) {  
		var time = obj[i].time;
		var open = obj[i].open;
		var close= obj[i].close;
		var high = obj[i].high;
		var low  = obj[i].low;

		var items = time.split(" ");
		var time_day  = items[0];
		var time_time = items[1];

		var obj_date = new Date(time_day);

		var items = time_time.split(":");
		obj_date.setHours(items[0], items[1]);
		//obj_date.setMinutes(items[1]);
		//alert(obj_date);
		
		chartData.push({
		date: obj_date,
		open: open,
		close: close,
		high: high,
		low: low,
		volume: 0
		});
	}  
    }

    // this method is called when chart is first inited as we listen for "dataUpdated" event
    function zoomChart() {
	// different zoom methods can be used - zoomToIndexes, zoomToDates, zoomToCategoryValues
	chart.zoomToIndexes(10, 20);
    }
