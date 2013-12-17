package com.latupa.stock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * 布林线的返回结果
 * @author latupa
 *
 */
class BollRet {
	double upper;
	double mid;
	double lower;
	double bbi;
	
	public BollRet() {
	}
	
	public BollRet(BollRet bollret) {
		if (bollret != null) {
			this.upper	= bollret.upper;
			this.mid	= bollret.mid;
			this.lower	= bollret.lower;
			this.bbi	= bollret.bbi;
		}
	}
}

/**
 * macd的返回结果
 * @author latupa
 *
 */
class MacdRet {
	double diff;
	double dea;
	double macd;
	
	public MacdRet() {
	}
	
	public MacdRet(MacdRet macdret) {
		if (macdret != null) {
			this.diff	= macdret.diff;
			this.dea	= macdret.dea;
			this.macd	= macdret.macd;
		}
	}
}

/**
 * 均线的通用存储结构
 * @author latupa
 *
 */
class MaRet {
	double ma5;
	double ma10;
	double ma20;
	double ma30;
	double ma60;
	double ma120;
	
	public MaRet() {
		
	}
	
	public MaRet(MaRet maret) {
		if (maret != null) {
			this.ma5 = maret.ma5;
			this.ma10 = maret.ma10;
			this.ma20 = maret.ma20;
			this.ma30 = maret.ma30;
			this.ma60 = maret.ma60;
			this.ma120 = maret.ma120;
		}
	}
}

/**
 * 股票价格计算公式
 * @author latupa
 * TODO
 * 1. 用TreeMap来存储stock price，支持范围查找，优化查询性能
 *
 */
public class StockFunc {
	
	private static final Log log = LogFactory.getLog(StockPriceNew.class);

	//存储股票的价格数据{day, pr}
	public TreeMap<String, PriceRecord> stock_price = new TreeMap<String, PriceRecord>();
	
	//股票代码
	private String code;
	
	//股票市场
	private String market;
	
	//数据库连接
	public DBInst dbInst;  
	
	private StockPriceNew spn;
	
	public StockFunc(DBInst dbInst) {
		this.dbInst = dbInst;
		this.spn = new StockPriceNew(dbInst);
	}
	
	/**
	 * 加载股票的所有价格数据（非交易日的除外）
	 * @param code
	 * @param market
	 * @throws SQLException
	 */
	public void InitLoadStock(String code, String market) throws SQLException {
		this.code = code;
		this.market = market;
		
		log.info("load stock " + code + " of " + market);
		
		String table_name = spn.STOCK_PRICE_ED_TABLE_PRE + this.market;
		String sql = "select day as day, open as open, close as close from " + table_name + " where code = '" + this.code + "' and is_holiday = 0";
		log.debug(sql);
		ResultSet rs = dbInst.selectSQL(sql);
		
		while (rs.next()) {
			
			String day = rs.getString("day").replaceAll("-", "");
			
			PriceRecord pr = new PriceRecord();
			pr.open = rs.getDouble("open");
			pr.close = rs.getDouble("close");
			
			stock_price.put(day, pr);
		}
		
		rs.close();
	}
	
	/**
	 * 遍历显示股票所有的价格
	 */
	public void ShowLoadedStock() {
		
		for (String day : stock_price.keySet().toArray(new String[0])) {
			PriceRecord pr = stock_price.get(day);
			System.out.println("day:" + day + ", open:" + pr.open + ", close:" + pr.close);
		}
		System.out.println("stock " + code + " of " + market + " is total " + stock_price.size() + " records");
	}
	
	/**
	 * 清空股票价格
	 */
	public void ClearLoadedStock() {
		log.info("clear stock " + code + " of " + market);
		stock_price.clear();
	}
	
