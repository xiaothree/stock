package com.latupa.stock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BTCTradeAction {
	
	public static final Log log = LogFactory.getLog(BTCTradeAction.class);
	
	BTCApi btc_api;
	
	public BTCTradeAction() {
		this.btc_api	= new BTCApi();
	}
	
	/**
	 * 获取账户信息
	 * @return
	 * @throws InterruptedException
	 */
	public UserInfo GetUserInfo() throws InterruptedException {
		UserInfo user_info	= btc_api.ApiUserInfo();
		int count = 0;
		while (user_info == null) {
			Thread.sleep(5000);
			count++;
			if (count == 10) {
				break;
			}
			user_info = btc_api.ApiUserInfo();
		}
		return user_info;
	}
	
	public TradeRet DoBuy() throws InterruptedException {
		//获取账户中的金额
		UserInfo user_info	= null;
		while (user_info == null) {
			user_info	= btc_api.ApiUserInfo();
			user_info.Show();
			Thread.sleep(5000);
		}
		double cny	= user_info.cny;
		
		//获取当前买一价
		Ticker ticker	= null;
		while (ticker == null) {
			ticker	= btc_api.ApiTicker();
			ticker.Show();
			Thread.sleep(1000);
		}
		double buy_price	= ticker.buy + BTCApi.TRADE_DIFF;
		double buy_quantity	= cny / (buy_price + BTCApi.TRADE_DIFF);
		
		//委托买单
		log.info("buy total cny:" + cny + ", price:" + buy_price + ", amount:" + buy_quantity);
		String order_id	= btc_api.ApiTrade("buy", buy_price, buy_quantity);
		log.info("order_id:" + order_id);
		
		//获取委托的结果
		int count = 0;
		while (true) {
			count++;
			TradeRet trade_ret	= btc_api.ApiGetOrder(order_id);
			trade_ret.Show();
			if (trade_ret.status == TradeRet.STATUS.TOTAL) {
				return trade_ret;
			}
			Thread.sleep(5000);
			
			if (count == 10) {
				break;
			}
		}
		
		//撤销委托
		boolean ret	= btc_api.ApiCancelOrder(order_id);
		while (ret != true) {
			Thread.sleep(5000);
			ret	= btc_api.ApiCancelOrder(order_id);
		}
		
		return null;
	}
	
	/**
	 * 卖出操作
	 * @param position 仓位比例
	 * @return
	 * @throws InterruptedException
	 */
	public TradeRet DoSell(int position) throws InterruptedException {
		
		//获取账户中的数量
		UserInfo user_info	= null;
		while (user_info == null) {
			user_info	= btc_api.ApiUserInfo();
			user_info.Show();
			Thread.sleep(5000);
		}
		
		//如果没有持有中的数量，则直接返回
		if (user_info.btc == 0) {
			return new TradeRet();
		}
		
		//计算卖出数量
		double sell_quantity;
		if (position == 10) {
			sell_quantity	= user_info.btc;
		}
		else {
			sell_quantity	= user_info.btc * position / 10;
		}
		
		int sell_count = 0;
		
		while (true) {
			
			sell_count++;
			
			//获取当前卖一价
			Ticker ticker	= null;
			while (ticker == null) {
				ticker	= btc_api.ApiTicker();
				ticker.Show();
				Thread.sleep(1000);
			}
			double sell_price	= ticker.sell - BTCApi.TRADE_DIFF * sell_count;
			
			//委托卖单
			log.info("sell price:" + sell_price + ", amount:" + sell_quantity);
			String order_id	= btc_api.ApiTrade("sell", sell_price, sell_quantity);
			log.info("order_id:" + order_id);
			
			//获取委托的结果
			int count = 0;
			while (true) {
				count++;
				TradeRet trade_ret	= btc_api.ApiGetOrder(order_id);
				trade_ret.Show();
				if (trade_ret.status == TradeRet.STATUS.TOTAL) {
					return trade_ret;
				}
				else if (trade_ret.status == TradeRet.STATUS.PARTER) {//如果是部分成交，则继续卖出剩余的部分
					sell_quantity -= trade_ret.deal_amount;
				}
				Thread.sleep(5000);
				
				if (count == 5) {
					break;
				}
			}
			
			//撤销委托
			boolean ret	= btc_api.ApiCancelOrder(order_id);
			while (ret != true) {
				Thread.sleep(5000);
				ret	= btc_api.ApiCancelOrder(order_id);
			}
			
			if (sell_count == 5) {
				log.error("force sell failed!");
				return null;
			}
		}
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		BTCTradeAction btc_ta = new BTCTradeAction();
		try {
			btc_ta.DoSell(10);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
