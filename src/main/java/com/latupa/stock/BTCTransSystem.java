package com.latupa.stock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * BTC交易系统
 * @author latupa
 *
 */
public class BTCTransSystem {
	
	public static final Log log = LogFactory.getLog(BTCTransSystem.class);
	
	//K线周期(s)
	public int cycle_data;	
	
	//数据采集周期(s)
	public int cycle_fetch;
	
	//当前BTC份数
	public double btc_curt_quantity;
	
	//当前仓位(1-10)成
	public int btc_curt_position;
	 
	//初始资金
	public final double BTC_INIT_AMOUNT = 10000;
	
	public double btc_curt_amount;
	
	public double btc_buy_price;
	public double btc_profit;
	public double btc_accumulate_profit;
	
	public int btc_trans_succ_count;
	public int btc_trans_count;
	
	public final double BTC_FEE	= 0.003;
	public double btc_fee_cost;
	public double btc_accumulate_fee_cost;
	
	public String btc_time_buyin;
	
	//记录每年的受益
	public String btc_year_time;
	public double btc_year_amount_init;
	public double btc_year_price_init;
	
	//K线数
	public int btc_k_cycles = 0;
	
	//BTC数据
	public BTCData btc_data = new BTCData();
	
	//BTC计算公式
	public BTCFunc btc_func = new BTCFunc();
	
	//交易策略
	public BTCTransStrategy2 btc_trans_stra = new BTCTransStrategy2();
	
	public BTCTransSystem(int cycle_data, int cycle_fetch) {
		this.cycle_data		= cycle_data;
		this.cycle_fetch	= cycle_fetch;
		
		this.btc_curt_quantity	= 0;
		this.btc_curt_position	= 0;
		
		this.btc_curt_amount	= this.BTC_INIT_AMOUNT;
		this.btc_profit			= 0;
		this.btc_accumulate_profit	= 0;
		this.btc_trans_succ_count	= 0;
		this.btc_trans_count	= 0;
		this.btc_fee_cost		= 0;
		this.btc_accumulate_fee_cost	= 0;
		
		this.btc_year_time	= null;
		this.btc_year_amount_init	= this.BTC_INIT_AMOUNT;
		this.btc_year_price_init	= 0;
	}
	
	public void Route() {
		
		//如果从数据库mock信息，则把update thread注释掉，同时proc thread的第二个参数为1
//		BTCUpdateThread btc_update_thread = new BTCUpdateThread(this);
//		btc_update_thread.start();
		
		
		BTCProcThread btc_proc_thread = new BTCProcThread(this, 1);
		btc_proc_thread.start();
	}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		BTCTransSystem btc_ts = new BTCTransSystem(300, 10);
		btc_ts.Route();
	}
}
