package com.latupa.stock;

import java.util.Comparator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 比较器方法
 * 按时间从前到后排
 * @author Administrator
 */

class DateComparator implements Comparator<TransDetailRecord> {

	 public int compare(TransDetailRecord o1, TransDetailRecord o2) {
		 if (Integer.parseInt(o1.date) > Integer.parseInt(o2.date)) {
			 return 1;
		 }
		 else {
			 return -1;
		 }
	 }
}

/**
 * 交易记录
 */

public class TransDetailRecord {
	
	private static final Log log = LogFactory.getLog(TransDetailRecord.class);
	
	public static final String MARKET_SH	= "sh";	//上海证券市场标识
	public static final String MARKET_SZ	= "sz";	//深圳证券市场标识
	
	public static final int OPT_IN	= 1;	//买入
	public static final int OPT_OUT	= 2;	//卖出
	
	public String	date;	//交易日期
	public String	market;	//交易市场
	public String	code;	//股票代码
	public String	name;	//股票名称
	public int	opt;		//1---买入, 2---卖出
	public int	num;		//股票数
	public double	deal_price;		//股票成交价格
	public double	deal_amount;	//股票成交金额
	public double	deal_fee_comm;	//交易佣金
	public double	deal_fee_tax;	//交易印花税
	public double	deal_fee_trans;	//交易过户费
	public double	deal_fee_other;	//交易其他费用
	public double	deal_total;		//交易总金额（股票成交金额+交易各类费用）
	
	/**
	 * 合并相同的交易记录
	 * 注意：只能合并同一只股票连续相同的操作，例如买入、买入、买入、卖出、卖出，不能有交叉
	 * @param record
	 * @return
	 */
	public TransDetailRecord Merge(TransDetailRecord record) {
		
		if (!this.market.equals(record.market) ||
				!this.code.equals(record.code) ||
				this.opt != record.opt) {
			log.error("trans records are not same! market(" + this.market + "," + record.market + "), " +
					"code(" + this.code + "," + record.code + "), " +
					"opt(" + this.opt + "," + record.opt + ")");
			return null;
		}
		
		TransDetailRecord ret	= new  TransDetailRecord();
		
		ret.date	= this.date;  //日期以调用对象的为准
		ret.market	= this.market;
		ret.code	= this.code;
		ret.name	= this.name;
		ret.opt		= this.opt;
		ret.num		= this.num + record.num;
		ret.deal_price	= (this.deal_price * this.num + record.deal_price * record.num) / (this.num + record.num);
		ret.deal_amount	= this.deal_amount + record.deal_amount;
		ret.deal_fee_comm	= this.deal_fee_comm + record.deal_fee_comm;
		ret.deal_fee_tax	= this.deal_fee_tax + record.deal_fee_tax;
		ret.deal_fee_trans	= this.deal_fee_trans + record.deal_fee_trans;
		ret.deal_fee_other	= this.deal_fee_other + record.deal_fee_other;
		ret.deal_total		= this.deal_total + record.deal_total;
		
		return ret;
	}
	
	public String toString() {
		return "date:" + date + 
				", market:" + market +
				", code:" + code +
				", name:" + name +
				", opt:" + String.valueOf(opt) +
				", num:" + String.valueOf(num) +
				", deal_price:" + String.valueOf(deal_price) +
				", deal_amount:" + String.valueOf(deal_amount) +
				", deal_fee_comm:" + String.valueOf(deal_fee_comm) +
				", deal_fee_tax:" + String.valueOf(deal_fee_tax) +
				", deal_fee_trans:" + String.valueOf(deal_fee_trans) +
				", deal_fee_other:" + String.valueOf(deal_fee_other) +
				", deal_total:" + String.valueOf(deal_total);
	}
}
