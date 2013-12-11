package com.latupa.stock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 计算每天的仓位、利润
 * TODO
 * 1.计算完成更新calc.flag文件中的时间戳（在该文件中新增总资金配置）done
 * 2.查询每天的大盘指数 done
 * 3.把每天的计算结果更新到数据库中 done
 * @author Administrator
 *
 */
public class CalcInfo {
	
	private static final Log log = LogFactory.getLog(StockPrice.class);
	
	private StockPriceNew spn;
	
	private TransDetail td;
	
	//记录交易股票的利润信息
	//key:date+market+code（date为股票入场的时间）
	//value:ProfitRecord
	private HashMap<String, ProfitRecord> profit_record = new HashMap<String, ProfitRecord>();
	
	//计算标识文件，读取计算的起始时间
	private static final String FLAG_FILE_DIR = "src/main/resources/";
	private String flag_file = "calc.flag";
	
	//标识文件中的计算起始时间
	public String date_s;
	
	//标识文件中的当前总资金
	public double total;
	
	//标识文件中的描述信息
	private String desc;
	
	public static final String TABLE_NAME_PRE = "stock_summary_daily";
	private String table_name;
	
	private static final String DATE_NOW = "00000000";
	
	//表结构对应的字段
	public class Record {
		String date;
		double sh_price;
		double sz_price;
		double total;
		double position;
		double rate;
		double profit;
	}
	
	//仓位信息
	public class Position {
		double amount; //持仓金额
		double stock_num; //持仓股票个数
	}
	
	//股票获利记录
	public class ProfitRecord {
		double base; //入场资金
		double max; //入场期间最大收益
		double result; //出场时候的收益
	}
		
	/**
	 * 
	 * @param dbInst
	 * @param table_postfix
	 */
	public CalcInfo(DBInst dbInst, String table_postfix) {
		
		//初始化数据库表
		this.table_name = (table_postfix.equals("")) ? TABLE_NAME_PRE : TABLE_NAME_PRE + "__" + table_postfix;
		String sql = "create table if not exists " + this.table_name +
				"(`date` date NOT NULL DEFAULT '0000-00-00', " +
				"`szzs` double NOT NULL DEFAULT '0' COMMENT '上证综指', " +
				"`szcz` double NOT NULL DEFAULT '0' COMMENT '深证成指', " +
				"`total` double NOT NULL DEFAULT '0' COMMENT '总资金', " +
				"`position` double NOT NULL DEFAULT '0' COMMENT '股市资金', " +
				"`rate` double NOT NULL DEFAULT '0' COMMENT '仓位百分比，单位%', " +
				"`profit` double NOT NULL DEFAULT '0', " +
				"PRIMARY KEY (`date`) " +
				") ENGINE=MyISAM DEFAULT CHARSET=utf8"; 
		
		dbInst.updateSQL(sql);
		
		spn = new StockPriceNew(dbInst);
		//sp.LoadStockPrice(StockPrice.UPDATE_STOCK_TRANSED_CODE, "", "");
		
		td = new TransDetail(table_postfix, dbInst);
		td.ReadRecordsFromDB();
		
		flag_file = (table_postfix.equals("")) ? flag_file : flag_file + "." + table_postfix;
		
		try {
			FileInputStream fis		= new FileInputStream(FLAG_FILE_DIR + flag_file);
	        InputStreamReader isr	= new InputStreamReader(fis, "utf8");
	        BufferedReader br		= new BufferedReader(isr);
	        
	        Pattern pattern_date = Pattern.compile("^\\d{8}$");

	        String line = br.readLine();
	        
	        br.close();
	        isr.close();
	        fis.close();
	        
	        if (line != null) {
	        	String arrs[] = line.split(" ");
	        	
	        	Matcher matcher = pattern_date.matcher(arrs[0]);
	            if (matcher.matches()) {
	            	this.date_s	= arrs[0];
	            	this.total	= Double.parseDouble(arrs[1]);
	            	this.desc	= arrs[2];
	            	return;
	            }
	            else {
	            	log.error("the date read from " + FLAG_FILE_DIR + flag_file + " incorrect! date:" + this.date_s);
	            }
	        }
	        else {
	        	log.error("read " + FLAG_FILE_DIR + flag_file + " is null!");
	        }
	        
	    	System.exit(0);
		}
		catch (Exception e) {
			log.error("read " + FLAG_FILE_DIR + flag_file + "failed!", e);
			System.exit(0);
		}
	}
	
