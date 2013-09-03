package com.latupa.stock;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 获取大盘、股票的数据
 * @author Administrator
 * TODO
 * 1.保留两份stock_price，一份为原始的，另一份为除权处理之后的
 * 2.查询的时候，优先使用除权处理之后的stock_price
 */

public class StockPrice {

	private static final Log log = LogFactory.getLog(StockPrice.class);
	
	//记录股票历史价格{{market,code}, {date, price}}
	public HashMap<String, HashMap<String, PriceRecord>> stock_map = new HashMap<String, HashMap<String, PriceRecord>>();
	
	//yahoo接口：股票历史数据
	private static final String YAHOO_URL	= "http://table.finance.yahoo.com/table.csv?s=";
	//sina接口：实时交易数据
	private static final String SINA_URL	= "http://hq.sinajs.cn/list=";
	
	//股票价格表名前缀
	private static final String STOCK_PRICE_TABLE_PRE = "stock_price__";
	
	//股票除权后价格表名前缀
	private static final String STOCK_PRICE_ED_TABLE_PRE = "stock_price_ed__";
	
	//股票除权表名前缀
	private static final String STOCK_EXDIVIDE_TABLE_PRE = "stock_exdivide__";
	
	//上证综指代号
	public static final String CODE_SZZS	= "000001";
	//深圳成指代号
	public static final String CODE_SZCZ	= "399001";
	
	//标识所有股票
	public static final String ALL_STOCK_CODE	= "000000";

	//更新数据的时间
	public static final int UPDATE_ALL	= 0;	//更新实时和历史
	public static final int UPDATE_RT	= 1;	//更新实时
	public static final int UPDATE_HIS	= 2;	//更新历史
	public static final int UPDATE_MAX	= 3;	//最大值
	
	//更新数据的方式
	public static final int UPDATE_STOCK_TRANSED		= 0;	//更新所有交易过的股票
	public static final int UPDATE_STOCK_TRANSED_NEW	= 1;	//更新所有交易过，但是没有更新过数据的股票（即新交易的股票）
	public static final int UPDATE_STOCK_TRANSED_CODE	= 2;	//更新交易过的某只股票
	public static final int UPDATE_STOCK_CODE			= 3;    //更新某只股票
	public static final int UPDATE_STOCK_MAX			= 4;	//最大值
	
	//数据库连接
	public DBInst dbInst;  
	
	public class PriceRecord {
		double open;
		double close;
	}
	
	public class ExDivide {
		double give;
		double right;
		double right_price;
		double bonus;
	}
	
	public StockPrice(DBInst dbInst) {
		this.dbInst = dbInst;
	}
	
	public float toFloat(byte[] b) { 
		// 4 bytes
        int accum = 0; 
        for ( int shiftBy = 0; shiftBy < 4; shiftBy++ ) { 
                accum |= (b[shiftBy] & 0xff) << shiftBy * 8; 
        } 
        return Float.intBitsToFloat(accum); 
	}	
	 
	public long byte2int(byte[] res) { 
		// 一个byte数据左移24位变成0xaa000000，再右移8位变成0x00aa0000 
		long targets = (res[0] & 0xff) | ((res[1] << 8) & 0xff00) // | 表示安位或 
			 | ((res[2] << 24) >>> 8) | (res[3] << 24); 
		return targets; 
	} 
	 
	public static int getInt(byte[] bytes) {
	    return (0xff & bytes[0]) | (0xff00 & (bytes[1] << 8)) | (0xff0000 & (bytes[2] << 16)) | (0xff000000 & (bytes[3] << 24));
	}
	
	public static short getShort(byte[] bytes) {
		return (short) ((0xff & bytes[0]) | (0xff00 & (bytes[1] << 8)));
	}
	
	/**
	 * 处理除权文件
	 * @param file
	 */
	public void ProcExDivide(String market, String code, String file) {
		log.info("start proc ex-dividend " + file);
		
		TreeMap<String, ExDivide> ex_divide_map = ParseExDivide(file);
		StoreExDivideToDB(market, code, ex_divide_map, false);
		UpdateStockPriceByExDivide(market, code, ex_divide_map);
	}
	
