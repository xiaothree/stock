package com.latupa.stock;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 处理所有和股票价格相关的工作
 * @author latupa
 * TODO
 * 1.股票价格按照交易市场分表
 * 2.每一类股票价格保留两份，一份为原始的，另一份为除权处理之后的
 * 3.查询的时候，优先使用除权处理之后的股票价格
 * 4.历史数据通过大智慧导入
 */

public class StockPriceNew {

	private static final Log log = LogFactory.getLog(StockPriceNew.class);
	
	//记录股票历史价格{{market,code}, {date, price}}
	public HashMap<String, HashMap<String, PriceRecord>> stock_map = new HashMap<String, HashMap<String, PriceRecord>>();
	
	//记录股票涉及的市场列表
	private HashSet<String> market_set = new HashSet<String>();
	
	//股票价格表名前缀
	private static final String STOCK_PRICE_TABLE_PRE = "stock_price__";
	
	//股票除权后价格表名前缀
	//private static final String STOCK_PRICE_ED_TABLE_PRE = "stock_price_ed__";
	
	//股票除权表名前缀
	//private static final String STOCK_EXDIVIDE_TABLE_PRE = "stock_exdivide__";
	
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
	
	public StockPriceNew(DBInst dbInst) {
		this.dbInst = dbInst;
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
	 * @throws IOException
	 * @throws ParseException
	 */
	public byte[] ParseStockDAD(DataInputStream dis, String code, String market, String name) throws IOException, ParseException {
		
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
			
			stock_price.put(sdate, pr);
			
			System.out.println("date:" + sdate + " open:" + open + " close:" + close);
			
			//0-3
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
		
		String key = market + "," + code + "," + name;
		stock_map.put(key, stock_price);
		
		return byte4;
	}
	
	/**
	 * 从大智慧导入股票的历史价格数据（DAD），是原始数据
	 * 获取方式：大智慧-工具-数据管理-生成数据
	 * @param file_name
	 * @param clean 如果为true则先清空表
	 * @throws IOException
	 * @throws ParseException
	 */
	public void ImportHistoryPrice(String file_name, boolean clean) throws IOException, ParseException {
		
		DataInputStream dis = new DataInputStream(new FileInputStream(new File(file_name)));
		
		byte[] byte2 = new byte[2];
		byte[] byte4 = new byte[4];
		byte[] byte6 = new byte[6];
		byte[] byte12 = new byte[12];
		byte[] byte16 = new byte[16];
		
		String market;
		String code;
		String name;
		
		/**
		 * |4byte|2byte|6byte|4byte|
		 *   tag  market code none
		 * |4byte|12byte|
		 *  none  name 
		 */
		
		dis.read(byte16);
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
				
				//记录下涉及到的市场列表，用于入库时候清理数据用
				if (!market_set.contains(market)) {
					market_set.add(market);
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
						
				System.out.println("market:" + market + " code:" + code + " name:" + name);
				
				byte4 = ParseStockDAD(dis, code, market, name);
			}
		}
		
		dis.close();
		
		//存入数据库
		StoreStockPriceToDB(clean);
		
		stock_map.clear();
	}
	
