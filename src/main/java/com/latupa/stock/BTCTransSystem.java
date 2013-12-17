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
	
	//K线数
	public int btc_k_cycles = 0;
	
	//BTC数据
	public BTCData btc_data = new BTCData();
	
	//BTC计算公式
	public BTCFunc btc_func = new BTCFunc();
	
	//交易策略
	public BTCTransStrategy1 btc_trans_stra = new BTCTransStrategy1();
	
	public BTCTransSystem(int cycle_data, int cycle_fetch) {
		this.cycle_data		= cycle_data;
		this.cycle_fetch	= cycle_fetch;
		this.btc_curt_quantity	= 0;
		this.btc_curt_position	= 0;
	}
	
	public void Route() {
		
		BTCUpdateThread btc_update_thread = new BTCUpdateThread(this);
		btc_update_thread.start();
		
		BTCProcThread btc_proc_thread = new BTCProcThread(this);
		btc_proc_thread.start();
	}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		BTCTransSystem btc_ts = new BTCTransSystem(5, 1);
		btc_ts.Route();
	}
}
