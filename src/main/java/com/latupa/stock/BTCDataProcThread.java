package com.latupa.stock;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 针对抓取到的数据构造K线，同时计算指标值，更新到内存和数据库中
 * @author latupa
 *
 */
public class BTCDataProcThread extends Thread {
	public static final Log log = LogFactory.getLog(BTCDataProcThread.class);
	
	public BTCUpdateSystem btc_update_sys;
	
	public BTCDataProcThread(BTCUpdateSystem btc_update_sys) {
		this.btc_update_sys	= btc_update_sys;
	}
	
	/**
	 * 每条更新的价格记录，计算指标
	 * @param sDateTime
	 */
	private void CalcFunc(String sDateTime, int data_cycle) {
		
		BTCData btc_data = this.btc_update_sys.data_map.get(data_cycle);
		
		//更新基础数值
		btc_data.BTCRecordMemInsert(sDateTime);
		btc_data.BTCRecordDBInsert(sDateTime);
		btc_data.BTCSliceRecordInit();
		//this.btc_trans_sys.btc_data.BTCRecordMemShow();
		
		//计算均线
		MaRet ma_ret = btc_data.BTCCalcMa(this.btc_update_sys.btc_func, sDateTime);
		btc_data.BTCMaRetMemUpdate(sDateTime, ma_ret);
		btc_data.BTCMaRetDBUpdate(sDateTime);
		
		//计算布林线
		BollRet boll_ret = btc_data.BTCCalcBoll(this.btc_update_sys.btc_func, sDateTime);
		btc_data.BTCBollRetMemUpdate(sDateTime, boll_ret);
		btc_data.BTCBollRetDBUpdate(sDateTime);
		
		//计算Macd值
		try {
			MacdRet macd_ret = btc_data.BTCCalcMacd(this.btc_update_sys.btc_func, sDateTime, data_cycle);
			btc_data.BTCMacdRetMemUpdate(sDateTime, macd_ret);
			btc_data.BTCMacdRetDBUpdate(sDateTime);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void run() {
		
		DateFormat df	= new SimpleDateFormat("yyyyMMddHHmmss"); 
		
		long stamp_millis;
		long stamp_sec;
		
		long last_stamp_sec = 0;
		
		while (true) {
			
			stamp_millis = System.currentTimeMillis();
			stamp_sec = stamp_millis / 1000;
			
			//防止同一秒钟内处理多次
			if (stamp_sec == last_stamp_sec) {
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				continue;
			}
			
			for (int data_cycle : this.btc_update_sys.data_map.keySet()) {
					
				if (stamp_sec % (24 * 60 * 60) == 0) {	//每天清理一次内存中的历史数据
					log.info("clean cycle " + data_cycle);
					
					BTCData btc_data = this.btc_update_sys.data_map.get(data_cycle);
					btc_data.BTCDataMemoryClean(2);
				}
				
				if (stamp_sec % data_cycle == 0) {	
					log.info("trigger cycle " + data_cycle);
					
					BTCData btc_data = this.btc_update_sys.data_map.get(data_cycle);
					BTCDSliceRecord slice_record = btc_data.btc_s_record;
					
					int count = this.btc_update_sys.count_num_map.get(data_cycle);
					this.btc_update_sys.count_num_map.put(data_cycle, ++count);
					
					//第一次先不处理，避免数据不完整
					if (count == 1) {
						
						log.info("skip for first proc cycle " + data_cycle);
						btc_data.BTCSliceRecordInit();
						
						//加载数据库中的历史数据到内存中
						log.info("load data from db for cycle " + data_cycle);
						btc_data.BTCDataLoadFromDB(0);
						log.info("load finish");
						continue;
					}
					
					if (slice_record.init_flag == false) {
					
						log.info("update cycle " + data_cycle);
						slice_record.Show();

						int curt_k_num = btc_data.b_record_map.size();
						
						log.info("new k(" + data_cycle + "):" + curt_k_num);
						
						Date cur_date = new Date(stamp_millis);
						String sDateTime = df.format(cur_date); 
						
						CalcFunc(sDateTime, data_cycle);
					}
				}
			}
			
			last_stamp_sec = stamp_sec;
			
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
