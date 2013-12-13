package com.latupa.stock;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BTCTransSystem {
	
	public static final Log log = LogFactory.getLog(BTCTransSystem.class);
	
	//K线周期(s)
	public int data_cycle;	
	
	//数据采集周期(s)
	public int fetch_cycle;	
	
	//BTC数据
	public BTCData btc_data = new BTCData();
	
	//BTC计算公式
	public BTCFunc btc_func = new BTCFunc();
	
	public BTCTransSystem(int data_cycle, int fetch_cycle) {
		this.data_cycle		= data_cycle;
		this.fetch_cycle	= fetch_cycle;
	}
	
	public void Route() throws InterruptedException {
		
		long stamp_millis = System.currentTimeMillis();
		long stamp_sec = stamp_millis / 1000;
		
		//同步时间到分钟整点
		while (stamp_sec % 60 != 0) {
			log.info("skip for " + stamp_sec);
			Thread.sleep(200);
			stamp_millis = System.currentTimeMillis();
			stamp_sec = stamp_millis / 1000;
		}
		
		DateFormat df	= new SimpleDateFormat("yyyyMMddHHmmss");  
		long last_stamp_sec = 0;
		
		log.info("start process");
		
		while (true) {
			
			stamp_millis = System.currentTimeMillis();
			stamp_sec = stamp_millis / 1000;
			
			//log.info(stamp_sec);
			if (stamp_sec >= last_stamp_sec + this.fetch_cycle) {
				log.info("fetch:" + stamp_sec);
				
				if (btc_data.BTCSliceRecordUpdate() == false) {
					log.info("sleep 30 sec");
					Thread.sleep(30000);
				}
				
				if (stamp_sec % this.data_cycle == 0) {
					log.info("start update to mem");
					Date cur_date = new Date(stamp_millis);
					String sDateTime = df.format(cur_date); 
					btc_data.BTCRecordMemInsert(sDateTime);
					btc_data.BTCRecordDBInsert(sDateTime);
					btc_data.BTCSliceRecordInit();
					btc_data.BTCRecordMemShow();
					
					
					//计算5、10周期均线
					ArrayList<Integer> mas = new ArrayList<Integer>();
					mas.add(new Integer(5));
					mas.add(new Integer(10));
					
					TreeMap<Integer, Double> ret = btc_func.ma(btc_data.b_record_map, sDateTime, mas, 0);
					
					//计算布林线
					BollRet bollret;
					bollret = btc_func.boll(btc_data.b_record_map, sDateTime);
				}
				
				last_stamp_sec = stamp_sec; 
			}
			else {
				Thread.sleep(200);
			}
		}
	}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		BTCTransSystem btc_ts = new BTCTransSystem(30, 5);
		try {
			btc_ts.Route();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
