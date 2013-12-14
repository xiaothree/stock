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
	
	//BTC数据
	public BTCData btc_data = new BTCData();
	
	//BTC计算公式
	public BTCFunc btc_func = new BTCFunc();
	
	public BTCTransSystem(int cycle_data, int cycle_fetch) {
		this.cycle_data		= cycle_data;
		this.cycle_fetch	= cycle_fetch;
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
		BTCTransSystem btc_ts = new BTCTransSystem(30, 5);
		btc_ts.Route();
	}
}