	/**
	 * 把计算获得的数据写入DB
	 * @param dbInst
	 * @param record
	 */
	public void StoreRecordToDB(DBInst dbInst, Record record) {
		String sql = "insert into " + this.table_name + "(date, szzs, szcz, total, position, rate, profit) values " +
				"('" + record.date + "', " + 
				record.sh_price + ", " + 
				record.sz_price + ", " + 
				record.total + ", " + 
				record.position + ", " + 
				record.rate + ", " + 
				record.profit + ") " + 
				"ON DUPLICATE KEY UPDATE szzs = " + record.sh_price + ", " +
				"szcz = " + record.sz_price + ", " +
				"total = " + record.total + ", " +
				"position = " + record.position + ", " +
				"rate = " + record.rate + ", " +
				"profit = " + record.profit;
		dbInst.updateSQL(sql);
	}
	
	/**
	 * 
	 * @param date
	 * @param market
	 * @param code
	 * @param profit
	 * @param is_out 是否是出场时的利润
	 */
	public void UpdateProfitRecord(String date, String market, String code, double amount, double profit, boolean is_out) {
		String key = date + " " + market + " " + code;
		
		if (!profit_record.containsKey(key)) {
			ProfitRecord pr = new ProfitRecord();
			pr.base = amount;
			if (is_out) {
				pr.result = profit;
			}
			else {
				pr.max = profit;
			}
			profit_record.put(key, pr);
		}
		else {
			ProfitRecord pr = profit_record.get(key);
			if (is_out) {
				pr.result = profit;
				profit_record.put(key, pr);
			}
			else {
				if (profit > pr.max) {
					pr.max = profit;
					profit_record.put(key, pr);
				}
			}
		}
	}
	
	public void ScanProfitRecord() {
		
		DecimalFormat df = new DecimalFormat("0.##");
		
		TreeMap<String, ProfitRecord> pr_map = new TreeMap<String, ProfitRecord>(profit_record);
		for (String key : pr_map.keySet().toArray(new String[0])) {
			ProfitRecord pr = pr_map.get(key);
			log.info(key + ":" + df.format(((pr.base > 0) ? pr.max / pr.base : pr.base) * 100) + "%," + 
			df.format(((pr.base > 0) ? pr.result / pr.base : pr.base) * 100) + 
			"% (" + df.format(pr.base) + "," + df.format(pr.max) + "," + df.format(pr.result) + ")");
		}
	}
	
