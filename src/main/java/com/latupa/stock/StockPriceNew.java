package com.latupa.stock;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 记录股票价格
 * @author latupa
 *
 */
class PriceRecord {
	double open;
	double close;
	int is_holiday;  //是否休市;0为交易日，1为非交易日
}

/**
 * 记录除股票权数据
 * @author latupa
 *
 */
class ExDivide {
	double give;		//送股
	double right;		//配股
	double right_price;	//配股价
	double bonus;		//分红
}

/**
 * 处理所有和股票价格相关的工作
 * @author latupa
 * 1.股票价格按照交易市场分表
 * 2.每一类股票价格保留两份，一份为原始的，另一份为除权处理之后的
 * 3.查询的时候，优先使用除权处理之后的股票价格
 * 4.历史数据通过大智慧导入
 */

public class StockPriceNew {

	private static final Log log = LogFactory.getLog(StockPriceNew.class);
	
	//记录股票历史价格{{market,code}, {date, price}}
	public HashMap<String, HashMap<String, PriceRecord>> stock_map = new HashMap<String, HashMap<String, PriceRecord>>();
	
	//记录股票除权信息{{market,code}, {date, ex-divide}}
	public HashMap<String, HashMap<String, ExDivide>> ex_divide_map = new HashMap<String, HashMap<String, ExDivide>>();
	
	//记录股票价格涉及的市场列表
	private HashMap<String, Boolean> stock_price_market_map = new HashMap<String, Boolean>();
	
	//记录股票除权涉及的市场列表
	private HashMap<String, Boolean> stock_ex_divide_market_map = new HashMap<String, Boolean>();	
	
	//股票价格表名前缀
	private static final String STOCK_PRICE_TABLE_PRE = "stock_price__";
	
	//股票除权后价格表名前缀
	public final String STOCK_PRICE_ED_TABLE_PRE = "stock_price_ed__";
	
	//股票除权表名前缀
	private static final String STOCK_EXDIVIDE_TABLE_PRE = "stock_exdivide__";
	
	//所有股票市场
	private static final String STOCK_MARKET_ALL = "all";
	
	//数据库配置文件
	private static final String FLAG_FILE_DIR = "src/main/resources/";
	private static final String dbconf_file = "db.flag";
	
	//记录已成功处理的字节数
	private int byte_num = 0;
	
	//数据库连接
	public DBInst dbInst;  
	
