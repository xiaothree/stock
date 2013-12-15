package com.latupa.stock;

import java.util.TreeMap;

public interface BTCTransStrategy {
	
	/**
	 * 是否买入
	 * 先简化，如果需要买，则全部资金买入
	 * @param btc_data
	 * @return
	 */
	public boolean IsBuy(BTCData btc_data);
	
	/**
	 * 是否卖出
	 * 如果是卖出则返回1-10，表示卖出的比例；否则返回0
	 * @param btc_data
	 * @return
	 */
	public int IsSell(BTCData btc_data);
}