	/**
	 * 根据除权信息修改股票历史价格
	 * @param market
	 * @param code
	 * @param ex_divide_map
	 */
	public void UpdateStockPriceByExDivide(String market, String code, TreeMap<String, ExDivide> ex_divide_map) {
		
		log.info("start update stock price by ex-divide! market:" + market + ", code" + code);
		String table_name = STOCK_PRICE_TABLE_PRE + market + "_" + code;
		String ed_table_name = STOCK_PRICE_ED_TABLE_PRE + market + "_" + code;
		
		String sql;
		
		sql = "drop table if exists " + ed_table_name;
		log.info(sql);
		dbInst.updateSQL(sql);
		
		sql = "create table " + ed_table_name + " select * from " + table_name;
		log.info(sql);
		dbInst.updateSQL(sql);
		
		// 如果没有除权表，则不需要执行除权
		if (ex_divide_map == null) {
			return;
		}
		
		for (String date : ex_divide_map.keySet().toArray(new String[0])) {
			ExDivide ex_divide = ex_divide_map.get(date);
			double give 		= ex_divide.give;
			double right		= ex_divide.right;
			double right_price	= ex_divide.right_price;
			double bonus		= ex_divide.bonus;
			
//			//送股、分红
//			if (give != 0 || bonus !=0) {
//				sql = "update " + ed_table_name + " set open = open / " + String.valueOf(1 + give) + " - " + String.valueOf(bonus) +
//						", close = close / " + String.valueOf(1 + give) + " - " + String.valueOf(bonus) +
//						" where date < '" + date + "'"; 
//			}
//			//配股
//			else if (right != 0) {
//				sql = "update " + ed_table_name + " set open = (open + " + String.valueOf(right_price * right) + ") / " + String.valueOf(1 + right) +
//						", close = (close + " + String.valueOf(right_price * right) + ") / " + String.valueOf(1 + right) +
//						" where date < '" + date + "'"; 
//			}
			
			//除权价=(收盘价+配股比例×配股价-每股所派现金)÷(1+送股比例+配股比例)
			sql = "update " + ed_table_name + " set open = (open + " + String.valueOf(right_price * right) + " - " + String.valueOf(bonus) + ") / " + String.valueOf(1 + give + right) +
					", close = (close + " + String.valueOf(right_price * right) + " - " + String.valueOf(bonus) + ") / " + String.valueOf(1 + give + right) +
					" where date < '" + date + "'"; 
			
			
			log.info(sql);
			dbInst.updateSQL(sql);
		}
	}
	
	/**
	 * 从数据库读取除权信息
	 * @param market
	 * @param code
	 * @return
	 */
	public TreeMap<String, ExDivide> ReadExDivideFromDB(String market, String code) {
		log.info("start read ex-divide from db... " + "market:" + market + ", code:" + code);
		
		TreeMap<String, ExDivide> ex_divide_map = new TreeMap<String, ExDivide>();
		
		String table_name = STOCK_EXDIVIDE_TABLE_PRE + market + "_" + code;
		String sql = "select date, give, bonus from " + table_name;
		
		ResultSet rs = dbInst.selectSQL(sql);
		
		if (rs == null) {
			return null;
		}
		
		try {
			while (rs.next()) {
				String date		= rs.getString("date");
				
			    ExDivide ex_divide = new ExDivide();
			    ex_divide.give 		= rs.getDouble("give");
			    ex_divide.bonus 	= rs.getDouble("bonus");
			    
			    ex_divide_map.put(date, ex_divide);
			}
			
			rs.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			return null;
		}
		
		return ex_divide_map;
	}
	
	/**
	 * 除权数据入库
	 * @param market
	 * @param code
	 * @param ex_divide_map
	 * @param clean
	 */
	public void StoreExDivideToDB(String market, String code, TreeMap<String, ExDivide> ex_divide_map, boolean clean) {
		log.info("start store ex-divide to db...");
		
		for (String key : ex_divide_map.keySet().toArray(new String[0])) {
			
			String date = key;
			
			String table_name = STOCK_EXDIVIDE_TABLE_PRE + market + "_" + code;
			String sql = "create table if not exists " + table_name + 
					"(`date` DATE not null default '0000-00-00', " +
					"`give` double NOT NULL default '0', " +
					"`right` double NOT NULL default '0', " +
					"`right_price` double NOT NULL default '0', " +
					"`bonus` double NOT NULL default '0', " +
					"PRIMARY KEY (`date`)" +
					") ENGINE=MyISAM DEFAULT CHARSET=utf8";	
			
			dbInst.updateSQL(sql);
			if (clean) {
				sql = "delete from " + table_name;
				log.info("	" + sql);
				dbInst.updateSQL(sql);
			}
			
			ExDivide ex_divide = ex_divide_map.get(date);
				
			sql = "insert into " + table_name + "(`date`, `give`, `right`, `right_price`, `bonus`) values " +
					"('" + date + 
					"', " + ex_divide.give +
					"," + ex_divide.right +
					"," + ex_divide.right_price +
					"," + ex_divide.bonus + ") " +
					"ON DUPLICATE KEY UPDATE " +
					"`give` = " + ex_divide.give + ", " +
					"`right` = " + ex_divide.right + ", " +
					"`right_price` = " + ex_divide.right_price + ", " +
					"`bonus` = " + ex_divide.bonus;
			
			dbInst.updateSQL(sql);
		}
	}
	
