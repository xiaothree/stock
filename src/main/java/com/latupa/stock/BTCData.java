package com.latupa.stock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.TreeMap;

import net.sf.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class BTCBasicRecord {
	double high;
	double low;
	double open;
	double close;
	
	public BTCBasicRecord() {
		
	}
	
	public BTCBasicRecord(BTCBasicRecord record) {
		this.high	= record.high;
		this.low	= record.low;
		this.open	= record.open;
		this.close	= record.close;
	}
	
	public void Show() {
		DecimalFormat df = new DecimalFormat("#0.00");
		System.out.println("open:" + this.open + ", " +
				"close:" + this.close +", " +
				"high:" + this.high + ", " +
				"^" + String.valueOf(df.format((this.high - this.open) / this.open * 100)) + "%, " +
				"low:" + this.low + ", " +
				"V" + String.valueOf(df.format((this.open - this.low) / this.open * 100)) + "%,"
				);
	}
}

class BTCDSliceRecord extends BTCBasicRecord {
	boolean init_flag;
}


class BTCTotalRecord extends BTCBasicRecord {
	MaRet ma_record;
	BollRet boll_record;
	MacdRet macd_record;
	
	public BTCTotalRecord(BTCBasicRecord record) {
		this.high	= record.high;
		this.low	= record.low;
		this.open	= record.open;
		this.close	= record.close;
	}
}
	
/**
 * 所有交易数据相关操作
 * @author latupa
 *
 */
public class BTCData {
	
	public static final Log log = LogFactory.getLog(BTCData.class);
	
	//记录最近一个K线周期的数值
	public BTCDSliceRecord btc_s_record = new BTCDSliceRecord();
	
	//记录运行时间内的所有K线周期数据
	public TreeMap<String, BTCTotalRecord> b_record_map = new TreeMap<String, BTCTotalRecord>();
	
	//BTC行情接口
//	public static final String URL_PRICE = "https://www.okcoin.com/api/ticker.do?symbol=ltc_cny";
	public static final String URL_PRICE = "https://www.okcoin.com/api/ticker.do";
	
	//数据库连接
	public DBInst dbInst;  
	
	//数据库配置文件
	public static final String FLAG_FILE_DIR = "src/main/resources/";
	public static final String dbconf_file = "db.flag";
	
	public static final String BTC_PRICE_TABLE = "btc_price";
	public static final String BTC_TRANS_TABLE = "btc_trans";
	
	public BTCData() {
		this.dbInst	= ConnectDB();
		DBInit();
		BTCSliceRecordInit();
	}
	
	public synchronized void BTCSliceRecordInit() {
		this.btc_s_record.high	= 0;
		this.btc_s_record.low	= 0;
		this.btc_s_record.open	= 0;
		this.btc_s_record.close	= 0;
		this.btc_s_record.init_flag	= true;
	}
	
	public void DBInit() {
		String sql = "create table if not exists " + BTC_PRICE_TABLE + 
				"(`time` DATETIME not null default '0000-00-00 00:00:00', " +
				"`open` double NOT NULL default '0', " +
				"`close` double NOT NULL default '0', " +
				"`high` double NOT NULL default '0', " +
				"`low` double NOT NULL default '0', " +
				"`ma5` double NOT NULL default '0', " +
				"`ma10` double NOT NULL default '0', " +
				"`ma20` double NOT NULL default '0', " +
				"`ma30` double NOT NULL default '0', " +
				"`ma60` double NOT NULL default '0', " +
				"`ma120` double NOT NULL default '0', " +
				"`upper` double NOT NULL default '0', " +
				"`mid` double NOT NULL default '0', " +
				"`lower` double NOT NULL default '0', " +
				"`bbi` double NOT NULL default '0', " +
				"`diff` double NOT NULL default '0', " +
				"`dea` double NOT NULL default '0', " +
				"`macd` double NOT NULL default '0', " +
				"PRIMARY KEY (`time`)" +
				") ENGINE=InnoDB DEFAULT CHARSET=utf8";	
		
		dbInst.updateSQL(sql);
		
	}
	
	/**
	 * 对BTCTotalRecord映射表的操作：获取指定时间的record
	 * @param time
	 * @return
	 */
	public BTCTotalRecord BTCRecordOptGetByTime(String time) {
		if (this.b_record_map.containsKey(time)) {
			return this.b_record_map.get(time);
		}
		else {
			return null;
		}
	}
	
	/**
	 * 对BTCTotalRecord映射表的操作：获取指定周期的record
	 * @param cycle 1表示1个周期前record，以此类推，0表示当前最新周期record
	 * @return
	 */
	public BTCTotalRecord BTCRecordOptGetByCycle(int cycle) {
		for (String time : this.b_record_map.descendingKeySet().toArray(new String[0])) {
			if (cycle == 0) {
				return this.b_record_map.get(time);
			}
			cycle--;
		}
		
		return null;
	}
	
