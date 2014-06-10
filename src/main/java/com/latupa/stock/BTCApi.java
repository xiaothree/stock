package com.latupa.stock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TreeMap;

import net.sf.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


class Ticker {
	public static final Log log = LogFactory.getLog(Ticker.class);
	
	double bid;	//买入价
	double ask;	//卖出价
	String instrument;
	String time;
	public void Show() {
		log.info("instrument:" + instrument + ", time:" + time.toString() + ", bid:" + bid + ", ask:" + ask);
	}
}

class UserInfo {
	public static final Log log = LogFactory.getLog(UserInfo.class);
	
	double btc;
	double ltc;
	double cny;
	double btc_freezed;
	double ltc_freezed;
	double cny_freezed;
	public UserInfo() {
		this.btc = 0;
		this.ltc = 0;
		this.cny = 0;
		this.btc_freezed = 0;
		this.ltc_freezed = 0;
		this.cny_freezed = 0;
	}
	public void Show() {
		log.info("btc:" + btc + ", ltc:" + ltc + ", cny:" + cny + ", fbtc:" + btc_freezed + ", fltc:" + ltc_freezed + ", fcny:" + cny_freezed);
	}
}

class TradeRet {
	public static final Log log = LogFactory.getLog(TradeRet.class);
	
	public enum STATUS {
		WAIT,	//未成交
		CANCEL,	//委托取消
		PARTER,	//部分成交
		TOTAL;	//完全成交
	};
	
	String order_id;
	STATUS status;
	String symbol;
	String type;
	double price;
	double amount;
	double deal_amount;
	double avg_price;
	
	public TradeRet() {
		this.deal_amount	= 0;
		this.avg_price		= 0;
	}
	
	public void Show() {
		log.info("order_id:" + order_id + ", status:" + status + ", symbol:" + symbol + ", type:" + type + ", price:" + price + ", amount:" + amount + ", deal_amount:" + deal_amount + ", avg_price:" + avg_price);
	}
}

/**
 * 行情、交易相关Api
 * @author latupa
 *
 * TODO
 */
public class BTCApi {
	
	public static final Log log = LogFactory.getLog(BTCApi.class);
	
	public static final String URL_SANDBOX  = "http://api-sandbox.oanda.com";
	public static final String URL_PRACTICE = "https://api-fxpractice.oanda.com";
	public static final String URL_TRADE	= "https://api-fxtrade.oanda.com";
	
	public String token;
	public String partner;
	
	//数据库配置文件
	public static final String ACTION_FILE = "token.info";
	
	//交易委托的差价
	public static final int TRADE_DIFF = 3;
	
	public static final int ERROR_CODE_10009 = 10009; //订单不存在

	public BTCApi() {
		ReadInfo();
	}
	
	/**
     * 向指定URL发送GET方法的请求
     * 
     * @param url
     *            发送请求的URL
     * @param param
     *            请求参数，请求参数应该是 name1=value1&name2=value2 的形式。
     * @return URL 所代表远程资源的响应结果
     */
    public String sendGet(String url, String param) {
        String result = "";
        BufferedReader in = null;
        try {
            String urlNameString = url + "?" + param;
            URL realUrl = new URL(urlNameString);
            // 打开和URL之间的连接
            URLConnection connection = realUrl.openConnection();
            // 设置通用的请求属性
            connection.setRequestProperty("accept", "*/*");
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("user-agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            connection.setRequestProperty("Authorization", "Bearer " + this.token);
            connection.setConnectTimeout(5000);
            // 建立实际的连接
            connection.connect();
            // 获取所有响应头字段
//            Map<String, List<String>> map = connection.getHeaderFields();
            // 遍历所有的响应头字段
//            for (String key : map.keySet()) {
//                System.out.println(key + "--->" + map.get(key));
//            }
            // 定义 BufferedReader输入流来读取URL的响应
            in = new BufferedReader(new InputStreamReader(
                    connection.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result += line;
            }
        } catch (SocketTimeoutException e) {
        	log.error("发送GET请求超时！", e);
        } catch (Exception e) {
            log.error("发送GET请求出现异常！", e);
        }
        // 使用finally块来关闭输入流
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e2) {
            	log.error("关闭输入流出现异常！", e2);
            }
        }
        return result;
    }

