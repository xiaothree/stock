package com.latupa.stock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * 数据抓取线程
 * @author latupa
 *
 */
public class BTCUpdateThread extends Thread {
	
	public static final Log log = LogFactory.getLog(BTCUpdateThread.class);
	
	public BTCTransSystem btc_trans_sys;
	
	public BTCUpdateThread(BTCTransSystem btc_trans_sys) {
		this.btc_trans_sys	= btc_trans_sys;
	}

	public void run() {
		
		log.info("BTCUpdateThread start");
		
		long stamp_millis = System.currentTimeMillis();
		long stamp_sec = stamp_millis / 1000;
		
		//同步时间到分钟整点
		while (stamp_sec % 60 != 0) {
			log.info("sync for minute, now is " + stamp_sec);
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			stamp_millis = System.currentTimeMillis();
			stamp_sec = stamp_millis / 1000;
		}
		
		long last_stamp_sec = 0;
		
		log.info("start update process");
		
		while (true) {
			
			stamp_millis = System.currentTimeMillis();
			stamp_sec = stamp_millis / 1000;
			
			if (stamp_sec >= (last_stamp_sec + this.btc_trans_sys.cycle_fetch)) {
				log.info("fetch:" + stamp_sec);
				
				if (this.btc_trans_sys.btc_data.BTCSliceRecordUpdate() == false) {
					log.info("sleep 30 sec");
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
				last_stamp_sec = stamp_sec; 
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