	/**
	 * 解析除权文件
	 * @param file
	 */
	public TreeMap<String, ExDivide> ParseExDivide(String file) {
		
		TreeMap<String, ExDivide> ex_divide_map = new TreeMap<String, ExDivide>();
		
		try {
	        FileInputStream fis		= new FileInputStream(file);
	        InputStreamReader isr	= new InputStreamReader(fis, "gbk");
	        BufferedReader br		= new BufferedReader(isr);
	        
	        String line	= "";
	        String[] arrs	= null;
	        
	        Pattern pattern_date = Pattern.compile("^\\d{8}$");
	        
	        int line_num = 0;
	        while ((line = br.readLine()) != null) {
	        	line_num++;
	        	
	            arrs = line.split("\\t");
	            
	            String date = arrs[0].replace("-", "");
	            
	            Matcher matcher = pattern_date.matcher(date);
	            if (! matcher.matches()) {
	            	log.error("date format error for line " + line_num + "! date:" + arrs[0]);
	            	continue;
	            }
	            
	            ExDivide ex_divide = new ExDivide();
	            ex_divide.give 			= Double.parseDouble(arrs[1]);
	            ex_divide.right 		= Double.parseDouble(arrs[2]);
	            ex_divide.right_price 	= Double.parseDouble(arrs[3]);
	            ex_divide.bonus 		= Double.parseDouble(arrs[4]);
	            
	            ex_divide_map.put(date, ex_divide);
	        }
	        
	        br.close();
	        isr.close();
	        fis.close();
		}
		catch (Exception e) {
				log.error("file occur error! file:" + file, e);
				System.exit(0);
		}
		
		return ex_divide_map;
	}
	
	
	/**
	 * 直接从DB获取
	 * @param code
	 * @param date
	 * @return
	 */
	public PriceRecord GetStockPriceFromDB(String market, String code, String date) {
		String table_name = STOCK_PRICE_ED_TABLE_PRE + market + "_" + code;
		
		PriceRecord pr = null;
		
		String sql = null;
		ResultSet rs = null;
		
//		//先查询对应的表是否存在
//		String sql 	= "show tables like '" + table_name + "'";
//		ResultSet rs = dbInst.selectSQL(sql);
//				
		try {
//			//如果不存在，则立即返回
//			if (!rs.next()) {
//				return pr;
//			}
//			else {
				sql	= "select open as open, close as close from " + table_name + " where date = '" + date + "'";
				
				rs = dbInst.selectSQL(sql);
				if (rs.next()) {

					pr = new PriceRecord();
					pr.open 	= rs.getDouble("open");
					pr.close 	= rs.getDouble("close");
				}
				
				rs.close();
			//}
		} 
		catch (Exception e) {
			log.error("sql excute failed! sql:" + sql, e);
			System.exit(0);
		}
		
		return pr;
	}
	
	/**
	 * 获取指定股票、日期的价格
	 * @param code
	 * @param date
	 * @return
	 * @throws Exception 
	 */
	public PriceRecord GetStockPrice(String code, String market, String date) throws Exception {
		
		String key = market + "," + code;
		
		if (stock_map.containsKey(key)) {
			HashMap<String, PriceRecord> stock_price = stock_map.get(key);
			if (stock_price.containsKey(date)) {
				return stock_price.get(date);
			}
		}
		
		LoadStockPrice(StockPrice.UPDATE_STOCK_CODE, code, market);
		
		if (stock_map.containsKey(key)) {
			HashMap<String, PriceRecord> stock_price = stock_map.get(key);
			if (stock_price.containsKey(date)) {
				return stock_price.get(date);
			}
		}
		
		throw new Exception("query stock price failed! market: " + market + " code:" + code + " date:" + date);
	}
	
	
	/**
	 * 获取指定实时股票（含大盘）数据
	 * @param market
	 * @param code
	 * @throws Exception
	 */
	public void QueryStockRT(String market, String code) throws Exception {
		if (code == StockPrice.CODE_SZCZ || code == StockPrice.CODE_SZZS) {
			QueryStockRTIndex(market, code);
		}
		else {
			QueryStockRTPrice(market, code);
		}
	}
	/**
	 * 获取指定市场的当日指数 ，参数为：上证综指（sh-000001），深证成指（sz-399001）
	 * @param market
	 * @param code
	 * @throws Exception 
	 */
	public void QueryStockRTIndex(String market, String code) throws Exception {
		String url_actual	= SINA_URL + "s_" + market + code;
		
		log.info("request url:" + url_actual);
		
		URL url = new URL(url_actual);
		ArrayList<String> raws	= HttpUrl(url);
		
		if (raws.size() > 1) {
			throw new Exception(url_actual + " return lines error");
		}
		
		String raw = raws.get(0);
		String[] arrs	= raw.split("\"");
		String value	= arrs[1];
		arrs	= value.split(",");
		
		PriceRecord pr = new PriceRecord();
		pr.open		= Double.parseDouble(arrs[1]);//没有开盘数据，用收盘替代
		pr.close	= Double.parseDouble(arrs[1]);
		
		SimpleDateFormat  sdf	= new SimpleDateFormat("yyyyMMdd");  
		String stock_date	= sdf.format(new Date());  
		
		String key = market + "," + code;
		if (stock_map.containsKey(key)) {
			stock_map.get(key).put(stock_date, pr);
		}
		else {
			HashMap<String, PriceRecord> stock_price = new HashMap<String, PriceRecord>();
			stock_price.put(stock_date, pr);
			stock_map.put(key, stock_price);
		}
	}
	
