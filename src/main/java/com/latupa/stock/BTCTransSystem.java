package com.latupa.stock;

import java.io.File;
import java.text.DecimalFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * BTC交易系统
 * @author latupa
 *
 * TODO
 * 1. 切换为真实交易的时候，还需要初始化买卖状态，即BTCTransStrategy
 */
public class BTCTransSystem {
	
	public static final Log log = LogFactory.getLog(BTCTransSystem.class);
	
	//交易模式标识文件
	public static final String Trans_FILE_DIR = "src/main/resources/";
	public static final String Trans_FILE = "trans.flag";
	
	//运行模式
	public enum MODE {
		REVIEW,	//复盘
		ACTUAL;	//实盘
	};
	public MODE mode;
	
	//交易模式：true->真实交易；false->模拟交易
	public boolean trade_mode;
	
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
	
	public double btc_init_amount;
	public double btc_curt_amount;
	
	public double btc_buy_price;
	public double btc_sell_price;
	public double btc_profit;
	public double btc_accumulate_profit;
	
	public int btc_trans_succ_count;
	public int btc_trans_count;
	
	public final double BTC_FEE	= 0.003;
	public double btc_fee_cost;
	public double btc_accumulate_fee_cost;
	
	public String btc_time_buyin;
	
	//记录每年的收益
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
	
	//底层api
	public BTCApi btc_api = new BTCApi();
	
	//交易接口
	public BTCTradeAction btc_trade_action = new BTCTradeAction();
	
	//交易记录
	public BTCTransRecord btc_trans_rec = new BTCTransRecord(this.btc_data.dbInst);
	
	public BTCTransSystem(int cycle_data, int cycle_fetch, MODE mode) {
		this.cycle_data		= cycle_data;
		this.cycle_fetch	= cycle_fetch;
		this.mode			= mode;
		
		this.trade_mode	= false;
		this.btc_init_amount	= this.BTC_INIT_AMOUNT;
		this.btc_curt_amount	= this.BTC_INIT_AMOUNT;
		
		this.btc_trans_rec.InitTable();
		
		ContextInit();
	}
	
