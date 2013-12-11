package com.latupa.stock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TransDetail {
	
	private static final Log log = LogFactory.getLog(TransDetail.class);
	
	//记录从日志中解析完的明细数据
	//key:code+opt（导入交易数据的时候考虑到合并连续的相同买卖）
	//key:code+date
	//value:TransDetailRecord
	private HashMap<String, TransDetailRecord> record_map = new HashMap<String, TransDetailRecord>();
	
	//从数据库中读取交易明细，用于计算
	//key:market+code
	//value:list of ordered TransDetailRecord
	public HashMap<String, ArrayList<TransDetailRecord>> trans_map = new HashMap<String, ArrayList<TransDetailRecord>>();
	
	public String table_name = "stock_trans_detail";
	
	//数据库连接
	public DBInst dbInst;  
	
	public TransDetail(String table_postfix, DBInst dbInst) {
		table_name = (table_postfix.equals("")) ? table_name : table_name + "__" + table_postfix;
		this.dbInst = dbInst;
	}
	
	/**
	 * 用于复盘时新增交易记录
	 * @param table_postfix
	 * @param record_info
	 */
	public void RecordParse(String record_info) {
        
		log.info("start parse " + record_info + " for " + table_name);
		
        String[] arrs	= null;
        
        Pattern pattern_date = Pattern.compile("^\\d{8}$");
        
        arrs = record_info.split(",");
        
        if (arrs.length > 4 || arrs.length < 3) {
        	log.error("record items error! record:" + record_info);
        	System.exit(0);
        }
        
        Matcher matcher = pattern_date.matcher(arrs[0]);
        if (! matcher.matches()) {
        	log.error("date format error! date:" + arrs[0]);
        	return;
        }
        
        TransDetailRecord record = new TransDetailRecord();
        
        record.date 	= arrs[0];
        record.code 	= arrs[1];
        record.opt		= Integer.parseInt(arrs[2]);
        
        if (arrs.length == 4) {
        	record.market	= arrs[3];
        }
        else {
	        if (Double.parseDouble(record.code) >= 600000) {
	        	record.market = TransDetailRecord.MARKET_SH;
	        }
	        else {
	        	record.market = TransDetailRecord.MARKET_SZ;
	        }
        }
        
        StockPriceNew spn = new StockPriceNew(dbInst);
        PriceRecord pr = null;
        pr = spn.GetStockPriceFromDB(record.market, record.code, record.date);
        if (pr == null) {
        	log.error("get stock price failed! code:" + record.code + " date:" + record.date);
        	System.exit(0);
        }
        //record.deal_price	= Double.parseDouble(arrs[4]);
        
        if (record.opt == TransDetailRecord.OPT_IN) {
        	record.deal_amount	= 20000;
        	record.deal_price	= pr.open;
        	//record.deal_amount	= Double.parseDouble(arrs[5]);
            record.num			= (int)(record.deal_amount / record.deal_price);
        }
        else if (record.opt == TransDetailRecord.OPT_OUT) {
        	ReadRecordsFromDB();
        	String key = record.market + "\005" + record.code;
        	ArrayList<TransDetailRecord> trans_list = trans_map.get(key);
        	TransDetailRecord tran = trans_list.get(trans_list.size() - 1);
        	if (tran.opt != TransDetailRecord.OPT_IN) {
        		log.error("trans opt in db error! code:" + record.code + " date:" + tran.date);
            	System.exit(0);
        	}
        	record.num			= tran.num;
        	record.deal_price	= pr.close;
        	//record.num	= Integer.parseInt(arrs[5]);
        	record.deal_amount	= record.num * record.deal_price;
        }
        else {
        	log.error("record opt error! opt:" + record.opt);
        	System.exit(0);
        }
        
        record.name 			= "";
        record.deal_fee_comm	= 0;
        record.deal_fee_tax		= 0;
        record.deal_fee_trans	= 0;
        record.deal_fee_other	= 0;
        record.deal_total		= record.deal_amount; 
        
        String keyowrd = record.code + "\005" + String.valueOf(record.opt);
        record_map.put(keyowrd, record);
	}
	
	/**
	 * 日志解析
	 * @throws Exception
	 */
	public void LogParse(String file) {
		
		log.info("start parse " + file);
		
		try {
	        FileInputStream fis		= new FileInputStream(file);
	        InputStreamReader isr	= new InputStreamReader(fis, "gbk");
	        BufferedReader br		= new BufferedReader(isr);
	        
	        String line	= "";
	        String[] arrs	= null;
	        
	        Pattern pattern_date = Pattern.compile("^\\d{8}$");
	        
	        int line_num = 0;
	        while ((line = br.readLine()) != null) {
	        	line_num++;
	        	
	        	//字段之间用空格分隔，但是空格个数不定，同时要避免股票名称中可能会出现的一个空格
	            arrs = line.split("\\s{2,}");
	            
	            Matcher matcher = pattern_date.matcher(arrs[0]);
	            if (! matcher.matches()) {
	            	log.error("date format error for line " + line_num + "! date:" + arrs[0]);
	            	continue;
	            }
	            
		        TransDetailRecord record = new TransDetailRecord();
	            
	            record.date = arrs[0];
	            record.code = arrs[1];
	            record.name = arrs[2];
	            record.deal_price	= Double.parseDouble(arrs[4]);
	            record.num			= Integer.parseInt(arrs[5]);
	            record.deal_amount	= Double.parseDouble(arrs[6]);
	            record.deal_total		= Double.parseDouble(arrs[7]); 
	            record.deal_fee_comm	= Double.parseDouble(arrs[8]);
	            record.deal_fee_tax		= Double.parseDouble(arrs[9]);
	            record.deal_fee_trans	= Double.parseDouble(arrs[10]);
	            record.deal_fee_other	= Double.parseDouble(arrs[11]);


	            //修正负数
	            if (arrs[3].equals(new String("买入"))) {
	            	record.opt	= TransDetailRecord.OPT_IN;
	            	record.deal_total	= -record.deal_total;
	            }
	            else if (arrs[3].equals(new String("卖出"))) {
	            	record.opt	= TransDetailRecord.OPT_OUT;
	            	record.num	= -record.num;
	            }
	            else {
	            	log.error("trans record opt error! file:" + file + ", line:" + line_num);
	            	continue;
	            }
	            
	            if (arrs[13].startsWith("A262235553")) {
	            	record.market	= TransDetailRecord.MARKET_SH;
	            }
	            else if (arrs[13].startsWith("0139116982")) {
	            	record.market	= TransDetailRecord.MARKET_SZ;
	            }
	            else {
	            	log.error("trans record market error! file:" + file + ", line:" + line_num);
	            	continue;
	            }
	            
	            //合并相同code和操作的记录
	            String keyowrd = record.code + "\005" + String.valueOf(record.opt);
	            if (true == record_map.containsKey(keyowrd)) {
	            	log.info("same trans need to merge! code:" + record.code + ", opt:" + record.opt);
	            	TransDetailRecord value = record_map.get(keyowrd);
	            	record = value.Merge(record);
	            }
	            record_map.put(keyowrd, record);
	        }
	        
	        br.close();
	        isr.close();
		} catch (Exception e) {
			log.error("file occur error! file:" + file, e);
			System.exit(0);
		} 
	}
	
	/**
	 * 把交易明细数据加载到数据库中
	 */
	public void StoreRecordsToDB() {
		
		log.info("start store records to db");
		
		String sql = "create table if not exists " + table_name + 
				"(`date` date NOT NULL DEFAULT '0000-00-00', " +
				"`market` varchar(32) NOT NULL DEFAULT '', " +
				"`code` varchar(32) NOT NULL DEFAULT '', " +
				"`name` varchar(64) NOT NULL DEFAULT '', " +
				"`opt` int(3) NOT NULL DEFAULT '0' COMMENT '1-in;2-out', " +
				"`num` int(11) NOT NULL DEFAULT '0', " +
				"`deal_price` double NOT NULL DEFAULT '0', " +
				"`deal_amount` double NOT NULL DEFAULT '0', " +
				"`deal_fee_comm` double NOT NULL DEFAULT '0', " +
				"`deal_fee_tax` double NOT NULL DEFAULT '0', " +
				"`deal_fee_trans` double NOT NULL DEFAULT '0', " +
				"`deal_fee_other` double NOT NULL DEFAULT '0', " +
				"`deal_total` double NOT NULL DEFAULT '0', " +
				"PRIMARY KEY (`date`,`market`,`code`), " +
				"KEY `code` (`code`)" +
				") ENGINE=MyISAM DEFAULT CHARSET=utf8";
		
		dbInst.updateSQL(sql);
		
		for (TransDetailRecord value : record_map.values().toArray(new TransDetailRecord[0])) {
			System.out.println("	proc " + value.code);
			sql = "insert ignore into " + table_name + " values ('"
					+ value.date + "','"
					+ value.market + "','"
					+ value.code + "','"
					+ value.name + "',"
					+ value.opt + ","
					+ value.num + ","
					+ value.deal_price + ","
					+ value.deal_amount + ","
					+ value.deal_fee_comm + ","
					+ value.deal_fee_tax + ","
					+ value.deal_fee_trans + ","
					+ value.deal_fee_other + ","
					+ value.deal_total + ") "
					+ "ON DUPLICATE KEY UPDATE "
					+ "name = '" + value.name + "'," 
					+ "opt = " + value.opt + ","
					+ "num = " + value.num + ","
					+ "deal_price = " + value.deal_price + ","
					+ "deal_amount = " + value.deal_amount + "," 
					+ "deal_fee_comm = " + value.deal_fee_comm + ","
					+ "deal_fee_tax = " + value.deal_fee_tax + ","
					+ "deal_fee_trans = " + value.deal_fee_trans + ","
					+ "deal_fee_other = " + value.deal_fee_other + ","
					+ "deal_total = " + value.deal_total;
			
			if (dbInst.updateSQL(sql) != true) {
				log.error("store trans record error! " + sql);
			}
		}
	}
	
	/**
	 * 从DB读取交易明细
	 * @return
	 * @throws SQLException
	 */
	public void ReadRecordsFromDB() {

		log.info("read records from db");
		
		trans_map.clear();
		
		String sql = "select (date + 0) as date, market, code, name, opt, num, deal_price, deal_amount, deal_fee_comm," +
				"deal_fee_tax, deal_fee_other, deal_total from " + table_name;
		ResultSet rs = dbInst.selectSQL(sql);
		
		try {
			while (rs.next()) {
				TransDetailRecord record = new TransDetailRecord();
				
			    record.date 	= rs.getString("date");
			    record.market	= rs.getString("market");
			    record.code 	= rs.getString("code");
			    record.name 	= rs.getString("name");
			    record.opt		= rs.getInt("opt");
			    record.num		= rs.getInt("num");
			    record.deal_price		= rs.getDouble("deal_price");
			    record.deal_amount		= rs.getDouble("deal_amount");
			    record.deal_fee_comm	= rs.getDouble("deal_fee_comm");
			    record.deal_fee_tax		= rs.getDouble("deal_fee_tax");
			    record.deal_fee_other	= rs.getDouble("deal_fee_other");
			    record.deal_total		= rs.getDouble("deal_total");
				
				ArrayList<TransDetailRecord> list;
				String key = record.market + "\005" + record.code;
				list	= trans_map.get(key);
				if (list == null) {
					list = new ArrayList<TransDetailRecord>();
				}
				list.add(record);
				trans_map.put(key, list);
			}
		} catch (Exception e) {
			log.error("sql excute failed! sql:" + sql, e);
			System.exit(0);
		}
		
		DateComparator comp = new DateComparator();
		for (String key : trans_map.keySet().toArray(new String[0])) {
			ArrayList<TransDetailRecord> list = trans_map.get(key);
			Collections.sort(list, comp);
		}
	}
	
	/**
	 * 遍历内存中的明细数据record_map
	 */
	public void ScanRecordsToDB() {
		String[] keywords = record_map.keySet().toArray(new String[0]);
		
		for (String keyword : keywords) {
			String[] arrs = keyword.split("\005");
			
			String code	= arrs[0];
			int opt		= Integer.parseInt(arrs[1]);
			System.out.println("key-> " + code + "+" + String.valueOf(opt));
			
			System.out.println("value-> " + record_map.get(keyword).toString());
		}
	}
	
	/**
	 * 根据最新的价格修复复盘交易信息
	 * @param table_postfix
	 */
	public void RepairTransRecord(String table_postfix) {
		
		ReadRecordsFromDB();
		
		String[] keywords = trans_map.keySet().toArray(new String[0]);
		
		for (String keyword : keywords) {
			String[] arrs = keyword.split("\005");
			
			String market	= arrs[0];
			String code		= arrs[1];
			System.out.println("key-> " + market + "+" + code);
			
			int last_in_num = 0;
			
			StockPriceNew spn = new StockPriceNew(dbInst);
			
			for (TransDetailRecord record : trans_map.get(keyword)) {
				System.out.println("check -> before " + record.toString());
				
				int opt = record.opt;
				String sdate = record.date;
				
				PriceRecord pr = spn.GetStockPriceFromDB(market, code, sdate);
				
				if (opt == TransDetailRecord.OPT_IN) {
					record.deal_price = pr.open;
					record.num = (int)(record.deal_amount / record.deal_price);
					
					last_in_num = record.num;
				}
				else if (opt == TransDetailRecord.OPT_OUT) {
					record.deal_price = pr.close;
					if (last_in_num == 0) {
						log.error("last in num is 0 in " + table_postfix + "! code:" + code + " date:" + sdate);
						continue;
					}
					record.num = last_in_num;
					record.deal_amount = record.deal_price * record.num;
					record.deal_total = record.deal_amount;
					
					last_in_num = 0;
				}
				
				System.out.println("check -> after " + record.toString());
				
				String keyowrd = record.code + "\005" + record.date;
			    record_map.put(keyowrd, record);
			}
		}
		
		StoreRecordsToDB();
	}
	
	/**
	 * 处理交易明细
	 * @param args
	 */
	public static void main(String[] args) {
		
		if (args.length != 4 && args.length != 6 && args.length != 5) {
			TransDetail.usage();
			System.exit(0);
		}
		
		if (!args[0].equals("-m")) {
			TransDetail.usage();
			System.exit(0);
		}
		
		//DBInst dbInst	= new DBInst("jdbc:mysql://localhost:3306/stock", "latupa", "latupa");
		DBInst dbInst	= new DBInst("jdbc:mysql://192.168.116.153:3306/stock", "latupa", "latupa");
		
		String table_postfix = "";
		
		int mode = Integer.parseInt(args[1]);
		
		if (mode == 1) {
			if (!args[2].equals("-f")) {
				TransDetail.usage();
				System.exit(0);
			}
			
			String file_name = args[3];
			
			log.info("start proc trans detail file: " + file_name);
			
			TransDetail td = new TransDetail(table_postfix, dbInst);
			td.LogParse(file_name);
			td.StoreRecordsToDB();
			td.ScanRecordsToDB();
		}
		else if (mode == 2) {
			
			if (args.length != 6 && args.length != 5) {
				TransDetail.usage();
				System.exit(0);
			}
			
			if (!args[2].equals("-p")) {
				TransDetail.usage();
				System.exit(0);
			}
			
			table_postfix = args[3];
			TransDetail td = new TransDetail(table_postfix, dbInst);
			
			if (args[4].equals("-c")) {
				String record_info = args[5];
				
				System.out.println("table_postfix = " + table_postfix + " record:" + record_info);
				
				td.RecordParse(record_info);
				td.StoreRecordsToDB();
				td.ScanRecordsToDB();
			}
			else if (args[4].equals("-r")) {
				log.info("repair trans record for " + table_postfix);
				td.RepairTransRecord(table_postfix);
			}
			else {
				TransDetail.usage();
				System.exit(0);
			}
		}
		else {
			TransDetail.usage();
			System.exit(0);
		}
		
		log.info("proc finish!");
	}
	
	public static void usage() {
		System.out.println("usage as follow:");
		System.out.println("	-m mode 类型：1-日常；2-复盘");
		System.out.println("    	-f filename	交易明细文件");
		System.out.println("    	-p table_postfix 表名后缀，用于复盘时候独立表内容");
		System.out.println("    		-c record 复盘的交易明细：交易日期(yyyymmdd),股票代码,买入卖出(1-in),交易市场(sh/sz),交易价格(不需要输入)，交易金额(不需要输入)");
		System.out.println("    		-c record 复盘的交易明细：交易日期(yyyymmdd),股票代码,买入卖出(2-out),交易市场(sh/sz),交易价格(不需要输入)，交易股票数(不需要输入)");
		System.out.println("    		-r 修复所有数据");
	}
}
