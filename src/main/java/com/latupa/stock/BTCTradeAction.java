package com.latupa.stock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 交易操作
 * @author latupa
 *
 * TODO
 * 1. 部分成交的处理方式，取决于部分成交是否会成为委托的最终状态，暂不处理
 */

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
	public UserInfo DoUserInfo() throws InterruptedException {
		int count = 0;
		UserInfo user_info	= btc_api.ApiUserInfo();
		while (user_info == null) {
			count++;
			if (count == 5) {
				break;
			}
			Thread.sleep(5000);
			user_info = btc_api.ApiUserInfo();
		}
		return user_info;
	}
	
	/**
	 * 获取当前行情
	 * @return
	 * @throws InterruptedException 
	 */
	public Ticker DoTicker() throws InterruptedException {
		int count = 0;
		Ticker ticker = btc_api.ApiTicker();
		while (ticker == null) {
			count++;
			if (count == 5) {
				break;
			}
			Thread.sleep(5000);
			ticker = btc_api.ApiTicker();
		}
		return ticker;
	}
	
	/**
	 * 撤销委托
	 * @param order_id
	 * @return
	 * @throws InterruptedException 
	 */
	public boolean DoCancelOrder(String order_id) throws InterruptedException {
		int count = 0;
		boolean ret	= btc_api.ApiCancelOrder(order_id);
		while (ret != true) {
			count++;
			if (count == 5) {
				break;
			}
			Thread.sleep(5000);
			ret	= btc_api.ApiCancelOrder(order_id);
		}
		
		return ret;
	}
	
	/**
	 * 委托买卖
	 * @param type
	 * @param price
	 * @param quantity
	 * @return
	 * @throws InterruptedException
	 */
	public String DoTrade(String type, double price, double quantity) throws InterruptedException {
		int count = 0;
		String order_id = btc_api.ApiTrade(type, price, quantity);
		while (order_id == null) {
			count++;
			if (count == 5) {
				break;
			}
			
			Thread.sleep(5000);
			order_id = btc_api.ApiTrade(type, price, quantity);
		}
		return order_id;
	}
	
	/**
	 * 获取订单状态
	 * @param order_id
	 * @return
	 * @throws InterruptedException
	 */
	public TradeRet DoGetOrder(String order_id) throws InterruptedException {
		int count = 0;
		TradeRet trade_ret = btc_api.ApiGetOrder(order_id);
		while (trade_ret == null || 
				(trade_ret != null && trade_ret.status != TradeRet.STATUS.TOTAL)) {
			count++;
			if (count == 5) {
				break;
			}
			
			Thread.sleep(10000);
			trade_ret = btc_api.ApiGetOrder(order_id);
		}
		
		return trade_ret;
	}
	
	public TradeRet DoBuy() throws InterruptedException {
		
		int buy_count	= 0;
		double buy_total_quantity = 0;   //用于记录分批买入汇总的值
		
		while (true) {
			buy_count++;
			if (buy_count > 5) {
				log.error("force buy failed!");
				return null;
			}
			
			log.info("buy for " + buy_count + " times");
			
			//获取账户中的金额
			UserInfo user_info = DoUserInfo();
			if (user_info == null) {
				log.error("get account info failed!");
				return null;
			}
			user_info.Show();
			double cny	= user_info.cny;
			
			//获取当前买一价
			Ticker ticker = DoTicker();
			if (ticker == null) {
				log.error("get curt price failed!");
				return null;
			}
			ticker.Show();
			
			//计算委托价格
			double buy_price	= (ticker.buy + ticker.sell) / 2;
//			double buy_price	= (ticker.buy + ticker.sell) / 2 + BTCApi.TRADE_DIFF;
			//double buy_price	= ticker.buy + BTCApi.TRADE_DIFF;
			double buy_quantity	= cny / (buy_price + BTCApi.TRADE_DIFF);
			
			//委托买单
			log.info("buy total cny:" + cny + ", price:" + buy_price + ", quantify:" + buy_quantity + ", buy1:" + ticker.buy + ", sell1:" + ticker.sell);
			String order_id	= DoTrade("buy", buy_price, buy_quantity);
			if (order_id == null) {
				log.error("trade for buy failed!");
				return null;
			}
			log.info("order_id:" + order_id);
			
			//避免委托买单和查单过于频繁
			Thread.sleep(10000);

			//获取委托的结果
			TradeRet trade_ret = DoGetOrder(order_id);
			if (trade_ret == null) {
				log.error("get order result failed!");
				return null;
			}
			else {
				trade_ret.Show();
				
				buy_total_quantity += trade_ret.deal_amount;
				
				if (trade_ret.status == TradeRet.STATUS.TOTAL) {
					trade_ret.deal_amount = buy_total_quantity;
					return trade_ret;
				}
				else if (trade_ret.status == TradeRet.STATUS.PARTER) {//如果是部分成交，则继续卖出剩余的部分
					//如果剩余部分小于最小买入份额，就返回
					if (trade_ret.amount - trade_ret.deal_amount < 0.01) {
						trade_ret.deal_amount = buy_total_quantity;
						return trade_ret;
					}
				}
				else if (trade_ret.status == TradeRet.STATUS.WAIT) { //如果之前分批买入有成功的，那么也返回成功
					if (buy_total_quantity > 0) {
						trade_ret.deal_amount = buy_total_quantity;
						return trade_ret;
					}
				}
			}
			
			//撤销委托
			boolean ret = DoCancelOrder(order_id);
			if (ret != true) {
				log.error("cancel trade failed!");
				return null;
			}
		}
	}
	
	/**
	 * 卖出操作
	 * @param position 仓位比例
	 * @return
	 * @throws InterruptedException
	 */
	public TradeRet DoSell(int position) throws InterruptedException {
		
		//获取账户中的数量
		UserInfo user_info = DoUserInfo();
		if (user_info == null) {
			log.error("get account info failed!");
			return null;
		}
		user_info.Show();
		
		//如果没有持有中的数量，则直接返回
		if (user_info.btc == 0) {
			log.info("no quantity to sell");
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
		while (sell_quantity > 0) {
			
			sell_count++;
			log.info("sell for " + sell_count + " times");
			
			//获取当前卖一价
			Ticker ticker = DoTicker();
			if (ticker == null) {
				log.error("get curt price failed!");
				return null;
			}
			ticker.Show();
			
			//计算卖出委托价
			double sell_price = (ticker.sell + ticker.buy) / 2;
//			double sell_price = (ticker.sell + ticker.buy) / 2 - BTCApi.TRADE_DIFF * sell_count;
			//double sell_price	= ticker.sell - BTCApi.TRADE_DIFF * sell_count;
			
			//委托卖单
			log.info("sell price:" + sell_price + ", quantity:" + sell_quantity + ", buy1:" + ticker.buy + ", sell1:" + ticker.sell);
			String order_id	= DoTrade("sell", sell_price, sell_quantity);
			if (order_id == null) {
				log.error("trade for sell failed!");
				return null;
			}
			log.info("order_id:" + order_id);
			
			//避免委托卖单和查单过于频繁
			Thread.sleep(10000);
			
			//获取委托的结果
			TradeRet trade_ret = DoGetOrder(order_id);
			if (trade_ret == null) {
				log.error("get order result failed!");
				return null;
			}
			else {
				trade_ret.Show();
				if (trade_ret.status == TradeRet.STATUS.TOTAL) {
					return trade_ret;
				}
				else if (trade_ret.status == TradeRet.STATUS.PARTER) {//如果是部分成交，则继续卖出剩余的部分
					sell_quantity -= trade_ret.deal_amount;
				}
			}
			
			//撤销委托
			boolean ret = DoCancelOrder(order_id);
			if (ret != true) {
				log.error("cancel trade failed!");
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
