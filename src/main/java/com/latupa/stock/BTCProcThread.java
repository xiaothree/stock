package com.latupa.stock;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 处理周期K线，以及所有和判断、买卖操作相关的工作
 * @author latupa
 *
 */
public class BTCProcThread extends Thread {
		
	public static final Log log = LogFactory.getLog(BTCProcThread.class);
	
	public BTCTransSystem btc_trans_sys;
	
	public BTCProcThread(BTCTransSystem btc_trans_sys) {
		this.btc_trans_sys	= btc_trans_sys;
	}
	
	public void run() {
		
		log.info("BTCProcThread start");
		
		long stamp_millis;
		long stamp_sec;
		DateFormat df	= new SimpleDateFormat("yyyyMMddHHmmss");  
		
		while (true) {
			
			stamp_millis = System.currentTimeMillis();
			stamp_sec = stamp_millis / 1000;

				if ((stamp_sec % this.btc_trans_sys.cycle_data) == 0 && 
						(this.btc_trans_sys.btc_data.btc_s_record.init_flag == false)) {
					log.info("start update to system");
					Date cur_date = new Date(stamp_millis);
					String sDateTime = df.format(cur_date); 
					this.btc_trans_sys.btc_data.BTCRecordMemInsert(sDateTime);
					this.btc_trans_sys.btc_data.BTCRecordDBInsert(sDateTime);
					this.btc_trans_sys.btc_data.BTCSliceRecordInit();
					this.btc_trans_sys.btc_data.BTCRecordMemShow();
					
					
					//计算5、10周期均线
					ArrayList<Integer> mas = new ArrayList<Integer>();
					mas.add(new Integer(5));
					mas.add(new Integer(10));
					
					TreeMap<Integer, Double> ret = this.btc_trans_sys.btc_func.ma(this.btc_trans_sys.btc_data.b_record_map, sDateTime, mas, 0);
					
					//计算布林线
					BollRet bollret;
					bollret = this.btc_trans_sys.btc_func.boll(this.btc_trans_sys.btc_data.b_record_map, sDateTime);
					
					//计算macd值
					try {
						MacdRet macdret = this.btc_trans_sys.btc_func.macd(this.btc_trans_sys.btc_data.b_record_map, sDateTime, this.btc_trans_sys.cycle_data);
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else {
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
		}
	}
}