	/**
	 * 获取指定股票的当日价格数据 
	 * @param market
	 * @param code
	 * @throws Exception 
	 */
	public void QueryStockRTPrice(String market, String code) throws Exception {
		
		String url_actual	= SINA_URL + market + code;
		
		log.info("request url:" + url_actual);
		
		URL url = new URL(url_actual);
		ArrayList<String> raws	= HttpUrl(url);
		
		if (raws.size() > 1) {
			throw new Exception(url_actual + " return lines error");
		}
		
		String raw = raws.get(0);
		String[] arrs	= raw.split("\"");
		
		String value	= arrs[1];
		arrs	= value.split(",");
		
		if (arrs.length <= 1) {
			log.info("request url:" + url_actual + " return null");
			return;
		}
		
		PriceRecord pr = new PriceRecord();
		pr.open		= Double.parseDouble(arrs[1]);
		pr.close	= Double.parseDouble(arrs[3]);
		
		SimpleDateFormat  sdf	= new SimpleDateFormat("yyyyMMdd");  
		String stock_date	= sdf.format(new Date());  
		
		String key = market + "," + code;
		if (stock_map.containsKey(key)) {
			stock_map.get(key).put(stock_date, pr);
		}
		else {
			HashMap<String, PriceRecord> stock_price = new HashMap<String, PriceRecord>();
			stock_price.put(stock_date, pr);
			stock_map.put(key, stock_price);
		}
	}
	
	/**
	 * 处理DAD文件中的一个股票的所有天的数据
	 * @param dis
	 * @param code
	 * @param market
	 * @param need_proc true-需要入库；false-不需要入库
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */
	public byte[] ParseStock(DataInputStream dis, String code, String market, boolean need_proc) throws IOException, ParseException {
		
		byte[] byte4 = new byte[4];
		
		String sdate;
		Double open;
		Double close;
		long timestamp;
		
		//date->PriceRecord
		HashMap<String, PriceRecord> stock_price = new HashMap<String, PriceRecord>();
		
		Date date;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		
		String sdate_s = null;
		String sdate_e = null;
		
		//32-47
		dis.read(byte4);
		while (getInt(byte4) != 0xFFFFFFFF) {
			
			timestamp = byte2int(byte4);
			date = new Date(timestamp * 1000);
			sdate = sdf.format(date);
			
			if (sdate_s == null) {
				sdate_s = sdate;
			}
			sdate_e = sdate;
			
			dis.read(byte4);
			open = Double.parseDouble(toFloat(byte4) + "");
			
			dis.skipBytes(8);
			
			//48-63
			dis.read(byte4);
			close = Double.parseDouble(toFloat(byte4) + "");
			
			dis.skipBytes(12);
			
            PriceRecord pr = new PriceRecord();
            pr.open		= open;
			pr.close	= close;
			
			stock_price.put(sdate, pr);
			
//			System.out.println("date:" + sdate + " open:" + open + " close:" + close);
			
			dis.read(byte4);
		}
		
		//补全非交易日的数据（用离非交易日最近的交易日数据补齐）
		date = sdf.parse(sdate_s);
		
		sdate = sdf.format(date);
		PriceRecord last_pr = null;
		while (!sdate.equals(sdate_e)) {
			if (!stock_price.containsKey(sdate)) {
				stock_price.put(sdate, last_pr);
			}
			
			last_pr	= stock_price.get(sdate);
			date.setTime((date.getTime() / 1000 + 86400) * 1000);
			sdate	= sdf.format(date);
		}
		
		String key = market + "," + code;
		if (need_proc == true) {
			stock_map.put(key, stock_price);
		}
		
		return byte4;
	}
	