    /**
     * 向指定 URL 发送POST方法的请求
     * 
     * @param url
     *            发送请求的 URL
     * @param param
     *            请求参数，请求参数应该是 name1=value1&name2=value2 的形式。
     * @return 所代表远程资源的响应结果
     */
    public String sendPost(String url, String param) {
        PrintWriter out = null;
        BufferedReader in = null;
        String result = "";
        try {
            URL realUrl = new URL(url);
            // 打开和URL之间的连接
            URLConnection conn = realUrl.openConnection();
            // 设置通用的请求属性
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            conn.setRequestProperty("Authorization", "Bearer " + this.token);
            // 发送POST请求必须设置如下两行
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // 获取URLConnection对象对应的输出流
            out = new PrintWriter(conn.getOutputStream());
            // 发送请求参数
            out.print(param);
            // flush输出流的缓冲
            out.flush();
            // 定义BufferedReader输入流来读取URL的响应
            in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result += line;
            }
        } catch (Exception e) {
            log.error("发送 POST请求出现异常！", e);
        }
        //使用finally块来关闭输出流、输入流
        finally{
            try{
                if(out!=null){
                    out.close();
                }
                if(in!=null){
                    in.close();
                }
            }
            catch(IOException ex){
            	log.error("关闭输出、输入流出现异常！", ex);
            }
        }
        return result;
    }    
	
	private String md5(String plainText) {
        try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(plainText.getBytes());
			byte b[] = md.digest();
			int i;
			StringBuffer buf = new StringBuffer("");
			for (int offset = 0; offset < b.length; offset++) {
				i = b[offset];
				if (i < 0)
				i += 256;
				if (i < 16)
				buf.append("0");
				buf.append(Integer.toHexString(i));
			}
			    
		    return buf.toString();
		    //System.out.println("result: " + buf.toString());// 32位的加密
			//System.out.println("result: " + buf.toString().substring(8, 24));// 16位的加密
        } catch (NoSuchAlgorithmException e) {
        	log.error("calc md5 failed for str:" + plainText, e);
        	return null;
        }
	}
	
	public String HttpPost(String url, TreeMap<String, String> para) {
		String str_para = "";
		for (String key : para.keySet().toArray(new String[0])) {
			String value	= para.get(key);
			str_para	+= key + "=" + value + "&";
		}
		String ret = str_para.substring(0, str_para.length() - 1);
		log.debug("para:" + ret);
		
		return ret;
	}
	
	/**
	 * 随机生成价格值
	 * @return
	 */
