package com.latupa.stock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * 交易记录
 * @author latupa
 *
 */
public class BTCTransRecord {
	
	public static final Log log = LogFactory.getLog(BTCTransRecord.class);
	
	public enum OPT {
		OPT_BUY,
		OPT_SELL
	};
	
	//数据库连接
	public DBInst dbInst;  
	
	public static final String BTC_TRANS_TABLE = "btc_trans";
	
	public BTCTransRecord(DBInst dbInst) {
		this.dbInst	= dbInst;
	}
	
	public void InitTable() {
		String sql = "create table if not exists " + BTC_TRANS_TABLE + 
				"(`time` DATETIME not null default '0000-00-00 00:00:00', " +
				"`opt` int NOT NULL default '0', " +
				"`quantity` double NOT NULL default '0', " +
				"`price` double NOT NULL default '0', " +
				"`amount` double NOT NULL default '0', " +
				"PRIMARY KEY (`time`)" +
				") ENGINE=InnoDB DEFAULT CHARSET=utf8";	
		
		dbInst.updateSQL(sql);
	}
	
	public void InsertTrans(String time, OPT opt, double quantity, double price) {
		
		String sql = "insert into " + BTC_TRANS_TABLE + 
				"(`time`, `opt`, `quantity`, `price`, `amount`) values ('" +
				time + "', " +
				opt + ", " +
				quantity + ", " +
				price + ", " +
				(quantity * price) + ")";
		log.info(sql);
		
		dbInst.updateSQL(sql);
	}

}