	/**
	 * 导入历史股票价格数据（DAD）
	 * @param file_name
	 * @throws IOException
	 * @throws ParseException
	 */
	public void ImportHistoryPrice(String file_name) throws IOException, ParseException {
		//DataInputStream dis = new DataInputStream(new FileInputStream(new File("D:\\Installed App\\dzh2\\SUPERSTK.DAD")));
		DataInputStream dis = new DataInputStream(new FileInputStream(new File(file_name)));
		
		byte[] byte2 = new byte[2];
		byte[] byte4 = new byte[4];
		byte[] byte6 = new byte[6];
		byte[] byte8 = new byte[8];
		byte[] byte16 = new byte[16];
		
		String market;
		String code;
		String name;
		
		int num = 0;
		
		dis.read(byte16);
		while (dis.available() > 0) {
			
			//0-15
			dis.read(byte4);
			
			// new stock
			while (getInt(byte4) == 0xFFFFFFFF) {
				
				boolean need_proc = true;
				
				//4-15
				dis.read(byte2);
				if (getShort(byte2) == 0x0000) {
					break;
				}
				market = new String(byte2);
				
				dis.read(byte6);
				code = new String(byte6);
				
				if (market.equals("SH")) {
					market = TransDetailRecord.MARKET_SH;
				}
				else if (market.equals("SZ")) {
					market = TransDetailRecord.MARKET_SZ;
				}
				else {
					log.info("skip wrong stock! market:" + market + " code:" + code);
					need_proc = false;
				}
				
				num++;

				dis.skipBytes(4);
				
				//16-31
				dis.skipBytes(4);
				
				dis.read(byte8);
				name = new String(byte8, "gbk");
				
				dis.skipBytes(4);
						
				System.out.println("market:" + market + " code:" + code + " name:" + name);
				byte4 = ParseStock(dis, code, market, need_proc);
				
				
				if (num % 100 == 0) {
					//存入数据库
					log.info("    store code to db");
					//StoreStockPriceToDB(ALL_STOCK_CODE, false);
					
					stock_map.clear();
				}
			}
		}
		
		dis.close();
		
		//剩下的存入数据库
		log.info("    store code to db");
		//StoreStockPriceToDB(ALL_STOCK_CODE, false);
		
		stock_map.clear();

	}
	
	
	/**
	 * 获取指定股票的历史价格数据
	 * 如果是大盘，参数为：上证综指（sh-000001），深证成指（sz-399001）
	 * @param market
	 * @param code
	 * @throws Exception 
	 */
	public void QueryStockHistoryPrice(String market, String code) throws Exception {
		
		//date->PriceRecord
		HashMap<String, PriceRecord> stock_price	= new HashMap<String, PriceRecord>();
		
		//先获取历史数据
		String url_actual = YAHOO_URL + code + "." + (market.equals(TransDetailRecord.MARKET_SH) ? "ss" : market);
		
		log.info("request url:" + url_actual);
		
		URL url = new URL(url_actual);
		ArrayList<String> raws	= HttpUrl(url);
		
		if (raws.size() == 0) {
			throw new Exception(url_actual + " return null");
		}
		
		Pattern pattern_date = Pattern.compile("^\\d{8}$");
		Matcher matcher		= null;
		
		//用于记录股票价格的起始、结束日期，便于后面补空
        String date_s	= null;
        
        SimpleDateFormat  sdf	= new SimpleDateFormat("yyyyMMdd");  
		String date_e	= sdf.format(new Date());  
        
		for (int i = 0; i < raws.size(); i++) {
			String raw = raws.get(i);
			String[] arrs	= raw.split(",");
			String stock_date	= arrs[0].replace("-", "");
			
			matcher = pattern_date.matcher(stock_date);
            if (! matcher.matches()) {
            	continue;
            }
            
            //结束时期不取接口返回的时间，用当前日期，
            /**
             * 交易日[1-5]	休息日[6-10]
             * 10号执行程序，那么6-9日的数据为空
             */
//            if (date_e == null) {
//            	date_e = stock_date;
//            }
            
            date_s = stock_date;
			
            PriceRecord pr = new PriceRecord();
            pr.open		= Double.parseDouble(arrs[1]);
			pr.close	= Double.parseDouble(arrs[4]);
			
			stock_price.put(stock_date, pr);
		}
		
		//补全非交易日的数据（用离非交易日最近的交易日数据补齐）
		Date date = sdf.parse(date_s);
		
		String date_str	= sdf.format(date);
		PriceRecord last_pr = null;
		while (!date_str.equals(date_e)) {
			if (!stock_price.containsKey(date_str)) {
				stock_price.put(date_str, last_pr);
			}
			
			last_pr	= stock_price.get(date_str);
			date.setTime((date.getTime() / 1000 + 86400) * 1000);
			date_str	= sdf.format(date);
		}
		
		String key = market + "," + code;
		stock_map.put(key, stock_price);
	}
	
