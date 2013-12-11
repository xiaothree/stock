package com.latupa.stock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BTCData {
	
	public static final Log log = LogFactory.getLog(BTCData.class);
	
	public int data_cycle;	//K线周期(s)
	public int fetch_cycle;	//数据采集周期(s)
	
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

	}

}
