package com.latupa.stock;

public interface BTCTransStrategy {
	
	/**
	 * 是否买入
	 * 先简化，如果需要买，则全部资金买入
	 * @return
	 */
	public boolean IsBuy();
	
	/**
	 * 是否卖出
	 * 如果是卖出则返回1-10，表示卖出的比例；否则返回0
	 * @return
	 */
	public int IsSell();
	
	/**
	 * 入场后check状态是否变化
	 * @param btc_data
	 */
	public void CheckPoint(BTCData btc_data);
	
	public void InitPoint();
}