	/**
	 * 更新基础价格信息到DB
	 * @param time
	 */
	public void BTCRecordDBInsert(String time) {
		
		String sql = "insert into " + BTC_PRICE_TABLE + 
				"(`time`, `open`, `close`, `high`, `low`) values ('" +
				time + "', " +
				this.btc_s_record.open + ", " +
				this.btc_s_record.close + ", " +
				this.btc_s_record.high + ", " +
				this.btc_s_record.low + ")";

		dbInst.updateSQL(sql);
	}
	
	
	/**
	 * 更新Ma数据到DB
	 * @param time
	 */
	public void BTCMaRetDBUpdate(String time) {
		if (this.b_record_map.containsKey(time)) {
			BTCTotalRecord record = this.b_record_map.get(time);
			String sql = "update " + BTC_PRICE_TABLE + " set " +
					"ma5=" + record.ma_record.ma5 + ", " +
					"ma10=" + record.ma_record.ma10 + ", " +
					"ma20=" + record.ma_record.ma20 + ", " +
					"ma30=" + record.ma_record.ma30 + ", " +
					"ma60=" + record.ma_record.ma60 + ", " +
					"ma120=" + record.ma_record.ma120 + 
					" where time = '" + time + "'";
			
			dbInst.updateSQL(sql);
		}
		else {
			log.error("time " + time + "is not in db");
			System.exit(1);
		}
	}
	
	/**
	 * 更新Boll数据到DB
	 * @param time
	 */
	public void BTCBollRetDBUpdate(String time) {
		if (this.b_record_map.containsKey(time)) {
			BTCTotalRecord record = this.b_record_map.get(time);
			String sql = "update " + BTC_PRICE_TABLE + " set " +
					"upper=" + record.boll_record.upper + ", " +
					"mid=" + record.boll_record.mid + ", " +
					"lower=" + record.boll_record.lower + ", " +
					"bbi=" + record.boll_record.bbi + 
					" where time = '" + time + "'";
			
			dbInst.updateSQL(sql);
		}
		else {
			log.error("time " + time + "is not in db");
			System.exit(1);
		}
	}
	
	/**
	 * 更新Macd数据到DB
	 * @param time
	 */
	public void BTCMacdRetDBUpdate(String time) {
		if (this.b_record_map.containsKey(time)) {
			BTCTotalRecord record = this.b_record_map.get(time);
			String sql = "update " + BTC_PRICE_TABLE + " set " +
					"diff=" + record.macd_record.diff + ", " +
					"dea=" + record.macd_record.dea + ", " +
					"macd=" + record.macd_record.macd + 
					" where time = '" + time + "'";
			
			dbInst.updateSQL(sql);
		}
		else {
			log.error("time " + time + "is not in db");
			System.exit(1);
		}
	}
	
	
	/**
	 * 计算Macd
	 * @param btc_func
	 * @param time
	 * @param cycle_data
	 * @return
	 * @throws ParseException 
	 */
	public MacdRet BTCCalcMacd(BTCFunc btc_func, String time, int cycle_data) throws ParseException {
		return btc_func.macd(this.b_record_map, time, cycle_data);
	}
	
	/**
	 * 计算Boll
	 * @param btc_func
	 * @param time
	 * @return
	 */
	public BollRet BTCCalcBoll(BTCFunc btc_func, String time) {
		return btc_func.boll(this.b_record_map, time);
	}
	
	/**
	 * 计算均线
	 * @param btc_func
	 * @param time
	 * @return
	 */
	public MaRet BTCCalcMa(BTCFunc btc_func, String time) {
		ArrayList<Integer> mas = new ArrayList<Integer>();
		mas.add(new Integer(5));
		mas.add(new Integer(10));
		mas.add(new Integer(20));
		mas.add(new Integer(30));
		mas.add(new Integer(60));
		mas.add(new Integer(120));
		
		TreeMap<Integer, Double> ret = btc_func.ma(this.b_record_map, time, mas, 0);
		
		MaRet maret = new MaRet();
		maret.ma5	= ret.get(5);
		maret.ma10	= ret.get(10);
		maret.ma20	= ret.get(20);
		maret.ma30	= ret.get(30);
		maret.ma60	= ret.get(60);
		maret.ma120	= ret.get(120);
		
		return maret;
	}
	
	
	/**
	 * 更新基础价格信息到内存映射表中
	 * @param time
	 */
	public void BTCRecordMemInsert(String time) {
		if (this.btc_s_record.init_flag == false) {
			BTCTotalRecord record = new BTCTotalRecord(this.btc_s_record);
			this.b_record_map.put(time, record);
		}
	}
	