	/**
	 * 通过http获取指定url的返回值
	 * @param url
	 * @return
	 */
	public ArrayList<String> HttpUrl(URL url) {
		
		ArrayList<String> list = new ArrayList<String>();
		
		try {
			InputStream in = url.openStream();
			BufferedReader bin = new BufferedReader(new InputStreamReader(in, "gbk"));
			String s = null;
			while ((s = bin.readLine()) != null) {
				System.out.println(s);
				list.add(s);
			}
			bin.close();
		}
		catch (Exception e) {
			log.error("request failed! url:" + url.toString(), e);
		}
				
		return list;
	}
	
	/**
	 * 把内存中的指定股票的价格存入到数据库中
	 * @param code 如果为"000000"则表示内存中的全部存入到数据库
	 * @param clean 如果为true则先清空表
	 */
	public void StoreStockPriceToDB(String code, boolean clean) {
		log.info("start store price to db...");
		
		if (!code.equals(ALL_STOCK_CODE) && 
				!stock_map.containsKey(TransDetailRecord.MARKET_SH + "," + code) &&
				!stock_map.containsKey(TransDetailRecord.MARKET_SZ + "," + code)) {
			log.error("no stock price in mem! code:" + code);
			return;
		}
		
		int num = 0;
		
		for (String key : stock_map.keySet().toArray(new String[0])) {
			
			String arrs[] = key.split(",");
			String stock_market = arrs[0];
			String stock_code = arrs[1];
			
			if (code.equals(ALL_STOCK_CODE) || code.equals(stock_code)) {
				num++;
				log.info("	store " + num + ":" + stock_code);
				
				String table_name = STOCK_PRICE_TABLE_PRE + stock_market + "_" + stock_code;
				String sql = "create table if not exists " + table_name + 
						"(`date` DATE not null default '0000-00-00', " +
						"`open` double NOT NULL default '0', " +
						"`close` double NOT NULL default '0', " +
						"PRIMARY KEY (`date`)" +
						") ENGINE=MyISAM DEFAULT CHARSET=utf8";	
				
				dbInst.updateSQL(sql);
				if (clean) {
					sql = "delete from " + table_name;
					log.info("	" + sql);
					dbInst.updateSQL(sql);
				}
				
				//HashMap<String, Double> stock_price = stock_map.get(stock_code);
				TreeMap<String, PriceRecord> stock_price = new TreeMap<String, PriceRecord>(stock_map.get(key));
				for (String date : stock_price.keySet().toArray(new String[0])) {
					PriceRecord pr = stock_price.get(date);
					sql = "insert into " + table_name + "(date, open, close) values " +
							"('" + date + "', " + pr.open + "," + pr.close + ") " +
									"ON DUPLICATE KEY UPDATE open = " + pr.open + ", close = " + pr.close;
					dbInst.updateSQL(sql);
				}
			}
			
			TreeMap<String, ExDivide> ex_divide_map = ReadExDivideFromDB(stock_market, stock_code);
			UpdateStockPriceByExDivide(stock_market, stock_code, ex_divide_map);
		}
	}
	
	/**
	 * 扫描内存中的股票价格数据
	 */
	public void ScanStockPrice() {
		for (String key : stock_map.keySet().toArray(new String[0])) {
			System.out.println("key:" + key);
			System.out.println("value:<date,open,close>");
			
			//HashMap<String, Double> stock_price = stock_map.get(code);
			TreeMap<String, PriceRecord> stock_price = new TreeMap<String, PriceRecord>(stock_map.get(key));
			for (String date : stock_price.keySet().toArray(new String[0])) {
				PriceRecord pr = stock_price.get(date);
				System.out.println("<" + date + "," + pr.open + "," + pr.close + ">");
			}
		}
	}
	
