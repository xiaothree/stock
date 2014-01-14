package com.latupa.stock;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BTCUpdateSystem {
	public static final Log log = LogFactory.getLog(BTCUpdateSystem.class);
	
	//每个时间间隔分别对应BTCData<seconds, BTCData>
	public HashMap<Integer, BTCData> data_map = new HashMap<Integer, BTCData>();
	
	//每个时间间隔分别记录K线数<seconds, K线数>
	public HashMap<Integer, Integer> k_num_map = new HashMap<Integer, Integer>();
	
	public BTCFunc btc_func = new BTCFunc();
	
	//数据采集周期(s)
	public int fetch_cycle;
	
	public BTCUpdateSystem(ArrayList<Integer> data_cycle_list, int fetch_cycle) {
		for (int i : data_cycle_list) {
			this.data_map.put(i, new BTCData(i));
			this.k_num_map.put(i, 0);
		}
		
		this.fetch_cycle = fetch_cycle;
	}
	
	public void Route() {
		
		BTCUpdateThread update_thread = new BTCUpdateThread(this);
		update_thread.start();
		
		BTCDataProcThread data_proc_thread = new BTCDataProcThread(this);
		data_proc_thread.start();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ArrayList<Integer> alist = new ArrayList<Integer>();
		alist.add(60);
		alist.add(120);
		alist.add(300);
		alist.add(600);
		
		BTCUpdateSystem btc_us = new BTCUpdateSystem(alist, 20);
		btc_us.Route();
	}

}
