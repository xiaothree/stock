package com.latupa.stock;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.sf.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


class UserInfo {
	double btc;
	double ltc;
	double cny;
	double btc_freezed;
	double ltc_freezed;
	double cny_freezed;
	public void Show() {
		System.out.println("btc:" + btc + ", ltc:" + ltc + ", cny:" + cny + ", fbtc:" + btc_freezed + ", fltc:" + ltc_freezed + ", fcny:" + cny_freezed);
	}
}

public class BTCTransAction {
	
	public static final Log log = LogFactory.getLog(BTCTransAction.class);
	
	public String secretKey;
	public String partner;
	
	//数据库配置文件
	public static final String ACTION_FILE_DIR = "src/main/resources/";
	public static final String action_file = "action.info";
	
	public UserInfo userinfo = new UserInfo();

	public BTCTransAction() {
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
            // 建立实际的连接
            connection.connect();
            // 获取所有响应头字段
            Map<String, List<String>> map = connection.getHeaderFields();
            // 遍历所有的响应头字段
            for (String key : map.keySet()) {
                System.out.println(key + "--->" + map.get(key));
            }
            // 定义 BufferedReader输入流来读取URL的响应
            in = new BufferedReader(new InputStreamReader(
                    connection.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                result += line;
            }
        } catch (Exception e) {
            System.out.println("发送GET请求出现异常！" + e);
            e.printStackTrace();
        }
        // 使用finally块来关闭输入流
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
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
            System.out.println("发送 POST 请求出现异常！"+e);
            e.printStackTrace();
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
                ex.printStackTrace();
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
        	e.printStackTrace();
        	return null;
        }
	}
	
	public String HttpPost(String url, TreeMap<String, String> para) {
		String str_para = null;
		for (String key : para.keySet().toArray(new String[0])) {
			String value	= para.get(key);
			str_para	= key + "=" + value + "&";
		}
		String str_para_tmp	= str_para.substring(0, str_para.length() - 1) + this.secretKey;
		System.out.println("para:" + str_para_tmp);
		
		String md5	= md5(str_para_tmp).toUpperCase();
		System.out.println("md5:" + md5);
		
		String ret	= sendPost(url, str_para + "sign=" + md5);
		System.out.println("ret:" + ret);
		
		return ret;
	}
	
	/**
	 * 获取用户信息
	 */
	public void ApiUserInfo() {
		String url = "https://www.okcoin.com/api/userinfo.do";
		TreeMap<String, String> para = new TreeMap<String, String>();
		para.put("partner", this.partner);
		
		String ret	= HttpPost(url, para);
		
		int err_code = 0;
		
		try {
			JSONObject jsonObj = JSONObject.fromObject(ret);
			if (jsonObj.has("result")) {
				String s1 = jsonObj.getString("result");
				if (s1.equals("true")) {
					JSONObject jsonObj1	= jsonObj.getJSONObject("info").getJSONObject("funds").getJSONObject("free");
					this.userinfo.btc	= jsonObj1.getDouble("btc");
					this.userinfo.cny	= jsonObj1.getDouble("cny");
					this.userinfo.ltc	= jsonObj1.getDouble("ltc");
					
					JSONObject jsonObj2	= jsonObj.getJSONObject("info").getJSONObject("funds").getJSONObject("freezed");
					this.userinfo.btc_freezed	= jsonObj2.getDouble("btc");
					this.userinfo.cny_freezed	= jsonObj2.getDouble("cny");
					this.userinfo.ltc_freezed	= jsonObj2.getDouble("ltc");
					
					this.userinfo.Show();
				}
				else {
					err_code	= jsonObj.getInt("errorCode");
				}
			}
			else {
				err_code	= 9990;
			}
		}
		catch (Exception e) {
			log.error("parse json failed! json:" + ret, e);
			err_code	= 9991;
		}
		
		System.out.println("err_code=" + err_code);
	}
	
	/**
	 * 读取配置交易配置信息
	 */
	public void ReadInfo() {
		try {
			FileInputStream fis		= new FileInputStream(ACTION_FILE_DIR + action_file);
	        InputStreamReader isr	= new InputStreamReader(fis, "utf8");
	        BufferedReader br		= new BufferedReader(isr);
	        
	        String line = br.readLine();
	        
	        br.close();
	        isr.close();
	        fis.close();
	        
	        if (line != null) {
	        	String arrs[] = line.split(" ");
	        	
	        	this.secretKey	= arrs[0];
	        	this.partner	= arrs[1];
	        	
	        	return;
	        }
	        else {
	        	log.error("read " + ACTION_FILE_DIR + action_file + " is null!");
	        	return;
	        }
		}
		catch (Exception e) {
			log.error("read " + ACTION_FILE_DIR + action_file + " failed!", e);
			return;
		}
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		BTCTransAction btc_ta = new BTCTransAction();
		btc_ta.ApiUserInfo();
	}

}