	public void ContextInit() {
		
		this.btc_curt_quantity	= 0;
		this.btc_curt_position	= 0;
		
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
	
	/**
	 * 执行卖出操作
	 * @param sDateTime 当前时间
	 * @param price 当前价格
	 * @param sell_position 卖出比例
	 */
	public void SellAction(String sDateTime, double price, int sell_position) {
		
		this.btc_curt_position *= (1 - (double)sell_position / 10);
		
		double sell_quantity = 0;
		
		if (this.mode == MODE.ACTUAL && this.trade_mode == true) {//实盘（真实交易）
			try {
				TradeRet trade_ret = this.btc_trade_action.DoSell(sell_position);
				//卖出成功
				if (trade_ret != null) {
					sell_quantity	= trade_ret.deal_amount;
					this.btc_curt_quantity	-= sell_quantity;
					this.btc_sell_price	= trade_ret.avg_price;
					this.btc_profit	+= ((this.btc_sell_price - this.btc_buy_price) * sell_quantity); 
					this.btc_fee_cost += (this.btc_sell_price * sell_quantity * this.BTC_FEE);
				}
				else {
					log.error("sell action failed!");
					System.exit(0);
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				log.error("buy action failed!", e);
				System.exit(0);
			}
		}
		else {//复盘||实盘（模拟交易）
			sell_quantity = this.btc_curt_quantity * (double)sell_position / 10;
			this.btc_curt_quantity	-= sell_quantity;
			this.btc_sell_price	= price;
			this.btc_profit	+= ((this.btc_sell_price - this.btc_buy_price) * sell_quantity); 
			this.btc_fee_cost += (this.btc_sell_price * sell_quantity * this.BTC_FEE);
		}
		
		this.btc_trans_rec.InsertTrans(sDateTime, 
				BTCTransRecord.OPT.OPT_SELL, 
				sell_quantity, 
				this.btc_sell_price);
		
		DecimalFormat df1 = new DecimalFormat("#0.00");
		log.info("TransProcess[SELL]: quantity:" + df1.format(sell_quantity) +
				", price:" + df1.format(this.btc_sell_price) +
				", amount:" + df1.format(sell_quantity * this.btc_sell_price) +
				", curt position:" + this.btc_curt_position);
		
		if (this.btc_curt_position == 0) {//如果清仓
			
			//更新交易次数
			if (this.btc_profit > 0) {
				this.btc_trans_succ_count++;
			}
			this.btc_trans_count++;
			
			double last_curt_amount = this.btc_curt_amount;
			
			//更新总金额
			if (this.mode == MODE.ACTUAL && this.trade_mode == true) {//实盘（真实交易）
				try {
					UserInfo user_info = this.btc_trade_action.GetUserInfo();
					if (user_info != null) {
						this.btc_curt_amount = user_info.cny;
						this.btc_accumulate_profit = this.btc_curt_amount - this.btc_init_amount;
					}
				} catch (InterruptedException e) {
					log.error("get user info failed!");
					System.exit(0);
				}
			}
			else {
				this.btc_curt_amount += this.btc_profit;
				this.btc_accumulate_profit	+= this.btc_profit;
			}
			
			this.btc_accumulate_fee_cost += this.btc_fee_cost;
		
			log.info("TransProcess[SUMMARY]: " + "time:" + this.btc_time_buyin + "-" + sDateTime + 
					", btc_price:" + this.btc_buy_price + "-" + this.btc_sell_price + "(" + df1.format((this.btc_sell_price - this.btc_buy_price) / this.btc_buy_price * 100) + "%)" + 
					", amount:" + df1.format(this.btc_curt_amount) +
					", profit:" + df1.format(this.btc_profit) + "(" + df1.format(this.btc_profit / last_curt_amount * 100) + "%)" + 
					", accu profit:" + df1.format(this.btc_accumulate_profit) + "(" + df1.format(this.btc_accumulate_profit / this.btc_init_amount * 100) + "%)" +
					", fee cost:" + df1.format(this.btc_fee_cost) + "(" + df1.format(this.btc_fee_cost / last_curt_amount * 100) + "%)" + 
					", accu fee cost:" + df1.format(this.btc_accumulate_fee_cost) + "(" + df1.format(this.btc_accumulate_fee_cost / this.btc_init_amount * 100) + "%)" +
					", accu times:" + this.btc_trans_count + ", accu succ times:" + this.btc_trans_succ_count + "(" + df1.format((double)this.btc_trans_succ_count / (double)this.btc_trans_count * 100) + "%)");
			
			this.btc_profit		= 0;
			this.btc_fee_cost	= 0;
		}	
	}
	
	/**
	 * 执行买入操作
	 * @param sDatetime 当前时间
	 * @param price 当前价格
	 */
	public void BuyAction(String sDateTime, double price) {
		
		this.btc_time_buyin	= sDateTime;
		
		if (this.mode == MODE.ACTUAL && this.trade_mode == true) {//实盘（真实交易）
			try {
				TradeRet trade_ret = this.btc_trade_action.DoBuy();
				//买入成功
				if (trade_ret != null) {
					this.btc_curt_position = 10;
					this.btc_curt_quantity	= trade_ret.deal_amount;
					this.btc_buy_price	= trade_ret.avg_price;
					this.btc_fee_cost += (this.btc_curt_quantity * this.btc_buy_price * this.BTC_FEE);
				}
				else {
					log.error("buy action failed!");
					System.exit(0);
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				log.error("buy action failed!", e);
				System.exit(0);
			}
		}
		else {//复盘||实盘（模拟交易）
			this.btc_curt_position = 10;
			this.btc_curt_quantity = this.btc_curt_amount / price;
			this.btc_buy_price	= price;
			this.btc_fee_cost += (this.btc_curt_amount * this.BTC_FEE);
		}
		
		this.btc_trans_rec.InsertTrans(sDateTime, 
				BTCTransRecord.OPT.OPT_BUY, 
				this.btc_curt_quantity, 
				this.btc_buy_price);
		
		DecimalFormat df1 = new DecimalFormat("#0.00");
		log.info("TransProcess[BUY]: quantity:" + df1.format(this.btc_curt_quantity) +
				", price:" + df1.format(this.btc_buy_price) +
				", amount:" + df1.format(this.btc_curt_quantity * this.btc_buy_price) +
				", curt position:" + this.btc_curt_position);
		
		return;
	}
	
	/**
	 * 交易模式切换
	 * 注意：先清空仓位，再进入真实交易模式，否则数据统计会有问题
	 * @return
	 * @throws InterruptedException 
	 */
	public void TradeModeSwitch() throws InterruptedException {
		
		//只有实盘才涉及是否进行真实交易
		if (this.mode == MODE.ACTUAL) {
			
			if (new File(Trans_FILE_DIR + Trans_FILE).exists()) {
				//进入真实交易模式
				if (this.trade_mode == true) {//已经是真实交易
					return;
				}
				else {//转成真实交易
					log.info("start change to real trade mode ...");
					
					//获取当前资金
					UserInfo user_info	= this.btc_api.ApiUserInfo();
					if (user_info != null) {
						this.btc_init_amount	= user_info.cny;
						this.btc_curt_amount	= user_info.cny;
					}
					else {
						log.info("change failed! get curt real amount failed");
						return;
					}
					
					log.info("get curt real amount: " + this.btc_curt_amount);
					
					this.trade_mode	= true;
					
					ContextInit();
					
					log.info("change success");
				}
			}
			else {//模拟交易
				
				if (this.trade_mode == false) {//已经是模拟交易
					return;
				}
				else {//转成模拟交易
					log.info("change to virtual trade mode");
					
					//卖出当前所有仓位
					TradeRet trade_ret = this.btc_trade_action.DoSell(10);
					if (trade_ret != null) {
						//获取当前资金
						UserInfo user_info	= this.btc_api.ApiUserInfo();
						if (user_info != null) {
							this.btc_init_amount	= user_info.cny;
							this.btc_curt_amount	= user_info.cny;
						}
						else {
							log.info("get curt real amount failed");
							return;
						}
						
						log.info("get curt real amount: " + this.btc_curt_amount);
						
						this.trade_mode	= false;
						
						ContextInit();
						
						log.info("change success");
					}
					else {// 卖出失败
						log.error("change failed! sell btc failed");
						System.exit(0);
					}
				}
			}
		}
	}
	
	public void Route() {
		
		//实盘
		if (this.mode == MODE.ACTUAL) {
			BTCUpdateThread btc_update_thread = new BTCUpdateThread(this);
			btc_update_thread.start();
		}
		
		BTCProcThread btc_proc_thread = new BTCProcThread(this);
		btc_proc_thread.start();
	}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		BTCTransSystem btc_ts = new BTCTransSystem(300, 20, MODE.ACTUAL);
		btc_ts.Route();
	}
}
