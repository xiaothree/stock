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
	
	//死叉次数
	public int dead_cross = 0;
	
	public BTCTransStrategy1() {
		this.Init();
	}
	
	public void Init() {
		this.curt_status	= STATUS.READY;
		this.dead_cross		= 0;
	}
	
	public void CheckStatus(BTCData btc_data) {
		BTCTotalRecord record	= btc_data.BTCRecordOptGetByCycle(0);
		
		//是否多头且贴上轨
		if ((record.ma_record.ma5 > record.ma_record.ma10 && 
				record.ma_record.ma10 > record.ma_record.ma20 &&
				record.ma_record.ma20 > record.ma_record.ma30 &&
				record.ma_record.ma30 > record.ma_record.ma60) &&
				(record.close > record.boll_record.upper &&
						record.close > record.open)) {
			if (this.curt_status == STATUS.BUYIN) {
				this.curt_status = STATUS.BULL;
			}
		}
	}
	
	public boolean IsBuy(BTCData btc_data) {
		
		BTCTotalRecord record				= btc_data.BTCRecordOptGetByCycle(0);
		BTCTotalRecord record_1cycle_before	= btc_data.BTCRecordOptGetByCycle(1);
		BTCTotalRecord record_2cycle_before	= btc_data.BTCRecordOptGetByCycle(2);
		
		//MACD金叉
//		if ((record.macd_record.diff > record.macd_record.dea) &&
//				(record_1cycle_before.macd_record.diff < record_1cycle_before.macd_record.dea)) {
//			log.info("buy1, " + "price:" + record.close);
//			return true;
//		}
		
		//多头
		if (record.ma_record.ma5 > record.ma_record.ma10 && 
				record.ma_record.ma10 > record.ma_record.ma20 &&
				record.ma_record.ma20 > record.ma_record.ma30 &&
				record.ma_record.ma30 > record.ma_record.ma60) {
			
			//macd红线变短再变长
			if (record.macd_record.macd > 0 &&
					record_2cycle_before.macd_record.macd > record_1cycle_before.macd_record.macd &&
					record.macd_record.macd > record_1cycle_before.macd_record.macd) {
				log.info("buy2, " + "price:" + record.close);
				return true;
			}
			//boll贴上轨
			else if (record.close > record.boll_record.upper &&
						record.close > record.open) {
				log.info("buy3, " + "price:" + record.close);
				return true;
			}
		}
				
		return false;
	}
	
	public int IsSell(BTCData btc_data) {
		
		BTCTotalRecord record				= btc_data.BTCRecordOptGetByCycle(0);
		BTCTotalRecord record_1cycle_before	= btc_data.BTCRecordOptGetByCycle(1);
		BTCTotalRecord record_2cycle_before	= btc_data.BTCRecordOptGetByCycle(2);
		
		//MACD死叉
		if ((record.macd_record.diff < record.macd_record.dea) &&
				(record_1cycle_before.macd_record.diff > record_1cycle_before.macd_record.dea)) {
			this.dead_cross++;
		}
		
		if (this.curt_status == STATUS.BULL) {
			if (this.dead_cross == 1) {
				log.info("sell1 position:" + 5 + ", price:" + record.close);
				return 5;
			}
			else if (this.dead_cross == 2) {
				log.info("sell2 position:" + 5 + ", price:" + record.close);
				return 5;
			}
		}
		else if (this.curt_status == STATUS.BUYIN) {
			if (this.dead_cross > 0) {
				log.info("sell3 position:" + 10 + ", price:" + record.close);
				return 5;
			}
		}
		else if (this.curt_status == STATUS.HALF) {
			if (this.dead_cross > 0) {
				log.info("sell4 position:" + 5 + ", price:" + record.close);
				return 10;
			}
		}
		
		return 0;
	}
}