	/**
	 * 从数据库读取股票的价格信息
	 * @param mode 0-all trans stock;1-new trans stock;2-design trans stock;3-design any stock
	 * @param code
	 * @param market
	 */
	public void LoadStockPrice(int mode, String code, String market) {
		
		log.info("start load all stock price to mem");
		
		HashMap<String, String> stock_info;
		
		//直接指定股票
		if (mode == StockPrice.UPDATE_STOCK_CODE) {
			stock_info = new HashMap<String, String>();
			stock_info.put(code, market);
		}
		//从交易表中获取股票代码
		else {
			stock_info = GetStockTransed(mode, code);
		}
		
		for (String stock_code : stock_info.keySet().toArray(new String[0])) {
			ReadStockPriceFromDB(stock_code, market);
		}
		
		//读取大盘的价格信息
		ReadStockPriceFromDB(StockPrice.CODE_SZZS, TransDetailRecord.MARKET_SH);
		ReadStockPriceFromDB(StockPrice.CODE_SZCZ, TransDetailRecord.MARKET_SZ);

	}
	
	/**
	 * 从数据库读取指定股票的所有价格
	 * @param code 
	 * @param market
	 * @return
	 */
	public void ReadStockPriceFromDB(String code, String market) {
		String table_name = STOCK_PRICE_ED_TABLE_PRE + market + "_" + code;
		
		//先查询对应的表是否存在
		String sql 	= "show tables like '" + table_name + "'";
		ResultSet rs = dbInst.selectSQL(sql);
				
		try {
			//如果不存在，则立即获取数据
			if (!rs.next()) {
				QueryStockHistoryPrice(market, code);
				QueryStockRT(market, code);
				StoreStockPriceToDB(code, false);
			}
			else {
				sql	= "select date + 0 as date, open as open, close as close from " + table_name;
				
				HashMap<String, PriceRecord> stock_price = new HashMap<String, PriceRecord>();
				
				rs = dbInst.selectSQL(sql);
				while (rs.next()) {
					String date		= rs.getString("date");
					
					PriceRecord pr = new PriceRecord();
					pr.open 	= rs.getDouble("open");
					pr.close 	= rs.getDouble("close");
					
					stock_price.put(date, pr);
				}
				
				String key = market + "," + code;
				stock_map.put(key, stock_price);
			}
			
			rs.close();
		} 
		catch (Exception e) {
			log.error("sql excute failed! sql:" + sql, e);
			System.exit(0);
		}
	}
	
	/**
	 * 更新股票和大盘信息
	 * @param range_mode 0-alltime;1-rt;2-history
	 * @param mode 0-all trans stock;1-new trans stock;2-stock
	 * @param stock_code 
	 * @throws Exception
	 */
	public void UpdateStock(int range_mode, int mode, String stock_code) throws Exception {
		log.info("start update stock price! range_mode:" + range_mode + "mode:" + mode + " stock_code:" + stock_code);
		
		stock_map.clear();
		
		//更新股票数据
		log.info("    update stocks");
		HashMap<String, String> stock_info = GetStockTransed(mode, stock_code);
		for (String code : stock_info.keySet().toArray(new String[0])) {
			String market = stock_info.get(code);
			
			log.info("    update " + code);
			
			if (range_mode == UPDATE_RT || range_mode == UPDATE_ALL) {
				//查询实时数据
				QueryStockRT(market, code);
			}
			if (range_mode == UPDATE_HIS || range_mode == UPDATE_ALL) {
				//查询历史数据
				QueryStockHistoryPrice(market, code);
			}
			
			//存入数据库
			log.info("    store " + code + " to db");
			StoreStockPriceToDB(code, false);
		}
		
		//更新大盘数据
		log.info("    update dapan");
		if (range_mode == UPDATE_RT || range_mode == UPDATE_ALL) {
			//查询实时数据
			QueryStockRT(TransDetailRecord.MARKET_SH, CODE_SZZS);
			QueryStockRT(TransDetailRecord.MARKET_SZ, CODE_SZCZ);
		}
		
		if (range_mode == UPDATE_HIS || range_mode == UPDATE_ALL) {
			//查询历史数据
			QueryStockHistoryPrice(TransDetailRecord.MARKET_SH, CODE_SZZS);
			QueryStockHistoryPrice(TransDetailRecord.MARKET_SZ, CODE_SZCZ);
		}
		
		//存入数据库
		log.info("    store dapan to db");
		StoreStockPriceToDB(CODE_SZZS, false);
		StoreStockPriceToDB(CODE_SZCZ, false);
	}
	