	/**
	 * 标准差，为计算布林线使用
	 * @param p_day
	 * @return
	 */
	public double std(String p_day, int count, double average) {
		
		double sum = 0;
		int i = 0;
		for (String day : stock_price.headMap(p_day, true).descendingKeySet().toArray(new String[0])) {
			sum += (stock_price.get(day).close - average) * (stock_price.get(day).close - average);
			i++;
			if (i == count) {
				break;
			}
		}
		
		sum /= (count - 1);
		
		return Math.sqrt(sum);
	}
	
	
	
	
	/**
	 * 计算布林线相关指标
	 * @param p_day
	 * @return
	 */
	public BollRet boll(String p_day) {
		final int n = 26;
		final int p = 2;
		
		BollRet br = new BollRet();
		TreeMap<Integer, Double> maret_map;
		
		//先计算该周期的均值
		ArrayList<Integer> mas = new ArrayList<Integer>();
		mas.add(new Integer(n));
		maret_map = ma(p_day, mas, 0);
		br.mid = maret_map.get(new Integer(n)).doubleValue();
		if (br.mid == 0) {
			log.debug("return null for mid is 0");
			return null;
		}
		
		br.upper = br.mid + p * std(p_day, n, br.mid);
		br.lower = br.mid - p * std(p_day, n, br.mid);
		
		mas.clear();
		mas.add(new Integer(3));
		mas.add(new Integer(6));
		mas.add(new Integer(12));
		mas.add(new Integer(24));
		maret_map = ma(p_day, mas, 0);
		
		br.bbi = (maret_map.get(new Integer(3)).doubleValue() +
				maret_map.get(new Integer(6)).doubleValue() +
				maret_map.get(new Integer(12)).doubleValue() +
				maret_map.get(new Integer(24)).doubleValue()) / 4;
		
		log.debug("boll, mid:" + br.mid + ", upper:" + br.upper + ", lower:" + br.lower + ", bbi:" + br.bbi);
		
		return br;
	}
	
	/**
	 * ema
	 * @param x
	 * @param n
	 * @return
	 */
	private double ema(ArrayList<Double> x, int n, int size) {
//		log.debug("n:" + n + "->" + x.get(n - 1));
		if (n == 1) {
			return x.get(n - 1);
		}
		else {
			return (2 * x.get(n - 1) + (size - 1) * ema(x, n - 1, size)) / (size + 1);
		}
	}
	
	private ArrayList<Double> GetPriceArray(String p_day, int ndays) {
		ArrayList<Double> close_list = new ArrayList<Double>();
		 
		double last_close = 0.0;
		int count = 0;
		//先以时间降序写入到数组中
		for (String day : stock_price.headMap(p_day, true).descendingKeySet().toArray(new String[0])) {
			PriceRecord pr = stock_price.get(day);
			//保留三位小数
//			BigDecimal bg = new BigDecimal(pr.close);
//	        pr.close = bg.setScale(4, BigDecimal.ROUND_HALF_UP).doubleValue();
			close_list.add(count, pr.close);
			count++;
			last_close = pr.close;
			if (count == ndays) {
				break;
			}
		}
		
		while (count < ndays) {
			close_list.add(count, last_close);
			count++;
		}
		
		//转换成时间和数组位置成正序
		Collections.reverse(close_list);
		
		return close_list;
	}
	
