package com.latupa.stock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

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
	
	//交易模式表文件
	public static final String BTC_TRANS_MODE_TABLE = "trans_mode";
	
	//运行模式
	public enum MODE {
		REVIEW,	//复盘
		ACTUAL;	//实盘
	};
	public MODE mode;
	public String review_time_s; //复盘的起始时间
	public String review_time_e; //复盘的结束时间
	
	public enum OPT {
		BUY,
		SELL;
	};
	
	//交易模式：true->真实交易；false->模拟交易
	public boolean trade_mode;
	
	//K线周期(s)
	public int data_cycle;	
	
	//当前BTC份数
	public double btc_curt_quantity;
	
	//当前仓位(1-10)成
	public int btc_curt_position;
	
	//投入仓位，投入资金计算方式为账户资金*btc_invest_position/10
	public int btc_invest_position = 10;
	 
	//初始资金
	public final double BTC_INIT_AMOUNT = 10000;
	
	public double btc_curt_amount;
	
	public double btc_buy_price;
	public int btc_buy_reason;
	public double btc_sell_price;
	public double btc_profit;
	public double btc_accumulate_profit;
	
	public int btc_trans_succ_count;
	public int btc_trans_count;
	
	public final double BTC_FEE	= 0.003;
	public double btc_fee_cost;
	public double btc_accumulate_fee_cost;
	
	public String btc_time_buyin;
	
	//记录初始信息
	public String btc_time_init;
	public double btc_amount_init;
	public double btc_price_init;
	
	//记录每天的收益
	public String btc_day_time_init;
	public double btc_day_amount_init;
	public double btc_day_price_init;
	
	//记录每月的收益
	public String btc_month_time_init;
	public double btc_month_amount_init;
	public double btc_month_price_init;
	
	//记录每年的收益
	public String btc_year_time_init;
	public double btc_year_amount_init;
	public double btc_year_price_init;
	
	//K线数
	public int btc_k_cycles = 0;
	
	//BTC数据
	public BTCData btc_data;
	
	//BTC计算公式
	public BTCFunc btc_func = new BTCFunc();
	
	//交易策略
	public BTCTransStrategy3 btc_trans_stra = new BTCTransStrategy3();
	
	//底层api
	public BTCApi btc_api = new BTCApi();
	
	//交易接口
	public BTCTradeAction btc_trade_action = new BTCTradeAction();
	
	//交易记录
	public BTCTransRecord btc_trans_rec;
	
	public String btc_trans_postfix;
	
	public BTCTransSystem(int data_cycle, MODE mode, String context, String time_s, String time_e) {
		this.data_cycle	= data_cycle;
		this.mode		= mode;
		this.review_time_s	= time_s;
		this.review_time_e	= time_e;
		this.btc_data	= new BTCData(this.data_cycle);
		this.btc_trans_rec	= new BTCTransRecord(this.btc_data.dbInst);
		
		this.trade_mode	= false;
		this.btc_amount_init	= this.BTC_INIT_AMOUNT;
		this.btc_curt_amount	= this.BTC_INIT_AMOUNT;
		
		if (this.mode == MODE.REVIEW) {
			this.btc_trans_postfix = context;
		}
		else {
			this.btc_trans_postfix = "virtual";
		}
		this.btc_trans_rec.InitTable(this.btc_trans_postfix);
		
		ContextInit();
		InitTransMode();
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
		
		this.btc_day_time_init		= null;
		this.btc_day_amount_init 	= this.BTC_INIT_AMOUNT;
		this.btc_day_price_init		= 0;
		
		this.btc_month_time_init 			= null;
		this.btc_month_amount_init 	= this.BTC_INIT_AMOUNT;
		this.btc_month_price_init		= 0;
		
		this.btc_year_time_init	= null;
		this.btc_year_amount_init	= this.BTC_INIT_AMOUNT;
		this.btc_year_price_init	= 0;
	}
	
	/**
	 * 初始化交易模式表
	 */
	public void InitTransMode() {
		String sql = "create table if not exists " + BTC_TRANS_MODE_TABLE + 
				"(`status` int NOT NULL default '0', " +			//0--模拟交易  1--真实交易，后面字段暂时只针对真实交易有效
				"`start` varchar(6) NOT NULL default '', " +		//仓位控制起始时间
				"`end` varchar(6) NOT NULL default '', " +			//仓位控制结束时间
				"`position` int NOT NULL default '0'" +				//仓位控制比例，1-10，10表示满仓
				") ENGINE=InnoDB DEFAULT CHARSET=utf8";	
			
		this.btc_data.dbInst.updateSQL(sql);
	}
	
	/**
	 * 判断是否是真实交易
	 * @return true--真实交易   false--模拟交易
	 */
	public boolean CheckTransMode(String sDateTime) {
		ResultSet rs = null;
		
		String sql	= "select status as status, start as start, end as end, position as position from " + BTC_TRANS_MODE_TABLE;

		rs = this.btc_data.dbInst.selectSQL(sql);
		try {
			if (rs.next()) {
				int status		= rs.getInt("status");
				String start	= rs.getString("start");
				String end		= rs.getString("end");
				
				if (status == 1) {
					if (sDateTime.substring(8, 14).compareTo(start) >= 0 &&
						sDateTime.substring(8, 14).compareTo(end) <= 0) {
						this.btc_invest_position = rs.getInt("position");
					}
					else {
						this.btc_invest_position = 10;
					}
					
					log.info("invest_position:" + this.btc_invest_position);
					
					return true;
				}
				else {
					return false;
				}
			}
			
			rs.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return false;
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
				ArrayList<TradeRet> tr_list = this.btc_trade_action.DoSell(sell_position);
				
				//卖出成功
				if (tr_list != null && tr_list.size() > 0) {
					
					double sum_price = 0;
					for (TradeRet tr : tr_list) {
						sell_quantity	+= tr.deal_amount;
						sum_price		+= tr.avg_price;
						this.btc_profit	+= (tr.avg_price - this.btc_buy_price) * tr.deal_amount;
						this.btc_fee_cost	+= (tr.avg_price * tr.deal_amount * this.BTC_FEE);
					}
					
					this.btc_curt_quantity	-= sell_quantity;
					this.btc_sell_price	= sum_price / tr_list.size();
					
					log.info("profit up to now from this buy in is :" + this.btc_profit);
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
		
		this.btc_trans_rec.InsertTransDetail(this.btc_trans_postfix,
				sDateTime, 
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
					UserInfo user_info = this.btc_trade_action.DoUserInfo();
					if (user_info != null) {
						user_info.Show();
						this.btc_curt_amount = user_info.cny + user_info.cny_freezed;
						this.btc_profit = this.btc_curt_amount - last_curt_amount;
						this.btc_accumulate_profit = this.btc_curt_amount - this.btc_amount_init;
					}
					else {
						log.error("get cnt failed");
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
			
			this.btc_trans_rec.InsertTrans(this.btc_trans_postfix,
					sDateTime, 
					last_curt_amount, 
					this.btc_curt_amount, 
					this.btc_profit,
					this.btc_buy_reason,
					this.btc_buy_price,
					this.btc_sell_price,
					this.btc_time_buyin);
		
			log.info("TransProcess[SUMMARY]: " + "time:" + this.btc_time_buyin + "-" + sDateTime + 
					", btc_price:" + this.btc_buy_price + "->" + this.btc_sell_price + "(" + df1.format((this.btc_sell_price - this.btc_buy_price) / this.btc_buy_price * 100) + "%)" + 
					", amount:" + df1.format(this.btc_curt_amount) +
					", profit:" + df1.format(this.btc_profit) + "(" + df1.format(this.btc_profit / last_curt_amount * 100) + "%)" + 
					", accu profit:" + df1.format(this.btc_accumulate_profit) + "(" + df1.format(this.btc_accumulate_profit / this.btc_amount_init * 100) + "%)" +
					", fee cost:" + df1.format(this.btc_fee_cost) + "(" + df1.format(this.btc_fee_cost / last_curt_amount * 100) + "%)" + 
					", accu fee cost:" + df1.format(this.btc_accumulate_fee_cost) + "(" + df1.format(this.btc_accumulate_fee_cost / this.btc_amount_init * 100) + "%)" +
					", accu times:" + this.btc_trans_count + ", accu succ times:" + this.btc_trans_succ_count + "(" + df1.format((double)this.btc_trans_succ_count / (double)this.btc_trans_count * 100) + "%)");
			
			this.btc_profit		= 0;
			this.btc_fee_cost	= 0;
		}	
	}
	
	/**
	 * 执行买入操作
	 * @param sDatetime 当前时间
	 * @param price 当前价格
	 * @param reason 买入原因
	 */
	public void BuyAction(String sDateTime, double price, int reason) {
		
		this.btc_time_buyin	= sDateTime;
		this.btc_buy_reason	= reason;
		double amount = 0;
		
		if (this.mode == MODE.ACTUAL && this.trade_mode == true) {//实盘（真实交易）
			try {
				ArrayList<TradeRet> tr_list = this.btc_trade_action.DoBuy(this.btc_invest_position);
				//买入成功
				if (tr_list != null && tr_list.size() > 0) {
					this.btc_curt_position = 10;
					
					double sum_price = 0;
					for (TradeRet tr : tr_list) {
						this.btc_curt_quantity	+= tr.deal_amount;
						sum_price				+= tr.avg_price;
						this.btc_fee_cost 		+= (this.btc_curt_quantity * tr.avg_price * this.BTC_FEE);
						amount					+= (this.btc_curt_quantity * tr.avg_price);
					}
					this.btc_buy_price	= sum_price / tr_list.size();
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
		
		this.btc_trans_rec.InsertTransDetail(this.btc_trans_postfix,
				sDateTime, 
				BTCTransRecord.OPT.OPT_BUY, 
				this.btc_curt_quantity, 
				this.btc_buy_price);
		
		DecimalFormat df1 = new DecimalFormat("#0.00");
		log.info("TransProcess[BUY]: quantity:" + df1.format(this.btc_curt_quantity) +
				", price:" + df1.format(this.btc_buy_price) +
				", amount:" + df1.format(amount) +
				", curt position:" + this.btc_curt_position);
		
		return;
	}
	
	/**
	 * 交易模式切换
	 * 注意：先清空仓位，再进入真实交易模式，否则数据统计会有问题
	 * @return
	 * @throws InterruptedException 
	 */
	public void TradeModeSwitch(String sDateTime) throws InterruptedException {
		
		//只有实盘才涉及是否进行真实交易
		if (this.mode == MODE.ACTUAL) {
			
			if (CheckTransMode(sDateTime) == true) {
				//进入真实交易模式
				
				if (this.trade_mode == true) {//已经是真实交易
					log.info("already in real trade mode ...");
					return;
				}
				else {//转成真实交易
					log.info("start change to real trade mode ...");
					
					this.btc_trans_postfix = "actual";
					this.btc_trans_rec.InitTable(this.btc_trans_postfix);
					
					//获取当前资金
					UserInfo user_info	= this.btc_api.ApiUserInfo();
					if (user_info != null) {
						this.btc_amount_init	= user_info.cny;
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
					log.info("already in virtual trade mode ...");
					return;
				}
				else {//转成模拟交易
					log.info("change to virtual trade mode");
					
					//卖出当前所有仓位
					ArrayList<TradeRet> tr_list = this.btc_trade_action.DoSell(10);
					this.btc_trans_stra.curt_status = BTCTransStrategy3.STATUS.READY;
					if (tr_list != null) {
						//获取当前资金
						UserInfo user_info	= this.btc_api.ApiUserInfo();
						if (user_info != null) {
							this.btc_amount_init	= user_info.cny;
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
	
	/**
	 * 处理买卖相关
	 * @param sDateTime
	 */
	private void ProcTrans(String sDateTime) {
		
		this.btc_trans_stra.InitPoint();
		this.btc_trans_stra.CheckPoint(this.btc_data);
		
		BTCTotalRecord record	= this.btc_data.BTCRecordOptGetByCycle(0, null);
		
		
		//如果还未入场，则判断是否要入场
		if (this.btc_trans_stra.curt_status == BTCTransStrategy3.STATUS.READY) {
			
			int ret = this.btc_trans_stra.IsBuy(sDateTime);
			if (ret != 0) {
				this.BuyAction(sDateTime, record.close, ret);
				
				RecordForInit(sDateTime, record);
				RecordForDay(sDateTime, record, OPT.BUY);
				RecordForMonth(sDateTime, record, OPT.BUY);
				RecordForYear(sDateTime, record, OPT.BUY);
			}
		}
		//如果已入场，则判断是否要出场
		else if (this.btc_trans_stra.curt_status != BTCTransStrategy3.STATUS.READY) {
			
			//判断是否要出场
			int sell_position = this.btc_trans_stra.IsSell(sDateTime);
			
			//需要出场
			if (sell_position > 0) {
				this.SellAction(sDateTime, record.close, sell_position);
//				this.btc_trans_stra.CleanStatus();
			}
			
			if (sell_position == 10) {
				RecordForDay(sDateTime, record, OPT.SELL);
				RecordForMonth(sDateTime, record, OPT.SELL);
				RecordForYear(sDateTime, record, OPT.SELL);
			}
		}
	}
	
	/**
	 * 记录初始信息
	 * @param sDateTime
	 * @param record
	 */
	public void RecordForInit(String sDateTime, BTCTotalRecord record) {
		if (this.btc_time_init == null) {
			this.btc_time_init	= sDateTime;
			this.btc_price_init	= record.close;
		}
	}
	
	/**
	 * 记录日收益
	 * @param sDateTime
	 * @param record
	 */
	public void RecordForDay(String sDateTime, BTCTotalRecord record, OPT opt) {
		
		DecimalFormat df1 = new DecimalFormat("#0.00");
		
		String curt_day = sDateTime.substring(0, 8);
		
		//初始化记录每天的起始信息
		if (this.btc_day_time_init == null && opt == OPT.BUY) {
			this.btc_day_time_init 		= sDateTime;
			this.btc_day_price_init 	= record.close;
			this.btc_day_amount_init	= this.btc_amount_init;
		}
		//记录每天收益信息
		else if (!curt_day.equals(this.btc_day_time_init.substring(0, 8)) && opt == OPT.SELL) {
			log.info("TransProcess[DAY]: day:" + this.btc_day_time_init.substring(0, 8) +
					", time:" + this.btc_day_time_init + "->" + sDateTime +
					", price:" + df1.format(this.btc_day_price_init) + "->" + df1.format(record.close) + "(" + df1.format((record.close - this.btc_day_price_init) / this.btc_day_price_init * 100) + "%)" +
					", amount:" + df1.format(this.btc_day_amount_init) + "->" + df1.format(this.btc_curt_amount) + "(" + df1.format((this.btc_curt_amount - this.btc_day_amount_init) / this.btc_day_amount_init * 100) + "%)" +
					", accu price:" + df1.format(this.btc_price_init) + "->" + df1.format(record.close) + "(" + df1.format((record.close - this.btc_price_init) / this.btc_price_init * 100) + "%)" +
					", accu amount:" + df1.format(this.btc_amount_init) + "->" + df1.format(this.btc_curt_amount) + "(" + df1.format((this.btc_curt_amount - this.btc_amount_init) / this.btc_amount_init * 100) + "%)");
			
			this.btc_day_time_init 		= sDateTime;
			this.btc_day_amount_init	= this.btc_curt_amount;
			this.btc_day_price_init		= record.close;
		}
	}
	
	/**
	 * 记录月收益
	 * @param sDateTime
	 * @param record
	 */
	public void RecordForMonth(String sDateTime, BTCTotalRecord record, OPT opt) {
		
		DecimalFormat df1 = new DecimalFormat("#0.00");
		
		String curt_month = sDateTime.substring(0, 6);
		
		//初始化记录每月的起始信息
		if (this.btc_month_time_init == null && opt == OPT.BUY) {
			this.btc_month_time_init 		= sDateTime;
			this.btc_month_price_init 	= record.close;
			this.btc_month_amount_init	= this.btc_amount_init;
		}
		//记录每月收益信息
		else if (!curt_month.equals(this.btc_month_time_init.substring(0, 6)) && opt == OPT.SELL) {
			log.info("TransProcess[MONTH]: month:" + this.btc_month_time_init.substring(0, 6) +
					", time:" + this.btc_month_time_init + "->" + sDateTime +
					", price:" + df1.format(this.btc_month_price_init) + "->" + df1.format(record.close) + "(" + df1.format((record.close - this.btc_month_price_init) / this.btc_month_price_init * 100) + "%)" +
					", amount:" + df1.format(this.btc_month_amount_init) + "->" + df1.format(this.btc_curt_amount) + "(" + df1.format((this.btc_curt_amount - this.btc_month_amount_init) / this.btc_month_amount_init * 100) + "%)" +
					", accu price:" + df1.format(this.btc_price_init) + "->" + df1.format(record.close) + "(" + df1.format((record.close - this.btc_price_init) / this.btc_price_init * 100) + "%)" +
					", accu amount:" + df1.format(this.btc_amount_init) + "->" + df1.format(this.btc_curt_amount) + "(" + df1.format((this.btc_curt_amount - this.btc_amount_init) / this.btc_amount_init * 100) + "%)");
			
			this.btc_month_time_init 	= sDateTime;
			this.btc_month_amount_init	= this.btc_curt_amount;
			this.btc_month_price_init	= record.close;
		}
	}
		
		
	/**
	 * 记录年收益
	 * @param sDateTime
	 * @param record
	 */
	public void RecordForYear(String sDateTime, BTCTotalRecord record, OPT opt) {
		
		DecimalFormat df1 = new DecimalFormat("#0.00");
		
		//初始化记录每年信息的起始年
		if (this.btc_year_time_init == null && opt == OPT.BUY) {
			this.btc_year_time_init = sDateTime;
			this.btc_year_price_init = record.close;
		}
		//记录每年年终信息
		else if (!sDateTime.substring(0, 4).equals(this.btc_year_time_init.substring(0, 4)) && opt == OPT.SELL) {
			log.info("TransProcess: year:" + this.btc_year_time_init.substring(0, 4) +
					", time:" + this.btc_year_time_init + "->" + sDateTime + 
					", price:" + df1.format(this.btc_year_price_init) + "->" + df1.format(record.close) + "(" + df1.format((record.close - this.btc_year_price_init) / this.btc_year_price_init * 100) + "%)" +
					", amount:" + df1.format(this.btc_year_amount_init) + "->" + df1.format(this.btc_curt_amount) + "(" + df1.format((this.btc_curt_amount - this.btc_year_amount_init) / this.btc_year_amount_init * 100) + "%)");
			
			this.btc_year_time_init = sDateTime;
			this.btc_year_amount_init	= this.btc_curt_amount;
			this.btc_year_price_init	= record.close;
		}
	}
	
	public void Route() {
		
		//复盘
		if (this.mode == MODE.REVIEW) {
			log.info("start by mock src");
			
			//从数据库mock数值的初始化
			this.btc_data.BTCMockInit(this.review_time_s, this.review_time_e);
			
			//每次mock一条
			while (this.btc_data.btc_mock_it.hasNext()) {
				String sDateTime		= this.btc_data.btc_mock_it.next();
				BTCTotalRecord record	= this.btc_data.btc_mock_map.get(sDateTime);
				this.btc_data.b_record_map.put(sDateTime, record);
				
				log.info("proc " + sDateTime);
				ProcTrans(sDateTime);
			}
		}
		else {
			log.info("BTCProcThread start online");
			
			long stamp_millis;
			long stamp_sec;
			DateFormat df	= new SimpleDateFormat("yyyyMMddHHmmss"); 
			
			long last_stamp_sec = 0;
			
			while (true) {
				
				stamp_millis = System.currentTimeMillis();
				stamp_sec = stamp_millis / 1000;
				
				//防止同一秒钟内处理多次
				if (stamp_sec == last_stamp_sec) {
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					continue;
				}

				if (stamp_sec % this.data_cycle == 0) {
					
					Date cur_date = new Date(stamp_millis);
					String sDateTime = df.format(cur_date); 
					
					try {
						this.TradeModeSwitch(sDateTime);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						log.error("mode switch failed!", e);
						System.exit(0);
					}
					
					while (true) {
						this.btc_data.BTCDataLoadFromDB(20, null);
						if (!this.btc_data.b_record_map.containsKey(sDateTime)) {
							log.info("just wait k of " + sDateTime);
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						else {
							break;
						}
					}
					
					log.info("proc " + sDateTime);
					ProcTrans(sDateTime);
					
					this.btc_data.b_record_map.clear();
				}
				
				last_stamp_sec = stamp_sec;
				
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		//K线周期，运行模式， 标识（只有复盘有用），[复盘起始时间]， [复盘结束时间]
		int data_cycle = Integer.parseInt(args[0]);
		MODE mode = MODE.values()[Integer.parseInt(args[1])];//0-REVIEW;1-ACTUAL
		String context = args[2];
		
		String time_s = null;
		String time_e = null;
		if (args.length >= 4) {
			time_s = args[3];
		}
		
		if (args.length >= 5) {
			time_e = args[4];
		}
		
		System.out.println("para:" + data_cycle + "," + mode + "," + context + "," + time_s + "," + time_e);
		
		BTCTransSystem btc_ts = new BTCTransSystem(data_cycle, mode, context, time_s, time_e);
		btc_ts.Route();
	}
}