	/**
	 * 获取交易过的股票列表
	 * @param mode 0-all trans stock;1-new trans stock;2-stock
	 * @param stock_code
	 * @return
	 */
	public HashMap<String, String> GetStockTransed(int mode, String stock_code) {
		
		//获取所有交易表
		String sql = "show tables like 'stock_trans_detail%'";
		ResultSet rs = dbInst.selectSQL(sql);
		ArrayList<String> table_list = new ArrayList<String>();
		
		try {
			while (rs.next()) {
				String table = rs.getString(1);
				table_list.add(table);
			}
			
			rs.close();
		} 
		catch (Exception e) {
			log.error("sql excute failed! sql:" + sql, e);
			System.exit(0);
		}
		
		HashMap<String, String> stock_info = new HashMap<String, String>();
		for (int i = 0; i < table_list.size(); i++) {
			//从交易表中获取股票代码
			sql = "select distinct(code) as code, market from " + table_list.get(i);
			rs = dbInst.selectSQL(sql);
			
			try {
				while (rs.next()) {
					String code	= rs.getString("code");
					String market = rs.getString("market");
					stock_info.put(code, market);
				}
				
				rs.close();
			} catch (Exception e) {
				log.error("sql excute failed! sql:" + sql, e);
				System.exit(0);
			}
			
		}
		
		//挑选出新交易的股票
		if (mode == StockPrice.UPDATE_STOCK_TRANSED_NEW) {
			for (String code : stock_info.keySet().toArray(new String[0])) {
				sql = "show tables like '"+ StockPrice.STOCK_PRICE_TABLE_PRE + code + "'";
				rs = dbInst.selectSQL(sql);
				try {
					while (rs.next()) {
						String table = rs.getString(1);
						if (table.equals(StockPrice.STOCK_PRICE_TABLE_PRE + code)) {
							stock_info.remove(code);
						}
					}
					
					rs.close();
				} 
				catch (Exception e) {
					log.error("sql excute failed! sql:" + sql, e);
					System.exit(0);
				}
			}
		}
		//只获取指定的股票
		else if (mode == StockPrice.UPDATE_STOCK_TRANSED_CODE) {
			for (String code : stock_info.keySet().toArray(new String[0])) {
				if (!code.equals(stock_code)) {
					stock_info.remove(code);
				}
			}
		}
			
		return stock_info;
	}
	
	public static void main(String[] args) {

		if (args.length != 2 && args.length != 4 && args.length != 6) {
			StockPrice.usage();
			System.exit(0);
		}
		
		DBInst dbInst	= new DBInst("jdbc:mysql://localhost:3306/stock_new", "latupa", "latupa");
		//DBInst dbInst	= new DBInst("jdbc:mysql://192.168.116.153:3306/stock_new", "latupa", "latupa");
		
		StockPrice sp = new StockPrice(dbInst);
		
		if (args[0].equals("-f")) {
			String file_name = args[1];
			
			log.info("start import stock price from " + file_name);
			try {
				sp.ImportHistoryPrice(file_name);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		else if (args[0].equals("-e")) {
			String file_name = args[1];
			
			if (!args[2].equals("-m")) {
				StockPrice.usage();
				System.exit(0);
			}
			
			String market = args[3];
			
			if (!args[4].equals("-c")) {
				StockPrice.usage();
				System.exit(0);
			}
			
			String code = args[5];
			
			log.info("start import ex-divide from " + file_name);
			sp.ProcExDivide(market, code, file_name);

		}
		else if (args[0].equals("-t")) {
			
			int range_mode = Integer.parseInt(args[1]);
			if (range_mode >= UPDATE_MAX) {
				StockPrice.usage();
				System.exit(0);
			}
			
			if (!args[2].equals("-m")) {
				StockPrice.usage();
				System.exit(0);
			}
			
			int mode = Integer.parseInt(args[3]);
			if (mode >= UPDATE_STOCK_MAX) {
				StockPrice.usage();
				System.exit(0);
			}
			
			String stock_code = "";
			if (args.length == 6) {
				if (!args[4].equals("-s")) {
					StockPrice.usage();
					System.exit(0);
				}
				
				stock_code = args[5];
			}
			
			log.info("start query stock price! mode:" + String.valueOf(mode));
			
			try {
				sp.UpdateStock(range_mode, mode, stock_code);
			} catch (Exception e) {
				log.error("query url failed! " + e.toString(), e);
			}
			//.ScanStockPrice();
		}
		else {
			StockPrice.usage();
			System.exit(0);
		}
		
		log.info("proc finish!");
	}
	
	public static void usage() {
		System.out.println("usage as follow:");
		System.out.println("    -f dzh file");
		System.out.println("    -e ex-divide file. such as sh_000001");
		System.out.println("		-m market");
		System.out.println("		-c code");
		System.out.println("    -t range 0-alltime;1-rt;2-history");
		System.out.println("    	-m mode	0-all trans stock;1-new trans stock;2-stock");
		System.out.println("    		none");
		System.out.println("    		-s stock code");
	}
}
