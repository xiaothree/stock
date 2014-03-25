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
	
	public TradeRet DoBuy(int invest_position) throws InterruptedException {
		
		int buy_count = 0;//尝试交易次数
		int buy_avail_count = 0;//交易成功次数
		
		//获取账户中的金额
		UserInfo user_info = DoUserInfo();
		if (user_info == null) {
			log.error("get account info failed!");
			return null;
		}
		user_info.Show();
		
		double invest_cny = (invest_position == 10) ? user_info.cny : (user_info.cny * invest_position / 10);
		log.info("invest_cny:" + invest_cny);
		
		TradeRet trade_ret = new TradeRet();
		
		while (true) {
			buy_count++;
			
			log.info("buy for " + buy_count + " times");
			
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
			double buy_quantity	= invest_cny / (buy_price + BTCApi.TRADE_DIFF);
			
			//委托买单
			log.info("buy total cny:" + invest_cny + ", price:" + buy_price + ", quantify:" + buy_quantity + ", buy1:" + ticker.buy + ", sell1:" + ticker.sell);
			String order_id	= DoTrade("buy", buy_price, buy_quantity);
			if (order_id == null) {
				log.error("trade for buy failed!");
				return null;
			}
			log.info("order_id:" + order_id);
			
			//避免委托买单和查单过于频繁
			Thread.sleep(10000);

			//获取委托的结果
			TradeRet trade = DoGetOrder(order_id);
			if (trade == null) {
				log.error("get order result failed!");
				return null;
			}
			else {
				trade.Show();
				
				//有交易成功的部分
				if (trade.deal_amount > 0) {
					buy_avail_count++;
					trade_ret.deal_amount	+= trade.deal_amount;
					trade_ret.avg_price		+= trade.avg_price;
					
					invest_cny -= trade_ret.avg_price * trade_ret.deal_amount;
					log.info("remain cny:" + invest_cny);
					if (invest_cny <= 0) {
						log.info("buy reach invest_cny, finish");
						trade_ret.avg_price /= buy_avail_count;
						return trade_ret;
					}
				}
				
				if (trade.status == TradeRet.STATUS.TOTAL) {
					log.info("order return total, finish");
					trade_ret.avg_price /= buy_avail_count;
					return trade_ret;
				}
				else if (trade.status == TradeRet.STATUS.PARTER) {//如果是部分成交
					log.info("order return parter");
					//如果剩余部分小于最小买入份额，就返回
					if (trade.amount - trade.deal_amount < 0.01) {
						log.info("need buy(" + trade.amount + "), deal(" + trade.deal_amount + "), finish");
						trade_ret.avg_price /= buy_avail_count;
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
			
			//达到5次就结束
			if (buy_count >= 5) {
				if (trade_ret.deal_amount > 0) {//如果之前分批买入有成功的，那么也返回成功
					log.info("has buyed buy_total_quantity:" + trade_ret.deal_amount + ", finish");
					trade_ret.avg_price /= buy_avail_count;
					return trade_ret;
				}
				else {
					log.error("force buy failed!");
					return null;
				}
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
			//避免过于频繁
			Thread.sleep(5000);
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
		
		int sell_count = 0;//尝试交易次数
		int sell_avail_count = 0;//交易成功次数
		TradeRet trade_ret = new TradeRet();
		
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
			TradeRet trade = DoGetOrder(order_id);
			if (trade == null) {
				log.error("get order result failed!");
				return null;
			}
			else {
				trade.Show();
				
				if (trade.deal_amount > 0) {
					sell_avail_count++;
					trade_ret.deal_amount	+= trade.deal_amount;
					trade_ret.avg_price		+= trade.avg_price;
				}
				
				if (trade.status == TradeRet.STATUS.TOTAL) {
					trade_ret.avg_price /= sell_avail_count;
					return trade_ret;
				}
				else if (trade.status == TradeRet.STATUS.PARTER) {//如果是部分成交，则继续卖出剩余的部分
					sell_quantity -= trade.deal_amount;
				}
			}
			
			//撤销委托
			boolean ret = DoCancelOrder(order_id);
			if (ret != true) {
				log.error("cancel trade failed!");
				return null;
			}
		}
		
		return null;
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
