package com.latupa.stock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class StockInfo {
	String code;
	String market;
}

public class SelectStock {

	private static final Log log = LogFactory.getLog(StockPrice.class);
	
	//存储股票池
	private ArrayList<StockInfo> stock_pool = new ArrayList<StockInfo>();
	
	//存储选股结果列表
	private ArrayList<StockInfo> stock_result = new ArrayList<StockInfo>();
	
	//数据库连接
	public DBInst dbInst;
	
	private StockPriceNew spn;
	
	//数据库配置文件
	private static final String FLAG_FILE_DIR = "src/main/resources/";
	private static final String dbconf_file = "db.flag";
	
	public SelectStock() {
		this.dbInst = ConnectDB();
		this.spn = new StockPriceNew();
	}
	
	/**
	 * 获取指定市场的股票列表
	 * @param market
	 * @throws SQLException 
	 */
	public void LoadStocksFromMarket(String market) throws SQLException {
		
		log.info("load stock list of " + market);
		
		String table_name = spn.STOCK_PRICE_ED_TABLE_PRE + market;
		String sql = "select distinct(code) as code from " + table_name;
		log.info(sql);
		ResultSet rs = dbInst.selectSQL(sql);
		
		while (rs.next()) {
			
			StockInfo si = new StockInfo();
			si.code = rs.getString("code");
			si.market = market;
			stock_pool.add(si);
		}
		
		rs.close();
		return;
	}
	
	/**
	 * 显示需要处理的股票列表
	 */
	public void ShowStockPool() {
		System.out.println("stock pool");
		for (StockInfo si : stock_pool) {
			System.out.println(si.code + ":" + si.market);
		}
	}
	
	/**
	 * 显示选股结果
	 */
	public void ShowStockResult() {
		System.out.println("stock result");
		for (StockInfo si : stock_result) {
			System.out.println(si.code + ":" + si.market);
		}
	}
	
	
	/**
	 * 获取mysql连接
	 */
	public DBInst ConnectDB() {
		try {
			FileInputStream fis		= new FileInputStream(FLAG_FILE_DIR + dbconf_file);
	        InputStreamReader isr	= new InputStreamReader(fis, "utf8");
	        BufferedReader br		= new BufferedReader(isr);
	        
	        String line = br.readLine();
	        
	        br.close();
	        isr.close();
	        fis.close();
	        
	        if (line != null) {
	        	String arrs[] = line.split(" ");
	        	
	        	String host = arrs[0];
	        	String port = arrs[1];
	        	String db	= arrs[2];
	        	String user = arrs[3];
	        	String passwd = arrs[4];
	        	
	        	log.info("read db conf, host:" + host + ", port:" + port + ", db:" + db + ", user:" + user + ", passwd:" + passwd);
	        	DBInst dbInst = new DBInst("jdbc:mysql://" + host + ":" + port + "/" + db, user, passwd);
	        	return dbInst;
	        }
	        else {
	        	log.error("read " + FLAG_FILE_DIR + dbconf_file + " is null!");
	        	return null;
	        }
		}
		catch (Exception e) {
			log.error("read " + FLAG_FILE_DIR + dbconf_file + " failed!", e);
			return null;
		}
	}
	
	/**
	 * 选股主程序
	 * @throws SQLException 
	 */
	public void SelectProc(String day) throws SQLException {
		for (StockInfo si : stock_pool) {
			log.info("start proc " + si.code + ":" + si.market);
			
			StockFunc sf = new StockFunc(this.dbInst);
			sf.InitLoadStock(si.code, si.market);
			
			//缺乏当天数据，则跳过
			if (!sf.IsValid(day)) {
				log.info(si.code + " data not complete for day " + day);
				continue;
			}
			
			if (SelectMethodMa(sf, day)) {
				stock_result.add(si);
			}
		}
	}
	
	/**
	 * 按照突破60日均线的选股方式
	 * @param sf
	 * @param day
	 * @return
	 */
	private Boolean SelectMethodMa(StockFunc sf, String day) {
		
		//示例，比如突破60日均线，且60日均线高于120日均线
		ArrayList<Integer> mas = new ArrayList<Integer>();
		mas.add(new Integer(60));
		mas.add(new Integer(120));
		TreeMap<Integer, Double> maret = sf.ma(day, mas, 0);
		log.debug("open:" + sf.stock_price.get(day).open + ", close:" + sf.stock_price.get(day).close);
		
		if (sf.stock_price.get(day).close > maret.get(60) &&
				sf.stock_price.get(day).open < maret.get(60) &&
				maret.get(60) > maret.get(120)) {
			log.info("pick it");
			return true;
		}
		
		return false;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		SelectStock ss = new SelectStock();
		try {
			ss.LoadStocksFromMarket("sh");
			ss.LoadStocksFromMarket("hk");
			ss.ShowStockPool();
			ss.SelectProc("20121205");
			ss.ShowStockResult();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
