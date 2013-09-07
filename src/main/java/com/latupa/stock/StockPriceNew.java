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
 * 处理所有和股票价格相关的工作
 * @author latupa
 * TODO
 * 1.股票价格按照交易市场分表
 * 2.每一类股票价格保留两份，一份为原始的，另一份为除权处理之后的
 * 3.查询的时候，优先使用除权处理之后的股票价格
 */

public class StockPriceNew {

	private static final Log log = LogFactory.getLog(StockPriceNew.class);
	
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
	 * @return byte4 最后读取的4个字节
	 * @throws IOException
	 * @throws ParseException
	 */
	public byte[] ParseStock(DataInputStream dis, String code, String market) throws IOException, ParseException {
		
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
			
			timestamp = byte2int(byte4);
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
		
		String key = market + "," + code;
		stock_map.put(key, stock_price);
		
		return byte4;
	}
	
	/**
	 * 从大智慧导入股票的历史价格数据（DAD），是原始数据
	 * 获取方式：大智慧-工具-数据管理-生成数据
	 * @param file_name
	 * @throws IOException
	 * @throws ParseException
	 */
	public void ImportHistoryPrice(String file_name) throws IOException, ParseException {
		
		DataInputStream dis = new DataInputStream(new FileInputStream(new File(file_name)));
		
		byte[] byte2 = new byte[2];
		byte[] byte4 = new byte[4];
		byte[] byte6 = new byte[6];
		byte[] byte8 = new byte[8];
		byte[] byte12 = new byte[12];
		byte[] byte16 = new byte[16];
		
		String market;
		String code;
		String name;
		
		int num = 0;
		
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
				
				num++;

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
				
				byte4 = ParseStock(dis, code, market);
				
				if (num % 100 == 0) {
					//存入数据库
					log.info("    store " + num + " stocks update to now");
					//StoreStockPriceToDB(ALL_STOCK_CODE, false);
					
					stock_map.clear();
				}
			}
		}
		
		dis.close();
		
		//剩下的存入数据库
		log.info("    store " + num + " stocks update to now");
		//StoreStockPriceToDB(ALL_STOCK_CODE, false);
		
		stock_map.clear();

	}
	
	
	public static void main(String[] args) {

		if (args.length != 2 && args.length != 4 && args.length != 6) {
			StockPriceNew.usage();
			System.exit(0);
		}
		
		DBInst dbInst	= new DBInst("jdbc:mysql://localhost:3306/stock_new", "latupa", "latupa");
		//DBInst dbInst	= new DBInst("jdbc:mysql://192.168.116.153:3306/stock_new", "latupa", "latupa");
		
		StockPriceNew sp = new StockPriceNew(dbInst);
		
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
		else {
			StockPriceNew.usage();
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
