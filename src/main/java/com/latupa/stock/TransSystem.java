package com.latupa.stock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 股票信息
 * @author latupa
 *
 */
class StockDetail {
	
	//股票状态
	public static final int STOCK_STATUS_BUY	= 1;	//入场未盘活
	public static final int STOCK_STATUS_FREEUP	= 2;	//盘活
	public static final int STOCK_STATUS_SELL	= 3;	//出场
	
	String	market;			//股票市场
	String	code;			//股票代码
	String	buy_date;		//买入日期
	int		status;			//主要股票状态
	int		sub_status;		//次要状态
	String	sell_date;		//卖出日期
	double	price_buy;		//买入价格
	double	price_sell;		//卖出价格
}

/**
 * 交易系统
 * @author latupa
 *
 */
public class TransSystem {
	
	public static final Log log = LogFactory.getLog(StockPrice.class);
	
	//仓位范围
	public final int MIN_POSITION = 0;
	public final int MAX_POSITION = 10;
	
	//记录股票市场对应的大盘代码
	public static final HashMap<String, String> MARKET_CODE = new HashMap<String, String>(){
		/**
		 * 
		 */
		private static final long serialVersionUID = 8575945341628340022L;

		{
			put("sh", "000001");
			put("sz", "399001");
		}
	};
	
	//当前仓位[0,10]
	public int curt_position;
	
	//目标仓位[0,10]
	public int target_position;
	
	//记录当前仓位中的股票信息
	public ArrayList<StockDetail> stocks = new ArrayList<StockDetail>();
	
	//记录准备状态的股票信息
	public ArrayList<StockInfo> stocks_ready = new ArrayList<StockInfo>();
	
	//记录要处理的交易日列表
	public ArrayList<String> trans_days = new ArrayList<String>();
	
	//数据库连接
	public DBInst dbInst;
	
	public StockPriceNew spn;
	
	//指定处理的证券市场
	public String market;
	
	//指定计算的起始、结束时间
	public String date_s;
	public String date_e;
	
	//数据库配置文件
	public static final String FLAG_FILE_DIR = "src/main/resources/";
	public static final String dbconf_file = "db.flag";
	