	/**
	 * MACD计算公式
	 * y=ema(x,n), y=[2*x+(n-1)y']/(n+1),其中y'表示上一周期y的值
	 * @return
	 * @throws ParseException 
	 */
	public MacdRet macd(String p_day) throws ParseException {
		int p_long = 26;
		int p_short = 13;
		int p_m = 9;
		
		ArrayList<Double> close_list_long = GetPriceArray(p_day, p_long);
		ArrayList<Double> close_list_short = GetPriceArray(p_day, p_short);
		
		MacdRet mr = new MacdRet();
		
		double a13 = ema(close_list_short, p_short, p_short);
		double a26 = ema(close_list_long, p_long, p_long);
		
		mr.diff = a13 - a26;
		log.debug("macd, diff:" + mr.diff + ", ema13:" + a13 + ", ema26:" + a26);
		
		//计算9天的DIFF，为计算DEA准备数据（需要剔除掉非交易日的数据）！！！！
		ArrayList<Double> diff_list = new ArrayList<Double>();
		DateFormat format = new SimpleDateFormat("yyyyMMdd");
		Date date = format.parse(p_day);
		int count = 0;
		int i = 0;
		double last_diff = 0;
		while (true) {
			long tm = (date.getTime() / 1000 - i * 24 * 60 * 60) * 1000;
			Date tmp_date = new Date(tm);
			String tmp_day = format.format(tmp_date);
			
			if (stock_price.containsKey(tmp_day)) {
				close_list_long = GetPriceArray(tmp_day, p_long);
				close_list_short = GetPriceArray(tmp_day, p_short);
				
				a13 = ema(close_list_short, p_short, p_short);
				a26 = ema(close_list_long, p_long, p_long);
				
				double diff = a13 - a26;
				log.debug(tmp_day + ":" + diff);
				//保留三位小数
//				BigDecimal bg = new BigDecimal(diff);
//		        diff = bg.setScale(2, BigDecimal.ROUND_DOWN).doubleValue();
				diff_list.add(diff);
				last_diff = diff;
				
				count++;
				if (count >= p_m || stock_price.firstKey().equals(tmp_day)) {
					break;
				}
			}
			i++;
		}
		
		while (count < p_m) {
			log.debug("add" + last_diff);
			diff_list.add(last_diff);
			count++;
		}
		
		Collections.reverse(diff_list);
		mr.dea = ema(diff_list, p_m, p_m);
		
		mr.macd = 2 * (mr.diff - mr.dea);
		log.debug("macd, diff:" + mr.diff + ", dea:" + mr.dea + ", macd:" + mr.macd);
		
		return mr;
	}
	
	/**
	 * 均线
	 * @param p_day yyyymmdd 指定某天的均线
	 * @param days 均线周期列表（按照从小到大排序）
	 * @param pre_days 计算p_day之前几天的数据，如果为0，表示p_day当天
	 * @return
	 */
	public TreeMap<Integer, Double> ma(String p_day, ArrayList<Integer> days, int pre_days) {
		
		double sum = 0;
		int count = 0;
		int i = 0;  //处理到的均线坐标
		
		TreeMap<Integer, Double> maret_map = new TreeMap<Integer, Double>();
		
		
		for (String day : stock_price.headMap(p_day, true).descendingKeySet().toArray(new String[0])) {
			
			if (pre_days > 0) {
				pre_days--;
				continue;
			}
			
			PriceRecord pr = stock_price.get(day);
			sum += pr.close;
			count++;
			
			// 循环处理每个均线周期
			if (count == days.get(i)) {
				maret_map.put(count, sum / count);
				i++;
				// 所有给定周期已经计算完，则退出
				if (i == days.size()) {
					break;
				}
			}
		}
		
		//补全时间不足的返回结果
		while (i < days.size()) {
			maret_map.put(days.get(i), 0.0);
			i++;
		}
		
		for (Integer day : maret_map.keySet()) {
			log.debug(day.intValue() + "->" + maret_map.get(day));
		}
		
		return maret_map;
	}
	
	/**
	 * 
	 * @param p_day yyyymmdd 指定某天
	 * @return
	 */
	public Boolean IsValid(String p_day) {
		if (stock_price.containsKey(p_day)) {
			return true;
		}
		else {
			return false;
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		DBInst dbInst	= new DBInst("jdbc:mysql://192.168.153.148:3306/stock_new", "latupa", "latupa");
		StockFunc sf = new StockFunc(dbInst);
		try {
			sf.InitLoadStock("600000", "sh");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//sf.ShowLoadedStock();
		String day = "20000110";
		ArrayList<Integer> mas = new ArrayList<Integer>();
		mas.add(new Integer(5));
		mas.add(new Integer(10));
		mas.add(new Integer(20));
		mas.add(new Integer(30));
		mas.add(new Integer(60));
		mas.add(new Integer(120));
		
		sf.ma(day, mas, 0);
		
		//long time1 = System.currentTimeMillis();
		BollRet bollret;
		//for (int i = 0; i < 100000; i++) {
			bollret = sf.boll(day);
		//}
		//System.out.println("time cose:" + Long.toString(time2 - time1));
			
		try {
			MacdRet macdret = sf.macd(day);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		sf.ClearLoadedStock();
	}

}
