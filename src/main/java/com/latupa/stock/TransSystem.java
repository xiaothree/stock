package com.latupa.stock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 股票信息
 * @author latupa
 *
 */
class StockDetail {
	String market;		//股票市场
	String code;		//股票代码
	String buy_date;	//买入日期
	String sell_date;	//卖出日期
	double price_buy;	//买入价格
	double price_sell;	//卖出价格
}

/**
 * 交易系统
 * @author latupa
 *
 */
public class TransSystem {
	
	private static final Log log = LogFactory.getLog(StockPrice.class);
	
	//股票状态
	private final int STOCK_STATUS_READY	= 1;	//准备
	private final int STOCK_STATUS_BUY		= 2;	//入场
	private final int STOCK_STATUS_FREEUP	= 3;	//盘活
	private final int STOCK_STATUS_BULLS	= 4;	//多头
	private final int STOCK_STATUS_SELL		= 5;	//出场
	
	//仓位范围
	private final int MAX_POSITION = 10;
	private final int MIN_POSITION = 0;
	
	//当前仓位[0,10]
	private int position = MIN_POSITION;
	
	//记录当前仓位中的股票信息
	private ArrayList<StockDetail> stocks = new ArrayList<StockDetail>();
	
	//数据库连接
	public DBInst dbInst;
	
	//数据库配置文件
	private static final String FLAG_FILE_DIR = "src/main/resources/";
	private static final String dbconf_file = "db.flag";
	
	public TransSystem() {
		this.dbInst = ConnectDB();
	}
	
	/**
	 * 从配置文件获取mysql连接
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
	 * 准备状态股票的处理函数
	 */
	public void StockStatusReadyProc() {
		
	}
	
	/**
	 * 买入状态股票的处理函数
	 */
	public void StockStatusBuyProc() {
		
	}
	
	/**
	 * 盘活状态股票的处理函数
	 */
	public void StockStatusFreeup() {
		
	}
	
	/**
	 * 多头状态股票的处理函数
	 */
	public void StockStatusBulls() {
		
	}
	
	/**
	 * 卖出状态股票的处理函数
	 */
	public void StockStatusSell() {
		
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
