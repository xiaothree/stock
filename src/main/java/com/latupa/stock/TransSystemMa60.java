package com.latupa.stock;

import java.text.ParseException;

public class TransSystemMa60 extends TransSystem {

	public TransSystemMa60(String market, String date_s, String date_e) {
		// TODO Auto-generated constructor stub
		super(market, date_s, date_e);
	}
	
	/**
	 * 准备状态股票的处理函数
	 */
	public void StockStatusReadyProc() {
		System.out.println("ma60 ready proc");
	}
	
	/**
	 * 买入状态股票的处理函数
	 */
	public void StockStatusBuyProc() {
		System.out.println("ma60 buy proc");
	}
	
	/**
	 * 盘活状态股票的处理函数
	 */
	public void StockStatusFreeupProc() {
		System.out.println("ma60 freeup proc");
	}
	
	/**
	 * 多头状态股票的处理函数
	 */
	public void StockStatusBullsProc() {
		System.out.println("ma60 bulls proc");
	}
	
	/**
	 * 卖出状态股票的处理函数
	 */
	public void StockStatusSellProc() {
		System.out.println("ma60 sell proc");
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		TransSystem ts = new TransSystemMa60("sh", "20100101", "20100301");
		ts.LoadTransDays();
		try {
			ts.DailyRoute();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