//	public double FetchMock() {
//		Random random = new Random();
//		boolean is_positive = random.nextBoolean();
//		double wave	= random.nextDouble() * 50;
//		double next_price;
//		if (this.price_mock == 0) {
//			next_price	= is_positive ? 4000 + wave : 4000 - wave;
//		}
//		else {
//			next_price	= is_positive ? this.price_mock + wave : this.price_mock - wave;
//		}
//		
//		this.price_mock	= next_price;
//		return this.price_mock;
//	}
	
	public double FetchRTWeb() throws IOException {
		URL url = null;
		double last = 0;
		log.info("start fetch web");
		try {
			
            url = new URL("https://www.okcoin.com/market.do");
            
    		InputStream in = url.openStream();
    		BufferedReader bin = new BufferedReader(new InputStreamReader(in, "utf8"));
    		String s;
    		log.info("hello");
    		if ((s = bin.readLine()) != null) {
    			log.info("readline");
    			log.info(s);
    			if (s.equals("最新价格：")) {
    				System.out.println(s);
    			}
    		}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return last;
	}
	
	/**
	 * 获取当前数据
	 * @return
	 */
	public double FetchRT() {
		
		URL url = null;
		double last = 0;
		try {
			url = new URL("https://www.okcoin.com/api/ticker.do");
		
			InputStream in = url.openStream();
			BufferedReader bin = new BufferedReader(new InputStreamReader(in, "utf8"));
			String s = null;
			if ((s = bin.readLine()) != null) {
//				System.out.println(s);
				
				//触发了接口防抓取
				if (!s.startsWith("{")) {
					log.info("fetch failed!");
					return last;
				}
				
				try {
					JSONObject jsonObj = JSONObject.fromObject(s);
					if (jsonObj.has("ticker")) {
						String s1 = jsonObj.getString("ticker");
						JSONObject jsonObj1 = JSONObject.fromObject(s1);
						if (jsonObj1.has("last")) {
							last = jsonObj1.getDouble("last");
						}
						else {
							log.error("key \"last\" error !" + s1);
							return last;
						}
					}
					else {
						log.error("key \"ticker\" error !" + s);
						return last;
					}
						
					//System.out.println("last:" + last);
				}
				catch (Exception e) {
					log.error("parse json failed! json:" + s, e);
				}
			}
			else {
				log.error("request return null! url:" + url.toString());
			}
			
			bin.close();
		}
		catch (Exception e) {
			log.error("request failed! url:" + url.toString(), e);
		}
		
		return last;
	}
	
	/**
	 * 获取行情接口
	 * @return
	 */
	public Ticker ApiTicker() {
		
		String ret = sendGet(URL_PRACTICE, "instruments=EUR_USD");
		
		if (ret == "") {
			log.error("call failed! " + URL_PRACTICE);
			return null;
		}
		
		try {
			JSONObject jsonObj = JSONObject.fromObject(ret);
			if (jsonObj.has("prices")) {
				Ticker ticker	= new Ticker();
				JSONObject jsonObj1	= jsonObj.getJSONObject("prices");
				
				ticker.instrument	= jsonObj1.getString("instrument");
				ticker.time	= jsonObj1.getString("time");
				ticker.bid	= jsonObj1.getDouble("bid");
				ticker.ask	= jsonObj1.getDouble("ask");
				
				return ticker;
			}
			else if (jsonObj.has("code")){
				int error_code = jsonObj.getInt("code");
				String error_info = jsonObj.getString("message");
				log.error("parse json failed! error_code:" + error_code + ", error_info:" + error_info);
			}
		}
		catch (Exception e) {
			log.error("parse json failed! json:" + ret, e);
		}
		
		return null;
	}
	
	/**
	 * 获取订单状态
	 * @param order_id
	 */
	public TradeRet ApiGetOrder(String order_id) {
		String url = "https://www.okcoin.com/api/getorder.do";
		TreeMap<String, String> para = new TreeMap<String, String>();
		para.put("partner", this.partner);
		para.put("order_id", order_id);
		para.put("symbol", "btc_cny");
		
		String ret	= HttpPost(url, para);
		
		if (ret == "") {
			log.error("call failed!" + url);
			return null;
		}
		
		try {
			JSONObject jsonObj = JSONObject.fromObject(ret);
			if (jsonObj.has("result")) {
				String s1 = jsonObj.getString("result");
				if (s1.equals("true")) {
					TradeRet trade_ret	= new TradeRet();
					JSONObject jsonObj1	= jsonObj.getJSONArray("orders").getJSONObject(0);
					
					trade_ret.amount	= jsonObj1.getDouble("amount");
					trade_ret.avg_price	= jsonObj1.getDouble("avg_rate");
					trade_ret.deal_amount	= jsonObj1.getDouble("deal_amount");
					trade_ret.price	= jsonObj1.getDouble("rate");
					trade_ret.order_id	= Long.toString(jsonObj1.getLong("orders_id"));
					trade_ret.type	= jsonObj1.getString("type");
					int status	= jsonObj1.getInt("status");
					if (status == 0 || status == 3) {
						trade_ret.status	= TradeRet.STATUS.WAIT;
					}
					else if (status == 2) {
						trade_ret.status	= TradeRet.STATUS.TOTAL;
					}
					else if (status == -1) {
						trade_ret.status	= TradeRet.STATUS.CANCEL;
					}
					else if (status == 1) {
						trade_ret.status	= TradeRet.STATUS.PARTER;
					}
					
					return trade_ret;
				}
				else {
					log.error("parse json failed! json:" + ret);
				}
			}
			else {
				log.error("parse json failed! json:" + ret);
			}
		}
		catch (Exception e) {
			log.error("parse json failed! json:" + ret, e);
		}
		return null;
	}
	
	/**
	 * 取消订单
	 * @param order_id
	 * @return
	 */
	public boolean ApiCancelOrder(String order_id) {
		String url = "https://www.okcoin.com/api/cancelorder.do";
		TreeMap<String, String> para = new TreeMap<String, String>();
		para.put("partner", this.partner);
		para.put("order_id", order_id);
		para.put("symbol", "btc_cny");
		
		String ret	= HttpPost(url, para);
		
		if (ret == "") {
			log.error("call failed!" + url );
			return false;
		}
		
		try {
			JSONObject jsonObj = JSONObject.fromObject(ret);
			if (jsonObj.has("result")) {
				String s1 = jsonObj.getString("result");
				if (s1.equals("true")) {
					return true;
				}
				else {
					int error_code = jsonObj.getInt("errorCode");
					if (error_code == ERROR_CODE_10009) {//如果是订单不存在，说明已经委托成功了，不允许撤单
						return true;
					}
					else {
						log.error("parse json failed! json:" + ret);
					}
				}
			}
			else {
				log.error("parse json failed! json:" + ret);
			}
		}
		catch (Exception e) {
			log.error("parse json failed! json:" + ret, e);
		}
		return false;
	}
	
	/**
	 * 下单操作
	 * @param type "buy/sell"
	 * @param price 价格
	 * @param amount 交易量
	 * @return
	 */
	public String ApiTrade(String type, double price, double amount) {
		String url = "https://www.okcoin.com/api/trade.do";
		TreeMap<String, String> para = new TreeMap<String, String>();
		para.put("partner", this.partner);
		para.put("symbol", "btc_cny");
		para.put("type", type);
		para.put("rate", Double.toString(price));
		para.put("amount", Double.toString(amount));
		
		String ret	= HttpPost(url, para);
		
		if (ret == "") {
			log.error("call failed!" + url );
			return null;
		}
		
		try {
			JSONObject jsonObj = JSONObject.fromObject(ret);
			if (jsonObj.has("result")) {
				String s1 = jsonObj.getString("result");
				if (s1.equals("true")) {
					String order_id	= Long.toString(jsonObj.getLong("order_id"));
					return order_id;
				}
				else {
					log.error("parse json failed! json:" + ret);
				}
			}
			else {
				log.error("parse json failed! json:" + ret);
			}
		}
		catch (Exception e) {
			log.error("parse json failed! json:" + ret, e);
		}
		return null;
	}
	
	/**
	 * 获取用户信息
	 */
	public UserInfo ApiUserInfo() {
		String url = "https://www.okcoin.com/api/userinfo.do";
		TreeMap<String, String> para = new TreeMap<String, String>();
		para.put("partner", this.partner);
		
		String ret	= HttpPost(url, para);
		
		if (ret == "") {
			log.error("call failed!" + url );
			return null;
		}
		
		try {
			JSONObject jsonObj = JSONObject.fromObject(ret);
			if (jsonObj.has("result")) {
				String s1 = jsonObj.getString("result");
				if (s1.equals("true")) {
					UserInfo user_info	= new UserInfo();
					
					JSONObject jsonObj1	= jsonObj.getJSONObject("info").getJSONObject("funds").getJSONObject("free");
					user_info.btc	= jsonObj1.getDouble("btc");
					user_info.cny	= jsonObj1.getDouble("cny");
					user_info.ltc	= jsonObj1.getDouble("ltc");
					
					JSONObject jsonObj2	= jsonObj.getJSONObject("info").getJSONObject("funds").getJSONObject("freezed");
					user_info.btc_freezed	= jsonObj2.getDouble("btc");
					user_info.cny_freezed	= jsonObj2.getDouble("cny");
					user_info.ltc_freezed	= jsonObj2.getDouble("ltc");
					
					return user_info;
				}
				else {
					log.error("parse json failed! json:" + ret);
				}
			}
			else {
				log.error("parse json failed! json:" + ret);
			}
		}
		catch (Exception e) {
			log.error("parse json failed! json:" + ret, e);
		}
		
		return null;
	}
	
	/**
	 * 读取配置交易配置信息
	 */
	public void ReadInfo() {
		
		try {
			InputStream fis			= BTCApi.class.getClassLoader().getResourceAsStream(ACTION_FILE);
	        InputStreamReader isr	= new InputStreamReader(fis, "utf8");
	        BufferedReader br		= new BufferedReader(isr);
	        
	        String line = br.readLine();
	        
	        br.close();
	        isr.close();
	        fis.close();
	        
	        if (line != null) {
	        	this.token = line;
	        	return;
	        }
	        else {
	        	log.error("read " + ACTION_FILE + " is null!");
	        	return;
	        }
		}
		catch (Exception e) {
			log.error("read " + ACTION_FILE + " failed!", e);
			return;
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		BTCApi btc_api = new BTCApi();
		Ticker ticker = btc_api.ApiTicker();
		ticker.toString();
//		String order_id = btc_api.ApiTrade("buy", 4955, 1);
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		boolean ret = btc_api.ApiCancelOrder(order_id);
//		System.out.println("ret:" + ret);
//		btc_api.ApiGetOrder(order_id);
	}
}

