package com.latupa.stock;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.latupa.stock.BTCTransSystem.MODE;

/**
 * 处理周期K线更新，以及触发所有和判断、买卖操作相关的工作
 * @author latupa
 *
 * TODO
 * 1. 年息统计数据抽取出去
 */
public class BTCProcThread extends Thread {
		
	public static final Log log = LogFactory.getLog(BTCProcThread.class);
	
	public BTCTransSystem btc_trans_sys;
	
	public BTCProcThread(BTCTransSystem btc_trans_sys) {
		this.btc_trans_sys	= btc_trans_sys;
	}
	
	/**
	 * 处理买卖相关
	 * @param sDateTime
	 */
	private void ProcTrans(String sDateTime) {
		
		BTCTransRecord btc_trans_rec = new BTCTransRecord(this.btc_trans_sys.btc_data.dbInst);
		btc_trans_rec.InitTable();
		
		this.btc_trans_sys.btc_trans_stra.InitPoint();
		this.btc_trans_sys.btc_trans_stra.CheckPoint(this.btc_trans_sys.btc_data);
		
		BTCTotalRecord record	= this.btc_trans_sys.btc_data.BTCRecordOptGetByCycle(0);
		DecimalFormat df1 = new DecimalFormat("#0.00");
		
		//如果还未入场，则判断是否要入场
		if (this.btc_trans_sys.btc_trans_stra.curt_status == BTCTransStrategy2.STATUS.READY) {
			
			if (this.btc_trans_sys.btc_trans_stra.IsBuy(sDateTime) == true) {
				this.btc_trans_sys.BuyAction(sDateTime, record.close);
			}
		}
		//如果已入场，则判断是否要出场
		else if (this.btc_trans_sys.btc_trans_stra.curt_status != BTCTransStrategy2.STATUS.READY) {
			
			//判断是否要出场
			int sell_position = this.btc_trans_sys.btc_trans_stra.IsSell(sDateTime);
			
			//需要出场
			if (sell_position > 0) {
				
				this.btc_trans_sys.btc_trans_stra.CleanStatus();
				this.btc_trans_sys.SellAction(sDateTime, record.close, sell_position);
			}
		}
		
		//初始化记录每年信息的起始年
		if (this.btc_trans_sys.btc_year_time == null) {
			this.btc_trans_sys.btc_year_time = sDateTime.substring(0, 4);
			this.btc_trans_sys.btc_year_price_init = record.close;
		}
		//记录每年年终信息
		else if (!sDateTime.substring(0, 4).equals(this.btc_trans_sys.btc_year_time)) {
			log.info("TransProcess: year:" + this.btc_trans_sys.btc_year_time +
					", price init:" + df1.format(this.btc_trans_sys.btc_year_price_init) +
					", price last:" + df1.format(record.close) + "(" + df1.format((record.close - this.btc_trans_sys.btc_year_price_init) / this.btc_trans_sys.btc_year_price_init * 100) + "%)" +
					", amount init:" + df1.format(this.btc_trans_sys.btc_year_amount_init) +
					", amount last:" + df1.format(this.btc_trans_sys.btc_curt_amount) + "(" + df1.format((this.btc_trans_sys.btc_curt_amount - this.btc_trans_sys.btc_year_amount_init) / this.btc_trans_sys.btc_year_amount_init * 100) + "%)");
			
			this.btc_trans_sys.btc_year_time = sDateTime.substring(0, 4);
			this.btc_trans_sys.btc_year_amount_init	= this.btc_trans_sys.btc_curt_amount;
			this.btc_trans_sys.btc_year_price_init	= record.close;
		}
	}
	
	/**
	 * 每条更新的价格记录，计算指标
	 * @param sDateTime
	 */
	private void CalcFunc(String sDateTime) {
		
		//更新基础数值
		this.btc_trans_sys.btc_data.BTCRecordMemInsert(sDateTime);
		this.btc_trans_sys.btc_data.BTCRecordDBInsert(sDateTime);
		this.btc_trans_sys.btc_data.BTCSliceRecordInit();
		//this.btc_trans_sys.btc_data.BTCRecordMemShow();
		
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
	}
	
	public void run() {
		
		//复盘
		if (this.btc_trans_sys.mode == MODE.REVIEW) {
			log.info("BTCProcThread start by mock src");
			
			//从数据库mock数值的初始化
			this.btc_trans_sys.btc_data.UpdateMockInit();
			
			String sDateTime	= null;
			
			//每次mock一条
			while (this.btc_trans_sys.btc_data.UpdateMockGet()) {
				sDateTime	= String.valueOf((int)this.btc_trans_sys.btc_data.btc_s_record.open);
				System.out.println(sDateTime);
				CalcFunc(sDateTime);
				this.btc_trans_sys.btc_k_cycles++;
				if (this.btc_trans_sys.btc_k_cycles >= 30) {
					ProcTrans(sDateTime);
				}
			}
		}
		else {
			log.info("BTCProcThread start online");
			
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
					
					this.btc_trans_sys.btc_k_cycles++;
					log.info("start update to system:" + this.btc_trans_sys.btc_k_cycles + " records");
					
					try {
						this.btc_trans_sys.TradeModeSwitch();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						log.error("mode switch failed!", e);
						System.exit(0);
					}
					
					Date cur_date = new Date(stamp_millis);
					String sDateTime = df.format(cur_date); 
					
					CalcFunc(sDateTime);
					
					if (this.btc_trans_sys.btc_k_cycles >= 30) {
						ProcTrans(sDateTime);
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
}