	public TransSystem(String market, String date_s, String date_e) {
		
		log.info("start proc " + market + " from " + date_s + " to " + date_e);
		
		this.market = market;
		this.date_s = date_s;
		this.date_e = date_e;
		
		if (!MARKET_CODE.containsKey(market)) {
			log.error("init market " + market + " is error!");
			System.exit(0);
		}
		
		this.dbInst = ConnectDB();
		this.spn = new StockPriceNew(dbInst);
		
		this.curt_position = 0;
		this.target_position = 0;
		
		//初次需要更新目标仓位
		TargetPositionUpdate();
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
	
	/*
	 * 加载指定股票市场的交易日列表
	 */
	public void LoadTransDays() {
		
		String table_name = spn.STOCK_PRICE_ED_TABLE_PRE + this.market;
		String sql = "select DATE_FORMAT(day,'%Y%m%d') as day from " + table_name + " where code = '" + MARKET_CODE.get(this.market) + "' " +
				"and is_holiday = 0 " +
				"and day >= '" + this.date_s + "' " +
				"and day <= '" + this.date_e + "' " +
				"order by day asc";
		ResultSet rs = dbInst.selectSQL(sql);
		
		try {
			while (rs.next()) {
				String date	= rs.getString("day");
				trans_days.add(date);
			}
			
			rs.close();
		} catch (Exception e) {
			log.error("sql excute failed! sql:" + sql, e);
			System.exit(0);
		}
	}
	
	public void ShowTransDays() {
		for (int i = 0; i < trans_days.size(); i++) {
			System.out.println(trans_days.get(i));
		}
	}
	
	/**
	 * 买入状态股票的处理函数
	 */
	public void StockStatusBuyProc() {
		System.out.println("buy proc");
	}
	
	/**
	 * 盘活状态股票的处理函数
	 */
	public void StockStatusFreeupProc() {
		System.out.println("freeup proc");
	}
	
	/**
	 * 卖出状态股票的处理函数
	 */
	public void StockStatusSellProc() {
		System.out.println("sell proc");
	}
	
	/**
	 * 选股处理
	 */
	public void StockChooseProc() {
		System.out.println("choose proc");
	}
	
	/*
	 * 计算目标仓位
	 */
	public void TargetPositionUpdate() {
		if (this.curt_position <= 3) {
			this.target_position += 2;
		}
	}
	
	/**
	 * 从选股结果中抽取股票
	 * @param stock_pool 选股结果列表
	 * @param num 需要的股票数
	 */
	public void PickStockFromPool(ArrayList<StockInfo> stock_pool, int num) {
		
		int count = stock_pool.size();
		
		//如果需要的股票数多于股票池，那么直接全部返回，否则随机选择
		if (count <= num) {
			stocks_ready = stock_pool;
		}
		else {
			Random rand = new Random();
			
			HashMap<Integer, Boolean> index_exist = new HashMap<Integer, Boolean>();
			for (int i = 0; i < num; i++) {
				int index = rand.nextInt(count);
				while (index_exist.containsKey(index)) {
					index = rand.nextInt(count);
				}
				index_exist.put(index, true);
				
				stocks_ready.add(stock_pool.get(index));
			}
		}
	}
	
	/**
	 * 执行选股，作为准备买入的股票
	 * @param date
	 */
	public void ChooseStocks(String date) {
		
		if (this.target_position > this.curt_position) {
			
			log.info("need to choose " + (this.target_position - this.curt_position) + " stocks");
		
			SelectStock ss = new SelectStock();
			try {
				ss.LoadStocksFromMarket(this.market);
	//			ss.ShowStockPool();
				ss.SelectProc(date);
				ss.ShowStockResult();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(0);
			}
			
			PickStockFromPool(ss.stock_result, this.target_position - this.curt_position);
			
			System.out.println("real result:");
			for (StockInfo si : this.stocks_ready) {
				System.out.println(si.code + ":" + si.market);
			}
		}
	}
	
	
	/**
	 * 买入准备好的股票
	 * @param date
	 */
	public void BuyStocks(String date) {
		
		if (this.stocks_ready.size() > 0) {
			
			for (StockInfo si : stocks_ready) {
				
				log.info("buy stock " + si.code);
				
				//获取股票相关信息记录在内存中
				StockDetail sd	= new StockDetail();
				sd.buy_date 	= date;
				sd.code			= si.code;
				sd.market		= si.market;
				sd.status		= StockDetail.STOCK_STATUS_BUY;
				
				StockPriceNew spn = new StockPriceNew(dbInst);
		        PriceRecord pr = spn.GetStockPriceFromDB(sd.market, sd.code, sd.buy_date);
		        if (pr == null) {
		        	log.error("get stock price failed! code:" + sd.code + " date:" + sd.buy_date);
		        	System.exit(0);
		        }
		        sd.price_buy	= pr.open;
		        stocks.add(sd);
				
		        
		        //股票交易信息记录到数据库
				TransDetail td = new TransDetail("test", this.dbInst);
				String record = date + "," + si.code + "," + "1" + "," + si.market;
				td.RecordParse(record);
				td.StoreRecordsToDB();
				td.ScanRecordsToDB();
				
				this.curt_position++;
			}
			
			this.stocks_ready.clear();
		}
	}
	
	/*
	 * 处理每天的买卖操作
	 */
	public void DailyRoute() throws ParseException {
		
		Iterator<String> it = trans_days.iterator();
		
		while (it.hasNext()) {
			String date = (String)it.next();
			log.info("proc " + date);
			
			//首先买入READY的股票
			BuyStocks(date);
			
			//入场股票处理
			StockStatusBuyProc();
			
			//盘活股票处理
			StockStatusFreeupProc();
			
			//卖出股票处理
			StockStatusSellProc();
			
			//执行选股
			ChooseStocks(date);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
