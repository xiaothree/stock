package com.latupa.stock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import net.sf.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



class BTCRecord {
	double high;
	double low;
	double last;
}

public class BTCData {
	
	public static final Log log = LogFactory.getLog(BTCData.class);
	
	//K线周期(s)
	public int data_cycle;	
	
	//数据采集周期(s)
	public int fetch_cycle;	
	
	//记录K线周期的数值
	public BTCRecord record;
	
	//BTC行情接口
	public static final String URL_PRICE = "https://www.okcoin.com/api/ticker.do";
	
	//数据库连接
	public DBInst dbInst;  
	
	//数据库配置文件
	public static final String FLAG_FILE_DIR = "src/main/resources/";
	public static final String dbconf_file = "db.flag";
	
	public BTCData(int data_cycle, int fetch_cycle) {
		this.dbInst 		= ConnectDB();
		this.data_cycle		= data_cycle;
		this.fetch_cycle	= fetch_cycle;
	}
	
	public void CleanBTCRecord() {
		this.record.high	= 0;
		this.record.low		= 0;
		this.record.last	= 0;
	}
	
	/**
	 * 更新BTCRecord的值
	 */
	public void UpdateRecord() {
		
		double last = FetchRT();
		
		if (this.record.last == 0) {
			this.record.high	= last;
			this.record.low		= last;
		}
		else {
			this.record.high	= (last > this.record.high) ? last : this.record.high;
			this.record.low		= (last < this.record.low) ? last : this.record.low;
		}
		
		this.record.last	= last;
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
			BufferedReader bin = new BufferedReader(new InputStreamReader(in, "gbk"));
			String s = null;
			if ((s = bin.readLine()) != null) {
				System.out.println(s);
		        JSONObject jsonObj = JSONObject.fromObject(JSONObject.fromObject(s).getString("ticker"));
		        last = jsonObj.getDouble("last");
		        System.out.println("last:" + last);
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
		BTCData btc_data = new BTCData(10, 1);
		btc_data.FetchRT();
		
	}

}
