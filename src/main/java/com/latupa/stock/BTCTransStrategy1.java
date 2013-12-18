package com.latupa.stock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 交易策略
      买入：
	1. macd金叉||（多头&&macd>0&& macd变短再变长）
      卖出：
	1. 是否多头&&贴boll上轨
		1）是，则等两次死叉，第一次死叉出一半；然后再死叉（或者变长再变短），出另一半
		2）否，macd一重顶出一半，二重顶再出另一半，如果直接死叉则全部出
 * @author latupa
 */
public class BTCTransStrategy1 implements BTCTransStrategy {
	
	public static final Log log = LogFactory.getLog(BTCTransStrategy1.class);
	
	public enum STATUS {
		READY,	//待买，即空仓
		BUYIN,	//买入
		BULL,	//多头且贴上轨
		HALF;	//半仓
	};
	
	//当前状态
	public STATUS curt_status = STATUS.READY;
	
	//金叉、死叉
	public boolean is_dead_cross	= false;
	public boolean is_gold_cross	= false;
	
	//多头
	public boolean is_bull	= false;
	
	//贴上轨
	public boolean is_boll_up	= false;
	
	//macd顶、底
	public boolean is_macd_top		= false;	//macd>0 顶
	public boolean is_macd_bottom	= false;	//macd<0 底
	public boolean is_macd_up		= false;	//macd>0 变短再变长
	public boolean is_macd_down		= false;	//macd<0 变短再变长
	
	public BTCTransStrategy1() {
		this.InitPoint();
	}
	
	public void InitPoint() {
		this.is_dead_cross	= false;
		this.is_gold_cross	= false;
		this.is_boll_up		= false;
		this.is_bull		= false;
		this.is_macd_top	= false;
		this.is_macd_bottom	= false;
		this.is_macd_up		= false;
		this.is_macd_down	= false;
	}
	
	public void CheckPoint(BTCData btc_data) {
		BTCTotalRecord record	= btc_data.BTCRecordOptGetByCycle(0);
		BTCTotalRecord record_1cycle_before	= btc_data.BTCRecordOptGetByCycle(1);
		BTCTotalRecord record_2cycle_before	= btc_data.BTCRecordOptGetByCycle(2);
		
		//多头
		if ((record.ma_record.ma5 > record.ma_record.ma10 && 
				record.ma_record.ma10 > record.ma_record.ma20 &&
				record.ma_record.ma20 > record.ma_record.ma30 &&
				record.ma_record.ma30 > record.ma_record.ma60)) {
			this.is_bull	= true;
		}
		
		//贴上轨
		if (record.close > record.boll_record.upper &&
						record.close > record.open) {
			this.is_boll_up	= true;
		}
		
		//死叉
		if ((record.macd_record.diff < record.macd_record.dea) &&
				(record_1cycle_before.macd_record.diff > record_1cycle_before.macd_record.dea)) {
			this.is_dead_cross	= true;
		}
		
		//金叉
		if ((record.macd_record.diff > record.macd_record.dea) &&
				(record_1cycle_before.macd_record.diff < record_1cycle_before.macd_record.dea)) {
			this.is_gold_cross	= true;
		}
		
		//macd红线变短再变长
		if (record.macd_record.macd > 0 &&
				record_2cycle_before.macd_record.macd > record_1cycle_before.macd_record.macd &&
				record.macd_record.macd > record_1cycle_before.macd_record.macd) {
			this.is_macd_up	= true;
		}
		
		//macd顶
		if (record.macd_record.macd > 0 &&
				record_2cycle_before.macd_record.macd < record_1cycle_before.macd_record.macd &&
				record.macd_record.macd < record_1cycle_before.macd_record.macd) {
			this.is_macd_top	= true;
		}
	}
	
	public boolean IsBuy() {
		
//		if (this.is_gold_cross) {
//			this.curt_status	= STATUS.READY;
//			log.info("TransProcess: buy for gold_cross, status from " + STATUS.READY + " to " + STATUS.BUYIN);
//			return true;
//		}
		
		if (this.is_bull) {
			if (this.is_macd_up) {
				this.curt_status	= STATUS.BULL;
				log.info("TransProcess: buy for bull && macd_up, status from " + STATUS.READY + " to " + STATUS.BULL);
				return true;
			}
			
			if (this.is_boll_up) {
				this.curt_status	= STATUS.BULL;
				log.info("TransProcess: buy for bull && boll_up, status from " + STATUS.READY + " to " + STATUS.BULL);
				return true;
			}
		}
		
		return false;
	}
	
	public int IsSell() {
		
		if (this.curt_status == STATUS.BULL) {
			if (this.is_dead_cross) {
				this.curt_status	= STATUS.HALF;
				log.info("TransProcess: sell for dead_cross in bull, status from " + STATUS.BULL + " to " + STATUS.HALF);
				return 5;
			}
		}
		else if (this.curt_status == STATUS.BUYIN) {
			if (this.is_dead_cross) {
				this.curt_status	= STATUS.READY;
				log.info("TransProcess: sell for dead_cross in buy, status from " + STATUS.BUYIN + " to " + STATUS.READY);
				return 10;
			}
			if (this.is_macd_top) {
				this.curt_status	= STATUS.HALF;
				log.info("TransProcess: sell for macd_top in buy, status from " + STATUS.BUYIN + " to " + STATUS.HALF);
				return 5;
			}
		}
		else if (this.curt_status == STATUS.HALF) {
			if (this.is_dead_cross) {
				this.curt_status	= STATUS.READY;
				log.info("TransProcess: sell for dead_cross in half, status from " + STATUS.HALF + " to " + STATUS.READY);
				return 5;
			}
			if (this.is_macd_top) {
				this.curt_status	= STATUS.READY;
				log.info("TransProcess: sell for macd_top in half, status from " + STATUS.HALF + " to " + STATUS.READY);
				return 5;
			}
		}
		
		return 0;
	}
}