	/**
	 * 把内存中stock_map的股票价格存入到数据库中
	 * @param clean 如果为true则先清空表
	 */
	public void StoreStockPriceToDB(boolean clean) {
		log.info("start store stock price to db...");
		
		for (String market : market_set.toArray(new String[0])) {
			//创建表
			String table_name = STOCK_PRICE_TABLE_PRE + market;
			String sql = "create table if not exists " + table_name + 
					"(" +
					"`code` varchar(32) not null default '', " +
					"`name` varchar(64) not null default '', " +
					"`day` DATE not null default '0000-00-00', " +
					"`open` double NOT NULL default '0', " +
					"`close` double NOT NULL default '0', " +
					"PRIMARY KEY (`code`, `day`)" +
					") ENGINE=InnoDB DEFAULT CHARSET=utf8";	
			log.info("	" + sql);
			dbInst.updateSQL(sql);
			
			//清理历史数据
			if (clean) {
				sql = "delete from " + table_name;
				log.info("	" + sql);
				dbInst.updateSQL(sql);
			}
		}
		
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
			
			stock_num++;
			log.info("	store " + stock_num + ":" + market + "-" + code + "-" + name);
			
			TreeMap<String, PriceRecord> stock_price = new TreeMap<String, PriceRecord>(stock_map.get(key));
			//key:date, value:PriceRecord
			for (String day : stock_price.keySet().toArray(new String[0])) {
				PriceRecord pr = stock_price.get(day);
				String sql = "insert into " + table_name + 
						"(code, name, day, open, close) values " +
						"('" + code + "', " + 
						"'" + name + "', " + 
						"'" + day + "', " + 
						pr.open + "," + 
						pr.close + ") " +
						"ON DUPLICATE KEY UPDATE " +
						"name = '" + name + "', " + 
						"open = " + pr.open + "," +
						"close = " + pr.close;
				dbInst.updateSQL(sql);
			}
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
		Double give;
		Double right;
		Double right_price;
		Double bonus;
		long timestamp;
		
		Date date;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		
		/**
		 * |4byte|4byte|4byte|4byte|4byte|
		 *   time  give  pay  price profit
		 */
		
		//0-3
		dis.read(byte4);
		while ((dis.available() > 0) && (getInt(byte4) != 0xFFFFFFFF)) {
			
			timestamp = byte2int(byte4);
			date = new Date(timestamp * 1000);
			sdate = sdf.format(date);
			
			//4-7
			dis.read(byte4);
			give = Double.parseDouble(getFloat(byte4) + "");
			
			//4-7
			dis.read(byte4);
			right = Double.parseDouble(getFloat(byte4) + "");
			
			//4-7
			dis.read(byte4);
			right_price = Double.parseDouble(getFloat(byte4) + "");
			
			//4-7
			dis.read(byte4);
			bonus = Double.parseDouble(getFloat(byte4) + "");
			
          
			System.out.println("date:" + sdate + " give:" + give + " right:" + right + " right_price:" + right_price + " bonus:" + bonus);
			
			dis.read(byte4);

		}
		
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
				
				//记录下涉及到的市场列表，用于入库时候清理数据用
				if (!market_set.contains(market)) {
					market_set.add(market);
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
				
				System.out.println("market:" + market + " code:" + code);
				
				dis.read(byte8);
				
				byte4 = ParseExDividePWR(dis, code, market);
			}
		}
		
		dis.close();
		
	}
	
	public static void main(String[] args) {

		if (args.length != 2 && args.length != 4 && args.length != 6) {
			StockPriceNew.usage();
			System.exit(0);
		}
		
		//DBInst dbInst	= new DBInst("jdbc:mysql://localhost:3306/stock_new", "latupa", "latupa");
		DBInst dbInst	= new DBInst("jdbc:mysql://192.168.153.135:3306/stock_new", "latupa", "latupa");
		
		StockPriceNew sp = new StockPriceNew(dbInst);
		
		if (args[0].equals("-f")) {
			String file_name = args[1];
			
			log.info("start import stock price from " + file_name);
			try {
				sp.ImportHistoryPrice(file_name, true);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		else if (args[0].equals("-d")) {
			String file_name = args[1];
			
			log.info("start import exdivide data from " + file_name);
			try {
				sp.ImportExDivideData(file_name, true);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParseException e) {
				// TODO Auto-generated catch block
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
		System.out.println("    -f dzh dad file");
		System.out.println("    -d dzh pwr file");
		System.out.println("    -e ex-divide file. such as sh_000001");
		System.out.println("		-m market");
		System.out.println("		-c code");
		System.out.println("    -t range 0-alltime;1-rt;2-history");
		System.out.println("    	-m mode	0-all trans stock;1-new trans stock;2-stock");
		System.out.println("    		none");
		System.out.println("    		-s stock code");
	}
}