	public StockPriceNew() {
		this.dbInst = ConnectDB();
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
	
	public static float getFloat(byte[] bytes) { 
		// 4 bytes
        int accum = 0; 
        for ( int shiftBy = 0; shiftBy < 4; shiftBy++ ) { 
                accum |= (bytes[shiftBy] & 0xff) << shiftBy * 8; 
        } 
        return Float.intBitsToFloat(accum); 
	}	
	
	/**
	 * 解析大智慧DAD文件中的一个股票的所有天的数据
	 * @param dis
	 * @param code
	 * @param market
	 * @param name
	 * @return byte4 最后读取的4个字节
	 * @throws Exception 
	 */
	public byte[] ParseStockDAD(DataInputStream dis, String code, String market, String name) throws Exception {
		
		byte[] byte4 = new byte[4];
		
		String sdate;
		Double open;
		Double close;
		long timestamp;
		int count = 0;
		
		//date->PriceRecord
		HashMap<String, PriceRecord> stock_price = new HashMap<String, PriceRecord>();
		
		Date date;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		
		String sdate_s = null;
		String sdate_e = null;
		
		date = new Date(System.currentTimeMillis());
		String curt_date = sdf.format(date);
		
		/**
		 * |4byte|4byte|4byte|4byte|
		 *   date  open  none  none
		 * |4byte|4byte|4byte|4byte|
		 *  close none  none  none
		 */
		
		//0-3
		dis.read(byte4);
		while (getInt(byte4) != 0xFFFFFFFF) {
			
			timestamp = getInt(byte4);
			date = new Date(timestamp * 1000);
			sdate = sdf.format(date);
			
			if (sdate_s == null) {
				sdate_s = sdate;
			}
			
			//如果日期小于等于最大值（sdate_e），或者日期大于当前时间，则该记录有误
			if ((sdate_e != null && Integer.parseInt(sdate) <= Integer.parseInt(sdate_e)) ||
					Integer.parseInt(sdate) > Integer.parseInt(curt_date)) {
				log.warn("skip record, market:" + market + ", code:" + code + ", date:" + sdate + ", date end:" + sdate_e);
//				throw new Exception("stock price record error! market:" + market + ", code:" + code + ", date:" + sdate);
				dis.skipBytes(28);
				dis.read(byte4);
				continue;
			}
			
			sdate_e = sdate;
			
			//4-7
			dis.read(byte4);
			open = Double.parseDouble(getFloat(byte4) + "");
			
			//8-15
			dis.skipBytes(8);
			
			//16-19
			dis.read(byte4);
			close = Double.parseDouble(getFloat(byte4) + "");
			
			//20-31
			dis.skipBytes(12);
			
            PriceRecord pr = new PriceRecord();
            pr.open		= open;
			pr.close	= close;
			pr.is_holiday = 0;
			
			if (pr.open == 0 || pr.close == 0) {
				log.warn("skip record, date:" + sdate + ", open:" + pr.open + ", close:" + pr.close);
				//throw new Exception("stock price record error! market: " + market + ", code:" + code + ", date:" + sdate + ", open:" + pr.open + ", close:" + close);
				dis.read(byte4);
				continue;
			}
			
			count++;
			
			stock_price.put(sdate, pr);
			
			this.byte_num += 32;
			
			//log.info("date:" + sdate + " open:" + open + " close:" + close);
			
			//0-3
			dis.read(byte4);
		}
		
		log.info("parse " + count + " records");
		
		//补全非交易日的数据（用离非交易日最近的交易日数据补齐）
		try {
			date = sdf.parse(sdate_s);
		}
		catch (Exception e) {
			e.printStackTrace();
			log.info("skip it");
			return byte4;
		}
		
		sdate = sdf.format(date);
		PriceRecord last_pr = null;
		while (Integer.parseInt(sdate) < Integer.parseInt(sdate_e)) {
			if (!stock_price.containsKey(sdate) && last_pr != null) {
				PriceRecord new_pr = new PriceRecord();
				new_pr.open = last_pr.open;
				new_pr.close = last_pr.close;
				new_pr.is_holiday = 1;
				stock_price.put(sdate, new_pr);
			}
			
			last_pr	= stock_price.get(sdate);
			date.setTime((date.getTime() / 1000 + 86400) * 1000);
			sdate	= sdf.format(date);
		}
		
		String key = market + "," + code + "," + name;
		stock_map.put(key, stock_price);
		
		return byte4;
	}
	
	/**
	 * 从大智慧导入股票的历史价格数据（DAD），是原始数据
	 * 获取方式：大智慧-常用工具-数据管理中心-生成数据
	 * @param file_name
	 * @param clean 如果为true则先清空表
	 * @throws Exception 
	 */
	public void ImportHistoryPrice(String file_name, boolean clean) throws Exception {
		
		DataInputStream dis = new DataInputStream(new FileInputStream(new File(file_name)));
		
		byte[] byte2 = new byte[2];
		byte[] byte4 = new byte[4];
		byte[] byte6 = new byte[6];
		byte[] byte12 = new byte[12];
		byte[] byte16 = new byte[16];
		
		String market;
		String code;
		String name;
		
		this.byte_num = 0;
		
		/**
		 * |4byte|2byte|6byte|4byte|
		 *   tag  market code none
		 * |4byte|12byte|
		 *  none  name 
		 */
		
		dis.read(byte16);
		this.byte_num += 16;
		while (dis.available() > 0) {
			
			//0-3
			dis.read(byte4);
			
			// new stock
			while (getInt(byte4) == 0xFFFFFFFF) {
				
				//4-5
				dis.read(byte2);
				if (getShort(byte2) == 0x0000) {
					break;
				}
				market = new String(byte2);
				market = market.toLowerCase();
				
				//记录下涉及到的市场列表，用于入库时候创建表
				if (!stock_price_market_map.containsKey(market)) {
					stock_price_market_map.put(market, Boolean.FALSE);
				}
				
				//6-11
				dis.read(byte6);
				
				//code由小于等于6个字节构成，某个byte为0x00标识结束
				boolean byte_is_zero = false;
				for (int i = 0; i <= byte6.length - 1; i++) {
					if (byte6[i] == 0x00) {
						byte_is_zero = true;
					}
					
					if (byte_is_zero == true) {
						byte6[i] = 0x00;
					}
				}
				code = new String(byte6);
				code = code.trim();
				
				//12-15
				dis.skipBytes(4);
				
				//16-19
				dis.skipBytes(4);
				
				//20-31
				dis.read(byte12);
				
				this.byte_num += 32;
				//name由小于等于12个字节构成，某个byte为0x00标识结束
				byte_is_zero = false;
				for (int i = 0; i <= byte12.length - 1; i++) {
					if (byte12[i] == 0x00) {
						byte_is_zero = true;
					}
					
					if (byte_is_zero == true) {
						byte12[i] = 0x00;
					}
				}
				name = new String(byte12, "gbk");
				name = name.trim();
						
				log.info("market:" + market + " code:" + code + " name:" + name);
				
				byte4 = ParseStockDAD(dis, code, market, name);
				
				log.info("start postion:" + this.byte_num);
				
				//把该股票的数据存入数据库
				StoreStockPriceToDB(clean);
				
				stock_map.clear();
			}
		}
		
		dis.close();
	}
	
	/**
	 * 把内存中stock_map的股票价格存入到数据库中
	 * @param clean 如果为true，则先清空表中该股票的所有记录
	 */
	public void StoreStockPriceToDB(boolean clean) {
		log.info("start store stock price to db...");
		
		String sql;
		int stock_num = 0;
		//key:market+code+name, value:date->PriceRecord
		for (String key : stock_map.keySet().toArray(new String[0])) {
			
			String arrs[] = key.split(",");
			String market = arrs[0];
			String code = arrs[1];
			String name = " ";
			if (arrs.length == 3) {
				name = arrs[2];
			}
			
			String table_name = STOCK_PRICE_TABLE_PRE + market;
			
			if (stock_price_market_map.get(market).booleanValue() != true) {
				sql = "create table if not exists " + table_name + 
						"(" +
						"`code` varchar(32) not null default '', " +
						"`name` varchar(64) not null default '', " +
						"`day` DATE not null default '0000-00-00', " +
						"`open` double NOT NULL default '0', " +
						"`close` double NOT NULL default '0', " +
						"`is_holiday` int NOT NULL default '0', " +
						"PRIMARY KEY (`code`, `day`) " +
						") ENGINE=InnoDB DEFAULT CHARSET=utf8 " +
						"partition by key(code) " +
						"partitions 200";	
				log.info("	" + sql);
				dbInst.updateSQL(sql);
				stock_price_market_map.put(market, Boolean.TRUE);
			}
			
			stock_num++;
			log.info("	store " + stock_num + ":" + market + "-" + code + "-" + name);
			
			//清理历史数据
			if (clean) {
				sql = "delete from " + table_name + " where code = '" + code + "'";
				log.info("	" + sql);
				dbInst.updateSQL(sql);
			}
			
			TreeMap<String, PriceRecord> stock_price = new TreeMap<String, PriceRecord>(stock_map.get(key));
			//key:date, value:PriceRecord
			log.info("start insert records");
			int count = 0;
//			for (String day : stock_price.keySet().toArray(new String[0])) {
//				PriceRecord pr = stock_price.get(day);
//				sql = "insert into " + table_name + 
//						"(`code`, `name`, `day`, `open`, `close`, `is_holiday`) values " +
//						"('" + code + "', " + 
//						"'" + name + "', " + 
//						"'" + day + "', " + 
//						pr.open + "," + 
//						pr.close + "," +
//						pr.is_holiday + ") " +
//						"ON DUPLICATE KEY UPDATE " +
//						"`name` = '" + name + "', " + 
//						"`open` = " + pr.open + "," +
//						"`close` = " + pr.close + "," +
//						"`is_holiday` = " + pr.is_holiday;
//				dbInst.updateSQL(sql);
//				count++;
//				if (count % 1000 == 0) {
//					log.info("insert " + count + " records");
//				}
//			}
			
			sql = "insert into " + table_name + 
			"(`code`, `name`, `day`, `open`, `close`, `is_holiday`) values ";
			
			for (String day : stock_price.keySet().toArray(new String[0])) {
				PriceRecord pr = stock_price.get(day);
				sql = sql + "('" + code + "', " + 
						"'" + name + "', " + 
						"'" + day + "', " + 
						pr.open + "," + 
						pr.close + "," +
						pr.is_holiday + "), ";
				count++;
				if (count % 1000 == 0) {
					log.info("insert " + count + " records");
					sql = sql.substring(0, sql.length() - 2) + ";";
					dbInst.updateSQL(sql);
					sql = "insert into " + table_name + 
							"(`code`, `name`, `day`, `open`, `close`, `is_holiday`) values ";
				}
			}
			
			log.info("insert " + count + " records");
			sql = sql.substring(0, sql.length() - 2) + ";";
			dbInst.updateSQL(sql);
			log.info("insert finish");
		}
	}
	
	/**
	 * 根据除权数据修改股票历史价格
	 * @param market， 如果market为all，则根据所有市场的除权数据，更新对应的股票价格数据
	 * @throws SQLException 
	 */
	public void UpdateStockPriceByExDivide(String market) throws SQLException {
		
		log.info("start update stock price by ex-divide! market:" + market);
		
		String sql;
		
		//获取所有除权数据市场的表
		ArrayList<String> table_list = new ArrayList<String>();
		
		sql = "show tables like '" + STOCK_EXDIVIDE_TABLE_PRE + "%'";
		log.info(sql);
		ResultSet rs = dbInst.selectSQL(sql);
		
		if (rs == null) {
			log.info("没有可供处理的除权数据表:" + STOCK_EXDIVIDE_TABLE_PRE);
			return;
		}
		
		while (rs.next()) {
			String table = rs.getString(1);
			System.out.println(table);
			table_list.add(table);
		}
		
		rs.close();
		
		for (int i = 0; i < table_list.size(); i++) {
			String ex_table = table_list.get(i);
			
			log.info("开始处理除权表:" + ex_table);
			
			//如果指定了某个市场
			if (!market.equals(STOCK_MARKET_ALL)) {
				if (!ex_table.endsWith(market)) {
					continue;
				}
			}
			
			String stock_table = ex_table.replace(STOCK_EXDIVIDE_TABLE_PRE, STOCK_PRICE_TABLE_PRE);
			String stock_ed_table = ex_table.replace(STOCK_EXDIVIDE_TABLE_PRE, STOCK_PRICE_ED_TABLE_PRE);
			
			System.out.println("ex_table = " + ex_table + ", stock_table = " + stock_table + ", stock_ed_table = " + stock_ed_table);
			
			//1. 先删除除权后的股票价格表
			sql = "drop table if exists " + stock_ed_table;
			log.info(sql);
			dbInst.updateSQL(sql);
			
			//2. 创建除权后的股票价格表（从历史股票价格表导入）
			sql = "create table " + stock_ed_table + " select * from " + stock_table;
			log.info(sql);
			dbInst.updateSQL(sql);
			
			//3. 转为分区表
			sql = "alter table " + stock_ed_table + " partition by key(code) partitions 200";
			log.info(sql);
			dbInst.updateSQL(sql);
			
			//4. 根据除权数据文件，更新股票价格表
			sql = "select `code` as `code`, `day` as `day`, `give` as `give`, `right` as `right`, `right_price` as `right_price`, `bonus` as `bonus` from " + ex_table + " order by code, day";
			log.info(sql);
			rs = dbInst.selectSQL(sql);
			
			while (rs.next()) {
				String code = rs.getString("code");
				String day  = rs.getString("day");
				double give = rs.getDouble("give");
				double right = rs.getDouble("right");
				double right_price = rs.getDouble("right_price");
				double bonus = rs.getDouble("bonus");
				
				log.info("code:" + code + ", day:" + day);
				
//				//送股、分红
//				if (give != 0 || bonus !=0) {
//					sql = "update " + ed_table_name + " set open = open / " + String.valueOf(1 + give) + " - " + String.valueOf(bonus) +
//							", close = close / " + String.valueOf(1 + give) + " - " + String.valueOf(bonus) +
//							" where date < '" + date + "'"; 
//				}
//				//配股
//				else if (right != 0) {
//					sql = "update " + ed_table_name + " set open = (open + " + String.valueOf(right_price * right) + ") / " + String.valueOf(1 + right) +
//							", close = (close + " + String.valueOf(right_price * right) + ") / " + String.valueOf(1 + right) +
//							" where date < '" + date + "'"; 
//				}
				
				//除权价=(收盘价+配股比例×配股价-每股所派现金)÷(1+送股比例+配股比例)
				sql = "update " + stock_ed_table + " set open = (open + " + String.valueOf(right_price * right) + " - " + String.valueOf(bonus) + ") / " + String.valueOf(1 + give + right) +
						", close = (close + " + String.valueOf(right_price * right) + " - " + String.valueOf(bonus) + ") / " + String.valueOf(1 + give + right) +
						" where code = '" + code + "' and day < '" + day + "'"; 
				
				//log.info(sql);
				dbInst.updateSQL(sql);
			}
			rs.close();
		}
	}
	
	/**
	 * 把内存中exdivide_map的除权数据存入到数据库中
	 * @param clean 如果为true，则先清表中该股票的所有记录
	 */
	public void StoreExDivideToDB(boolean clean) {
		log.info("start store ex-divide to db...");
		
		String sql;
		int stock_num = 0;
		//key:market+code, value:date->ExDivide
		for (String key : ex_divide_map.keySet().toArray(new String[0])) {
			
			String arrs[] = key.split(",");
			String market = arrs[0];
			String code = arrs[1];
			
			String table_name = STOCK_EXDIVIDE_TABLE_PRE + market;
			
			if (stock_ex_divide_market_map.get(market).booleanValue() != true) {
				sql = "create table if not exists " + table_name + 
						"(" +
						"`code` varchar(32) not null default '', " +
						"`day` DATE not null default '0000-00-00', " +
						"`give` double NOT NULL default '0', " +
						"`right` double NOT NULL default '0', " +
						"`right_price` double NOT NULL default '0', " +
						"`bonus` double NOT NULL default '0', " +
						"PRIMARY KEY (`code`, `day`)" +
						") ENGINE=InnoDB DEFAULT CHARSET=utf8";	
				
				log.info("	" + sql);
				dbInst.updateSQL(sql);
				stock_ex_divide_market_map.put(market, Boolean.TRUE);
			}
			
			stock_num++;
			log.info("	store " + stock_num + ":" + market + "-" + code);
			
			//清理历史数据
			if (clean) {
				sql = "delete from " + table_name + " where code = '" + code + "'";
				log.info("	" + sql);
				dbInst.updateSQL(sql);
			}
			
			TreeMap<String, ExDivide> ex_divide = new TreeMap<String, ExDivide>(ex_divide_map.get(key));
			//key:date, value:ExDivide
			sql = "insert into " + table_name + 
					"(`code`, `day`, `give`, `right`, `right_price`, `bonus`) values ";
			for (String day : ex_divide.keySet().toArray(new String[0])) {
				ExDivide ed = ex_divide.get(day);
				sql = sql + "('" + code + "', " + 
						"'" + day + "', " + 
						ed.give + "," + 
						ed.right + "," +
						ed.right_price + "," +
						ed.bonus + "), ";
			}
			
			sql = sql.substring(0, sql.length() - 2) + ";";
			dbInst.updateSQL(sql);
		}
	}
	
	/**
	 * 解析大智慧PWR文件中的一个股票的所有除权数据
	 * @param dis
	 * @param code
	 * @param market
	 * @return byte4 最后读取的4个字节
	 * @throws IOException
	 * @throws ParseException
	 */
	public byte[] ParseExDividePWR(DataInputStream dis, String code, String market) throws IOException, ParseException {
		
		byte[] byte4 = new byte[4];
		
		String sdate;
		long timestamp;
		
		//date->ExDivide
		HashMap<String, ExDivide> ex_divide = new HashMap<String, ExDivide>();
		
		Date date;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		
		/**
		 * |4byte|4byte|4byte|4byte|4byte|
		 *   time  give  right  right_price bonus
		 */
		
		//0-3
		dis.read(byte4);
		while ((dis.available() > 0) && (getInt(byte4) != 0xFFFFFFFF)) {
			
			ExDivide ed = new ExDivide();
			
			timestamp = byte2int(byte4);
			date = new Date(timestamp * 1000);
			sdate = sdf.format(date);
			
			//4-7
			dis.read(byte4);
			ed.give = Double.parseDouble(getFloat(byte4) + "");
			
			//4-7
			dis.read(byte4);
			ed.right = Double.parseDouble(getFloat(byte4) + "");
			
			//4-7
			dis.read(byte4);
			ed.right_price = Double.parseDouble(getFloat(byte4) + "");
			
			//4-7
			dis.read(byte4);
			ed.bonus = Double.parseDouble(getFloat(byte4) + "");
			
			ex_divide.put(sdate, ed);
			
			//log.info("date:" + sdate + " give:" + ed.give + " right:" + ed.right + " right_price:" + ed.right_price + " bonus:" + ed.bonus);
			
			dis.read(byte4);

		}
		
		String key = market + "," + code;
		ex_divide_map.put(key, ex_divide);
		
		return byte4;
	}


	/**
	 * 导入大智慧除权数据文件SPLIT.PWR
	 * 获取方式：1.http://www.kboyi.com/post/1.html
	 *          2.dzh2\Download\PWR，通过大智慧下载数据获取
	 * @param file_name
	 * @param clean 如果为true则先清空表
	 * @throws IOException
	 * @throws ParseException
	 */
	public void ImportExDivideData(String file_name, boolean clean) throws IOException, ParseException {
		
		DataInputStream dis = new DataInputStream(new FileInputStream(new File(file_name)));
		
		byte[] byte2 = new byte[2];
		byte[] byte4 = new byte[4];
		byte[] byte6 = new byte[6];
		byte[] byte8 = new byte[8];

		String market;
		String code;
		
		/**
		 * |4byte|2byte|6byte|8byte|
		 *   tag  market code none
		 */
		dis.read(byte8);
		while (dis.available() > 0) {
			
			// new stock
			dis.read(byte4);
			while ((dis.available() > 0) && (getInt(byte4) == 0xFFFFFFFF)) {
				
				//4-5
				dis.read(byte2);
				if (getShort(byte2) == 0x0000) {
					break;
				}
				market = new String(byte2);
				market = market.toLowerCase();
				
				//记录下涉及到的市场列表，用于入库时候创建表用
				if (!stock_ex_divide_market_map.containsKey(market)) {
					stock_ex_divide_market_map.put(market, Boolean.FALSE);
				}
				
				//6-11
				dis.read(byte6);
				
				//code由小于等于6个字节构成，某个byte为0x00标识结束
				boolean byte_is_zero = false;
				for (int i = 0; i <= byte6.length - 1; i++) {
					if (byte6[i] == 0x00) {
						byte_is_zero = true;
					}
					
					if (byte_is_zero == true) {
						byte6[i] = 0x00;
					}
				}
				code = new String(byte6);
				code = code.trim();
				
				log.info("market:" + market + " code:" + code);
				
				dis.read(byte8);
				
				byte4 = ParseExDividePWR(dis, code, market);
				
				//存入数据库
				StoreExDivideToDB(clean);
						
				ex_divide_map.clear();
			}
		}
		
		dis.close();
	}
	
	public static void main(String[] args) {

		if (args.length > 3) {
			StockPriceNew.usage();
			System.exit(0);
		}
		
		StockPriceNew sp = new StockPriceNew();
		
		if (args[0].equals("-f")) {
			String file_name = args[1];
			boolean clear = false;
			
			if (args.length == 3 && args[2].equals("-c")) {
				clear = true;
			}
			
			log.info("start import stock price from " + file_name);
			try {
				sp.ImportHistoryPrice(file_name, clear);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		else if (args[0].equals("-d")) {
			String file_name = args[1];
			
			log.info("start import exdivide data from " + file_name);
			try {
				sp.ImportExDivideData(file_name, true);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		else if (args[0].equals("-e")) {
			String market = args[1];
			
			log.info("update stock price by exdivide data for market :" + market);
			try {
				sp.UpdateStockPriceByExDivide(market);
			} catch (SQLException e) {
				e.printStackTrace();
			} 
		}
		else {
			StockPriceNew.usage();
			System.exit(0);
		}
		
		log.info("proc finish!");
	}
	
	public static void usage() {
		System.out.println("usage as follow:");
		System.out.println("    -f dzh dad file [-c]");
		System.out.println("    -d dzh pwr file");
		System.out.println("    -e update stock price by ex-divide data");
	}
}
