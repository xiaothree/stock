package com.latupa.stock;

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
 * BTC价格计算公式
 * @author latupa
 */
public class BTCFunc {
	
	private static final Log log = LogFactory.getLog(StockPriceNew.class);
	
	/**
	 * 标准差，为计算布林线使用
	 * @param record_map 数据映射
	 * @param p_time 指定某个时间的标准差
	 * @return
	 */
	public double std(TreeMap<String, BTCBasicRecord> record_map, String p_time, int count, double average) {
		
		double sum = 0;
		int i = 0;
		for (String time : record_map.headMap(p_time, true).descendingKeySet().toArray(new String[0])) {
			sum += (record_map.get(time).close - average) * (record_map.get(time).close - average);
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
	 * @param record_map 数据映射
	 * @param p_time 指定某个时间的布林线
	 * @return
	 */
	public BollRet boll(TreeMap<String, BTCBasicRecord> record_map, String p_time) {
		final int n = 26;
		final int p = 2;
		
		BollRet br = new BollRet();
		TreeMap<Integer, Double> maret_map;
		
		//先计算该周期的均值
		ArrayList<Integer> mas = new ArrayList<Integer>();
		mas.add(new Integer(n));
		maret_map = ma(record_map, p_time, mas, 0);
		br.mid = maret_map.get(new Integer(n)).doubleValue();
		if (br.mid == 0) {
			log.debug("return null for mid is 0");
			return null;
		}
		
		br.upper = br.mid + p * std(record_map, p_time, n, br.mid);
		br.lower = br.mid - p * std(record_map, p_time, n, br.mid);
		
		mas.clear();
		mas.add(new Integer(3));
		mas.add(new Integer(6));
		mas.add(new Integer(12));
		mas.add(new Integer(24));
		maret_map = ma(record_map, p_time, mas, 0);
		
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
	
	
	/**
	 * @param record_map 数据映射
	 * @param p_time 指定某个时间的布林线
	 * @param pre_cycles 获取pre_cycles的数据
	 * @return
	 */
	private ArrayList<Double> GetPriceArray(TreeMap<String, BTCBasicRecord> record_map, String p_time, int pre_cycles) {
		ArrayList<Double> close_list = new ArrayList<Double>();
		 
		double last_close = 0.0;
		int count = 0;
		//先以时间降序写入到数组中
		for (String time : record_map.headMap(p_time, true).descendingKeySet().toArray(new String[0])) {
			BTCBasicRecord record = record_map.get(time);
			//保留三位小数
//			BigDecimal bg = new BigDecimal(pr.close);
//	        pr.close = bg.setScale(4, BigDecimal.ROUND_HALF_UP).doubleValue();
			close_list.add(count, record.close);
			count++;
			last_close = record.close;
			if (count == pre_cycles) {
				break;
			}
		}
		
		while (count < pre_cycles) {
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
	 * @param record_map 数据映射
	 * @param p_time 指定某个时间的布林线
	 * @return
	 * @throws ParseException
	 */
	public MacdRet macd(TreeMap<String, BTCBasicRecord> record_map, String p_time, int cycle_data) throws ParseException {
		int p_long = 26;
		int p_short = 13;
		int p_m = 9;
		
		ArrayList<Double> close_list_long = GetPriceArray(record_map, p_time, p_long);
		ArrayList<Double> close_list_short = GetPriceArray(record_map, p_time, p_short);
		
		MacdRet mr = new MacdRet();
		
		double a13 = ema(close_list_short, p_short, p_short);
		double a26 = ema(close_list_long, p_long, p_long);
		
		mr.diff = a13 - a26;
		log.debug("macd, diff:" + mr.diff + ", ema13:" + a13 + ", ema26:" + a26);
		
		//计算9天的DIFF，为计算DEA准备数据（需要剔除掉非交易日的数据）！！！！
		ArrayList<Double> diff_list = new ArrayList<Double>();
		DateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
		Date date = format.parse(p_time);
		int count = 0;
		int i = 0;
		double last_diff = 0;
		while (true) {
			long tm = (date.getTime() / 1000 - i * cycle_data) * 1000;
			Date tmp_date = new Date(tm);
			String tmp_time = format.format(tmp_date);
			
			if (record_map.containsKey(tmp_time)) {
				close_list_long = GetPriceArray(record_map, tmp_time, p_long);
				close_list_short = GetPriceArray(record_map, tmp_time, p_short);
				
				a13 = ema(close_list_short, p_short, p_short);
				a26 = ema(close_list_long, p_long, p_long);
				
				double diff = a13 - a26;
				log.debug(tmp_time + ":" + diff);
				//保留三位小数
//				BigDecimal bg = new BigDecimal(diff);
//		        diff = bg.setScale(2, BigDecimal.ROUND_DOWN).doubleValue();
				diff_list.add(diff);
				last_diff = diff;
				
				count++;
				if (count >= p_m || record_map.firstKey().equals(tmp_time)) {
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
	 * @param record_map 数据映射
	 * @param p_time 指定某个时间的均线
	 * @param cycles 均线周期列表（按照从小到大排序）
	 * @param pre_cycles 计算pre_cycles之前的数据，如果为0，表示time当时
	 * @return
	 */
	public TreeMap<Integer, Double> ma(TreeMap<String, BTCBasicRecord> record_map, String p_time, ArrayList<Integer> cycles, int pre_cycles) {
		
		double sum = 0;
		int count = 0;
		int i = 0;  //处理到的均线坐标
		
		TreeMap<Integer, Double> maret_map = new TreeMap<Integer, Double>();
		
		
		for (String time : record_map.headMap(p_time, true).descendingKeySet().toArray(new String[0])) {
			
			if (pre_cycles > 0) {
				pre_cycles--;
				continue;
			}
			
			BTCBasicRecord record = record_map.get(time);
			sum += record.close;
			count++;
			
			// 循环处理每个均线周期
			if (count == cycles.get(i)) {
				maret_map.put(count, sum / count);
				i++;
				// 所有给定周期已经计算完，则退出
				if (i == cycles.size()) {
					break;
				}
			}
		}
		
		//补全时间不足的返回结果
		while (i < cycles.size()) {
			maret_map.put(cycles.get(i), 0.0);
			i++;
		}
		
		for (int cycle : maret_map.keySet()) {
			log.debug(cycle + "->" + maret_map.get(cycle));
		}
		
		return maret_map;
	}
}