	/**
	 * 计算指定日期的利润
	 * @param date
	 * @return
	 * @throws Exception 
	 */
	public double CalcForProfit(String date) {
		
		log.info("start to calc profit for date " + date);
		
		double profit_total	= 0;
		
		for (String key : td.trans_map.keySet().toArray(new String[0])) {
			
			String[] arrs 	= key.split("\005");
			String market	= arrs[0];
			String code		= arrs[1];
			
			double profit	= 0;//该股票所有交易周期的利润汇总
			double amount	= 0;
			
			ArrayList<TransDetailRecord> list = td.trans_map.get(key);
			TransDetailRecord record		= null;
			TransDetailRecord record_pre	= record;
			
			int i;
			for (i = 0; i < list.size(); i++) {
				
				record = list.get(i);
				
				double profit_trans = 0; //该股票每次交易周期的利润
				
				String stock_date	= record.date;
				double stock_num	= record.num;
				double stock_total	= record.deal_total;
				
				//如果指定日期为交易当天
				if (date.compareTo(stock_date) == 0) {
					/**
					 * [start, end]
					 *    ^
					 */
					//如果为入场当天，则根据当天收盘市值-入场价格（含税）
					if (record.opt == TransDetailRecord.OPT_IN) {
						PriceRecord pr;
						try {
							pr = spn.GetStockPriceFromDB(market, code, date);
							
							profit_trans = (pr.close * stock_num) - stock_total;
							
							profit += profit_trans;
							amount = stock_total;
							
							UpdateProfitRecord(stock_date, market, code, amount, profit_trans, false);
							
						} catch (Exception e) {
							log.error(e.toString(), e);
							System.exit(0);
						}
					}
					/**
					 * [start, end]
					 *          ^
					 */
					//如果为出场当天，则根据出场价格（含税）-入场价格（含税）
					else if (record.opt == TransDetailRecord.OPT_OUT) {
						
						/**
						 * 		,end]
						 *        ^
						 */
						//有可能record_pre为空，即缺少入场的交易记录，对于这种情况只能忽略
						if (record_pre != null) {
							double stock_total_pre	= record_pre.deal_total;
							
							profit_trans = stock_total - stock_total_pre;
							profit += profit_trans;
							amount = stock_total_pre;
							
							UpdateProfitRecord(record_pre.date, market, code, amount, profit_trans, true);
						}
					}
					
					break;
				}
				
				//如果指定日期大于交易当天
				if (date.compareTo(stock_date) > 0) {
					
					/**
					 * [start, end]
					 *               ^
					 */
					//如果当天记录为出场，则算上该历史交易的利润
					if (record.opt == TransDetailRecord.OPT_OUT) {
						
						/**
						 * 		,end]
						 *             ^
						 */
						//有可能record_pre为空，即缺少入场的交易记录，对于这种情况只能忽略
						if (record_pre != null) {
							double stock_total_pre	= record_pre.deal_total;
							
							profit_trans = stock_total - stock_total_pre;
							profit += profit_trans;
							amount = stock_total_pre;
							
							UpdateProfitRecord(record_pre.date, market, code, amount, profit_trans, true);
						}
					}
				}
				
				//如果指定日期小于交易当天
				if (date.compareTo(stock_date) < 0) {
					/**
					 *      [start, end]
					 *   ^
					 */
					//如果是小于入场交易日期
					if (record.opt == TransDetailRecord.OPT_IN) {
						//do nothing
					}
					/**
					 * [start, end]
					 *        ^
					 */
					//如果是在历史交易端的中间，则根据历史收盘市值-入场价格（含税）
					else if (record.opt == TransDetailRecord.OPT_OUT) {
						PriceRecord pr;
						try {
							pr = spn.GetStockPriceFromDB(market, code, date);
							/**
							 * 		,end]
							 *     ^
							 */
							//有可能record_pre为空，即缺少入场的交易记录，对于这种情况只能忽略
							if (record_pre != null) {
								double stock_num_pre	= record_pre.num;
								double stock_total_pre	= record_pre.deal_total;
								
								profit_trans = (pr.close * stock_num_pre) - stock_total_pre;
								profit += profit_trans;
								amount = stock_total_pre;
								
								UpdateProfitRecord(record_pre.date, market, code, amount, profit_trans, false);
							}
						} catch (Exception e) {
							log.error(e.toString(), e);
							System.exit(0);
						}
					}
					
					break;
				}
				record_pre	= record;
			}
			
			//如果遍历完了，最后一种情况，指定日期大于入场日期，且该次入场还未出场
			if (i == list.size()) { 
				
				double profit_trans = 0; //该股票每次交易周期的利润
				
				/**
				 * [start,
				 *           ^
				 */
				//则根据当天收盘市值-入场价格（含税）
				if (record_pre.opt == TransDetailRecord.OPT_IN) { 
					PriceRecord pr;
					try {
						pr = spn.GetStockPriceFromDB(market, code, date);
						double stock_num_pre	= record_pre.num;
						double stock_total_pre	= record_pre.deal_total;
						
						profit_trans = (pr.close * stock_num_pre) - stock_total_pre;
						profit += profit_trans;
						amount = stock_total_pre;
						
						UpdateProfitRecord(record_pre.date, market, code, amount, profit_trans, false);
						
					} catch (Exception e) {
						log.error(e.toString(), e);
						System.exit(0);
					}
				}
			}
			
			//这支股票是否在指定的日期已经入场，如果还没有入场，则不涉及利润计算
			if (amount > 0) {
				profit_total += profit;
				double rate = profit / amount * 100;
				
				DecimalFormat df = new DecimalFormat("0.##");
				if (Math.abs(rate) >= 30) {
					log.info("	code:" + code +  ", profit:" + df.format(profit) + ", rate:" + df.format(rate) + "-----------------------!!!!!!!");
				}
				else {
					//log.info("	code:" + code +  ", profit:" + df.format(profit) + ", rate:" + df.format(rate));
				}
				
			}
			
		}
		
		return profit_total;
	}
	
