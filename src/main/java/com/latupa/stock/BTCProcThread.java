package com.latupa.stock;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 处理周期K线更新，以及触发所有和判断、买卖操作相关的工作
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

			if ((stamp_sec % this.btc_trans_sys.cycle_data == 0) && 
					(this.btc_trans_sys.btc_data.btc_s_record.init_flag == false)) {
				log.info("start update to system");
				
				Date cur_date = new Date(stamp_millis);
				String sDateTime = df.format(cur_date); 
				this.btc_trans_sys.btc_data.BTCRecordMemInsert(sDateTime);
				this.btc_trans_sys.btc_data.BTCRecordDBInsert(sDateTime);
				this.btc_trans_sys.btc_data.BTCSliceRecordInit();
				this.btc_trans_sys.btc_data.BTCRecordMemShow();
				
				//计算均线
				MaRet ma_ret = this.btc_trans_sys.btc_data.BTCCalcMa(this.btc_trans_sys.btc_func, sDateTime);
				this.btc_trans_sys.btc_data.BTCMaRetMemUpdate(sDateTime, ma_ret);
				this.btc_trans_sys.btc_data.BTCMaRetDBUpdate(sDateTime);
				
				//计算布林线
				BollRet boll_ret = this.btc_trans_sys.btc_data.BTCCalcBoll(this.btc_trans_sys.btc_func, sDateTime);
				this.btc_trans_sys.btc_data.BTCBollRetMemUpdate(sDateTime, boll_ret);
				this.btc_trans_sys.btc_data.BTCBollRetDBUpdate(sDateTime);
				
				//计算Macd值
				try {
					MacdRet macd_ret = this.btc_trans_sys.btc_data.BTCCalcMacd(this.btc_trans_sys.btc_func, sDateTime, this.btc_trans_sys.cycle_data);
					this.btc_trans_sys.btc_data.BTCMacdRetMemUpdate(sDateTime, macd_ret);
					this.btc_trans_sys.btc_data.BTCMacdRetDBUpdate(sDateTime);
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				this.btc_trans_sys.btc_k_cycles++;
				if (this.btc_trans_sys.btc_k_cycles >= 30) {
					
					BTCTransRecord btc_trans_rec = new BTCTransRecord(this.btc_trans_sys.btc_data.dbInst);
					btc_trans_rec.InitTable();
					
					this.btc_trans_sys.btc_trans_stra.InitPoint();
					this.btc_trans_sys.btc_trans_stra.CheckPoint(this.btc_trans_sys.btc_data);
					
					BTCTotalRecord record	= this.btc_trans_sys.btc_data.BTCRecordOptGetByCycle(0);
					
					//如果还未入场，则判断是否要入场
					if (this.btc_trans_sys.btc_trans_stra.curt_status == BTCTransStrategy1.STATUS.READY) {
						
						if (this.btc_trans_sys.btc_trans_stra.IsBuy() == true) {
							
							this.btc_trans_sys.btc_trans_stra.curt_status = BTCTransStrategy1.STATUS.BUYIN;
							this.btc_trans_sys.btc_curt_position = 10;
							this.btc_trans_sys.btc_curt_quantity = this.btc_trans_sys.BTC_INIT_AMOUNT / record.close;
							
							this.btc_trans_sys.btc_buy_price	= record.close;
							log.info("TransProcess: buy price:" + record.close);
							
							btc_trans_rec.InsertTrans(sDateTime, 
									BTCTransRecord.OPT.OPT_BUY, 
									this.btc_trans_sys.btc_curt_quantity, 
									record.close);
						}
					}
					//如果已入场，则判断是否要出场
					else if (this.btc_trans_sys.btc_trans_stra.curt_status != BTCTransStrategy1.STATUS.READY) {
						
						//判断是否要出场
						int position = this.btc_trans_sys.btc_trans_stra.IsSell();
						
						//需要出场
						if (position > 0) {
							
							log.info("TransProcess: sell price:" + record.close);
									
							this.btc_trans_sys.btc_curt_position -= position;
							double sell_quantity = this.btc_trans_sys.btc_curt_quantity * position / 10;
							this.btc_trans_sys.btc_curt_quantity -= sell_quantity;
							
							this.btc_trans_sys.btc_profit	+= ((record.close - this.btc_trans_sys.btc_buy_price) * sell_quantity); 
							this.btc_trans_sys.btc_accumulate_profit	+= this.btc_trans_sys.btc_profit;
							
							if (this.btc_trans_sys.btc_curt_position == 0) {
								log.info("TransProcess: this profit:" + this.btc_trans_sys.btc_profit + ", accu profit:" + this.btc_trans_sys.btc_accumulate_profit);
								this.btc_trans_sys.btc_profit	= 0;
							}
							
							btc_trans_rec.InsertTrans(sDateTime, 
									BTCTransRecord.OPT.OPT_SELL, 
									sell_quantity, 
									record.close);
						}
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
}
