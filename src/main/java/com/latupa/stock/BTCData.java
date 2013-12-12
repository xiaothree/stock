package com.latupa.stock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
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


public class BTCData {
	
	public static final Log log = LogFactory.getLog(BTCData.class);
	
	//记录最近一个K线周期的数值
	public BTCDSliceRecord btc_s_record = new BTCDSliceRecord();
	
	//记录运行时间内的所有K线周期数据
	public TreeMap<String, BTCBasicRecord> b_record_map = new TreeMap<String, BTCBasicRecord>();
	
	//BTC行情接口
//	public static final String URL_PRICE = "https://www.okcoin.com/api/ticker.do?symbol=ltc_cny";
	public static final String URL_PRICE = "https://www.okcoin.com/api/ticker.do";
	
	//数据库连接
	public DBInst dbInst;  
	
	//数据库配置文件
	public static final String FLAG_FILE_DIR = "src/main/resources/";
	public static final String dbconf_file = "db.flag";
	
	public BTCData() {
		//this.dbInst 		= ConnectDB();

		BTCSliceRecordInit();
	}
	
	public void BTCSliceRecordInit() {
		this.btc_s_record.high	= 0;
		this.btc_s_record.low	= 0;
		this.btc_s_record.open	= 0;
		this.btc_s_record.close	= 0;
		this.btc_s_record.init_flag	= true;
	}
	
	/**
	 * 
	 * @param time
	 */
	public void BTCRecordMemInsert(String time) {
		if (this.btc_s_record.init_flag == false) {
			BTCBasicRecord record = new BTCBasicRecord(this.btc_s_record);
			this.b_record_map.put(time, record);
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
	public boolean BTCSliceRecordUpdate() {
		
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