	/**
	 * 计算date_s到当前时间的仓位和利润
	 */
	public void CalcForDateRange(String date_e, DBInst dbInst) {
		//获取当前时间
		SimpleDateFormat sdf	= new SimpleDateFormat("yyyyMMdd");  
		
		if (date_e.equals(DATE_NOW)) { 
			date_e = sdf.format(new Date()); 
		}
		
		log.info("calc from " + date_s + " to " + date_e);
		
		Date date;
		String date_str = "";
		
		try {
			
			Record record = new Record();
			
			date 		= sdf.parse(date_s);
			date_str	= sdf.format(date);
			
			while (Integer.parseInt(date_str) <= Integer.parseInt(date_e)) {
				double profit = CalcForProfit(date_str);
				Position position = CalcForPositionAmount(date_str);
				//System.out.println("date:" + date_str + ",profit:" + profit + ",amount:" + amount);
				
				//获取上证大盘的指数
				//StockPrice.PriceRecord sh_pr = sp.GetStockPrice(StockPrice.CODE_SZZS, TransDetailRecord.MARKET_SH, date_str);
				PriceRecord sh_pr = spn.GetStockPriceFromDB(TransDetailRecord.MARKET_SH, StockPrice.CODE_SZZS, date_str);
				
				//获取深证大盘的指数
				//StockPrice.PriceRecord sz_pr = sp.GetStockPrice(StockPrice.CODE_SZCZ, TransDetailRecord.MARKET_SZ, date_str);
				PriceRecord sz_pr = spn.GetStockPriceFromDB(TransDetailRecord.MARKET_SZ, StockPrice.CODE_SZCZ, date_str);
				
				record.date = date_str;
				record.sh_price	= sh_pr.close;
				record.sz_price	= sz_pr.close;
				record.total	= this.total;
				record.position	= position.amount;
				//record.rate		= (record.total == 0) ? 0 : record.position / record.total * 100;
				record.rate		= position.stock_num / 10 * 100;
				record.profit	= profit;
				
				//写入到DB中
				StoreRecordToDB(dbInst, record);
				
				date.setTime((date.getTime() / 1000 + 86400) * 1000);
				date_str = sdf.format(date);
			}
		} catch (Exception e) {
			log.error("calc from " + date_s + " to " + date_e + " failed!", e);
			e.printStackTrace();
		}
		
		ScanProfitRecord();
		
		//完成后把结束日期更新到标识文件中
		try {
			FileWriter fw;
			fw = new FileWriter(FLAG_FILE_DIR + flag_file, false);
	        String write_context = date_str + " " + String.valueOf(this.total) + " " + this.desc;
	        fw.write(write_context);
	        fw.close();
		} catch (Exception e) {
			log.error("save flag to " + FLAG_FILE_DIR + flag_file + " failed!");
			System.exit(0);
		}

	}
	
