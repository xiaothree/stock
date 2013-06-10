    var chart;
    var chartData = [];

    AmCharts.ready(function () {
	// generate some random data first
	generateChartData();

	// SERIAL CHART    
	chart = new AmCharts.AmSerialChart();
	chart.pathToImages = "amcharts/images/";
	chart.zoomOutButton = {
	    backgroundColor: '#000000',
	    backgroundAlpha: 0.25
	};
	chart.dataProvider = chartData;
	chart.categoryField = "date";
	chart.zoomOut();

	// listen for "dataUpdated" event (fired when chart is inited) and call zoomChart method when it happens
	chart.addListener("dataUpdated", zoomChart);

	// AXES
	// category                
	var categoryAxis = chart.categoryAxis;
	categoryAxis.parseDates = true; // as our data is date-based, we set parseDates to true
	categoryAxis.minPeriod = "DD"; // our data is daily, so we set minPeriod to DD
	categoryAxis.dashLength = 2;
	categoryAxis.gridAlpha = 0.15;
	categoryAxis.axisColor = "#DADADA";

	// first value axis (on the left)
	var valueAxis1 = new AmCharts.ValueAxis();
	valueAxis1.axisColor = "#FF4500";
	valueAxis1.axisThickness = 5;
	valueAxis1.gridAlpha = 0;
	chart.addValueAxis(valueAxis1);

	// second value axis (on the right) 
	var valueAxis2 = new AmCharts.ValueAxis();
	valueAxis2.position = "right"; // this line makes the axis to appear on the right
	valueAxis2.axisColor = "#FFD700";
	valueAxis2.gridAlpha = 0;
	valueAxis2.axisThickness = 5;
	chart.addValueAxis(valueAxis2);

	// third value axis (on the left, detached)
	valueAxis3 = new AmCharts.ValueAxis();
	valueAxis3.offset = 50; // this line makes the axis to appear detached from plot area
	valueAxis3.gridAlpha = 0;
	valueAxis3.axisColor = "#008000";
	valueAxis3.axisThickness = 5;
	chart.addValueAxis(valueAxis3);

	// fourth value axis (on the left, detached)
	valueAxis4 = new AmCharts.ValueAxis();
	valueAxis4.position = "right"; // this line makes the axis to appear on the right
	valueAxis4.offset = 50; // this line makes the axis to appear detached from plot area
	valueAxis4.gridAlpha = 0;
	valueAxis4.axisColor = "#000080";
	valueAxis4.axisThickness = 5;
	chart.addValueAxis(valueAxis4);

	// GRAPHS
	// first graph
	var graph1 = new AmCharts.AmGraph();
	graph1.valueAxis = valueAxis1; // we have to indicate which value axis should be used
	graph1.title = "上证指数";
	graph1.valueField = "szzs";
	graph1.bullet = "round";
	graph1.hideBulletsCount = 30;
	graph1.lineThickness = 4;
	chart.addGraph(graph1);

	// second graph                
	var graph2 = new AmCharts.AmGraph();
	graph2.valueAxis = valueAxis2; // we have to indicate which value axis should be used
	graph2.title = "深圳成指";
	graph2.valueField = "szcz";
	graph2.bullet = "square";
	graph2.hideBulletsCount = 30;
	graph2.lineThickness = 4;
	chart.addGraph(graph2);
	chart.hideGraph(graph2);

	// third graph
	var graph3 = new AmCharts.AmGraph();
	graph3.valueAxis = valueAxis3; // we have to indicate which value axis should be used
	graph3.valueField = "rate";
	graph3.title = "仓位";
	graph3.bullet = "triangleUp";
	graph3.hideBulletsCount = 30;
	graph3.lineThickness = 2;
	chart.addGraph(graph3);

	// four graph
	var graph4 = new AmCharts.AmGraph();
	graph4.valueAxis = valueAxis4; // we have to indicate which value axis should be used
	graph4.valueField = "profit";
	graph4.title = "利润";
	graph4.bullet = "triangleUp";
	graph4.hideBulletsCount = 30;
	graph4.lineThickness = 2;
	chart.addGraph(graph4);
	chart.hideGraph(graph4);

	// CURSOR
	var chartCursor = new AmCharts.ChartCursor();
	chartCursor.cursorPosition = "mouse";
	chart.addChartCursor(chartCursor);

	// SCROLLBAR
	var chartScrollbar = new AmCharts.ChartScrollbar();
	chart.addChartScrollbar(chartScrollbar);

	// LEGEND
	var legend = new AmCharts.AmLegend();
	legend.marginLeft = 110;
	chart.addLegend(legend);

	// WRITE
	chart.write("chartdiv");
    });

    // generate some random data, quite different range
    function generateChartData() {
	var json=document.getElementById('show').value;
	var obj = eval(json);  
	for(var i=0;i<obj.length;i++) {  
		var date = obj[i].date;
		var szzs = obj[i].szzs;
		var szcz = obj[i].szcz;
		var rate = obj[i].rate;
		var profit = obj[i].profit;

		var obj_date = new Date(date);

		chartData.push({
		date: obj_date,
		szzs: szzs,
		szcz: szcz,
		rate: rate,
		profit: profit
		});
	}  
    }

    // this method is called when chart is first inited as we listen for "dataUpdated" event
    function zoomChart() {
	// different zoom methods can be used - zoomToIndexes, zoomToDates, zoomToCategoryValues
	chart.zoomToIndexes(10, 20);
    }