	/**
	 * 把均线值更新到内存中
	 * @param time
	 * @param ma_ret
	 */
	public void BTCMaRetMemUpdate(String time, MaRet ma_ret) {
		if (this.b_record_map.containsKey(time)) {
			BTCTotalRecord record = this.b_record_map.get(time);
			record.ma_record = new MaRet(ma_ret);
			this.b_record_map.put(time, record);
		}
		else {
			log.error("time " + time + "is not in mem");
			System.exit(1);
		}
	}
	
	/**
	 * 把Boll线值更新到内存中
	 * @param time
	 * @param boll_ret
	 */
	public void BTCBollRetMemUpdate(String time, BollRet boll_ret) {
		if (this.b_record_map.containsKey(time)) {
			BTCTotalRecord record = this.b_record_map.get(time);
			record.boll_record = new BollRet(boll_ret);
			this.b_record_map.put(time, record);
		}
		else {
			log.error("time " + time + "is not in mem");
			System.exit(1);
		}
	}
	
	/**
	 * 把Macd值更新到内存中
	 * @param time
	 * @param boll_ret
	 */
	public void BTCMacdRetMemUpdate(String time, MacdRet macd_ret) {
		if (this.b_record_map.containsKey(time)) {
			BTCTotalRecord record = this.b_record_map.get(time);
			record.macd_record = new MacdRet(macd_ret);
			this.b_record_map.put(time, record);
		}
		else {
			log.error("time " + time + "is not in mem");
			System.exit(1);
		}
	}
	
	public void BTCRecordMemShow() {
		for (String time : this.b_record_map.keySet().toArray(new String[0])) {
			BTCBasicRecord record = this.b_record_map.get(time);
			System.out.println("time:" + time);
			record.Show();
		}
		System.out.println(b_record_map.size() + " records");
	}
	
	/**
	 * 更新BTCSliceRecord的值
	 * @throws IOException 
	 */
	public synchronized boolean BTCSliceRecordUpdate() {
		
		double last = FetchRT();
//		double last = FetchRTWeb();
		
		//获取失败，则不用更新
		if (last == 0) {
			return false;
		}
		
		if (this.btc_s_record.init_flag == true) {
			this.btc_s_record.high	= last;
			this.btc_s_record.low	= last;
			this.btc_s_record.open	= last;
			this.btc_s_record.close	= last;
			this.btc_s_record.init_flag	= false;
		}
		else {
			this.btc_s_record.high	= (last > this.btc_s_record.high) ? last : this.btc_s_record.high;
			this.btc_s_record.low	= (last < this.btc_s_record.low) ? last : this.btc_s_record.low;
			this.btc_s_record.close	= last;
		}
		
		btc_s_record.Show();
		return true;
	}
	
	public double FetchRTWeb() throws IOException {
		URL url = null;
		double last = 0;
		log.info("start fetch web");
		try {
			
            url = new URL("https://www.okcoin.com/market.do");
            
    		InputStream in = url.openStream();
    		BufferedReader bin = new BufferedReader(new InputStreamReader(in, "utf8"));
    		String s;
    		log.info("hello");
    		if ((s = bin.readLine()) != null) {
    			log.info("readline");
    			log.info(s);
    			if (s.equals("最新价格：")) {
    				System.out.println(s);
    			}
    		}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return last;
	}

	/**
	 * 获取当前数据
	 * @return
	 */
	public double FetchRT() {
		
		URL url = null;
		double last = 0;
		try {
			url = new URL(URL_PRICE);
		
			InputStream in = url.openStream();
			BufferedReader bin = new BufferedReader(new InputStreamReader(in, "utf8"));
			String s = null;
			if ((s = bin.readLine()) != null) {
//				System.out.println(s);
				
				//触发了接口防抓取
				if (!s.startsWith("{")) {
					log.info("fetch failed!");
					return last;
				}
				
				try {
					JSONObject jsonObj = JSONObject.fromObject(s);
					if (jsonObj.has("ticker")) {
						String s1 = jsonObj.getString("ticker");
						JSONObject jsonObj1 = JSONObject.fromObject(s1);
						if (jsonObj1.has("last")) {
							last = jsonObj1.getDouble("last");
						}
						else {
							log.error("key \"last\" error !" + s1);
							return last;
						}
					}
					else {
						log.error("key \"ticker\" error !" + s);
						return last;
					}
						
					//System.out.println("last:" + last);
				}
				catch (Exception e) {
					log.error("parse json failed! json:" + s, e);
				}
			}
			else {
				log.error("request return null! url:" + url.toString());
			}
			
			bin.close();
		}
		catch (Exception e) {
			log.error("request failed! url:" + url.toString(), e);
		}
		
		return last;
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
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		BTCData btc_data = new BTCData();
		while (true) {
			try {
				btc_data.BTCSliceRecordUpdate();
				Thread.sleep(2000);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
	}

}