	/**
	 * 计算指定日期的仓位
	 * [start1, end1],[start2, end2],...,[startn, endn]
	 *         ^      
	 * 找到指定日期在交易片段中的位置，小于或者等于指定日期的记录，如果该记录为买入，则表示在仓位中
	 * @param date
	 * @return
	 */
	public Position CalcForPositionAmount(String date) {
		
		log.info("start to calc position amount for date " + date);
		
		double position_amount	= 0;
		int stock_num = 0; //股票个数
		
		for (String key : td.trans_map.keySet().toArray(new String[0])) {
			
//			String[] arrs = key.split("\005");
//			String market	= arrs[0];
//			String code		= arrs[1];
			
			ArrayList<TransDetailRecord> list = td.trans_map.get(key);
			TransDetailRecord record		= null;
			TransDetailRecord record_pre	= record;
			
			int i;
			for (i = 0; i < list.size(); i++) {
				
				record = list.get(i);
				
				String stock_date	= record.date;
				
				//如果指定日期为入、出场当天，则都算仓位
				if (date.compareTo(stock_date) == 0) { 
					position_amount += record.deal_amount;
					stock_num++;
					break;
				}
				//找到指定日期的位置，然后判断其前一个交易是否为入场，如果是则算仓位
				else if (date.compareTo(stock_date) < 0) { 
					if ((record_pre != null) && (record_pre.opt == TransDetailRecord.OPT_IN)) { 
						position_amount += record_pre.deal_amount;
						stock_num++;
					}
					break;
				}
				
				record_pre	= record;
			}
			
			//如果遍历完了，说明指定日期大于所有交易，则如果最后的交易是买入，则计算仓位
			if (i == list.size()) { 
				if (record_pre.opt == TransDetailRecord.OPT_IN) { 
					position_amount += record_pre.deal_amount;
					stock_num++;
				}
			}
		}
		
		Position position = new Position();
		position.amount 	= position_amount;
		position.stock_num	= stock_num;
		
		return position;
	}
	

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		if (args.length != 2 && args.length != 6) {
			CalcInfo.usage();
			System.exit(0);
		}
		
		if (!args[0].equals("-m")) {
			CalcInfo.usage();
			System.exit(0);
		}
		
		//DBInst dbInst	= new DBInst("jdbc:mysql://localhost:3306/stock", "latupa", "latupa");
		DBInst dbInst	= new DBInst("jdbc:mysql://192.168.116.153:3306/stock", "latupa", "latupa");
		
		int mode = Integer.parseInt(args[1]);
		
		if (mode == 1) {
			
			CalcInfo ci = new CalcInfo(dbInst, "");
			
			ci.CalcForDateRange(CalcInfo.DATE_NOW, dbInst);
			//ci.CalcForDateRange("20130307", dbInst);
		}
		else if (mode == 2) {
			if (!args[2].equals("-p")) {
				CalcInfo.usage();
				System.exit(0);
			}
			
			String table_postfix = args[3];
			
			if (!args[4].equals("-d")) {
				CalcInfo.usage();
				System.exit(0);
			}
			
			String date_end = args[5];
			
			CalcInfo ci = new CalcInfo(dbInst, table_postfix);
			ci.CalcForDateRange(date_end, dbInst);
		}
		else {
			CalcInfo.usage();
			System.exit(0);
		}
		
		log.info("proc finish");
	}
	
	public static void usage() {
		System.out.println("usage as follow:");
		System.out.println("	-m mode 类型：1-日常；2-复盘");
		System.out.println("		none");
		System.out.println("    	-p table_postfix 表名后缀，用于复盘时候独立表内容");
		System.out.println("    	      提前修改calc.flag.table_postfix文件，计算从该标识文件的时间戳到现在为止");
		System.out.println("    		-d date 计算的截止时间");
	}

}
