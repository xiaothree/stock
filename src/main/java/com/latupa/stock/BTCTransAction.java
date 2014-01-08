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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BTCTransAction {
	
	public static final Log log = LogFactory.getLog(BTCTransAction.class);
	
	public BTCTransAction() {
		
	}
	
	public String DoSell(int position) {
		UserInfo user_info = null;
		while (user_info == null) {
			user_info	= ApiUserInfo();
		}
		
		double amount	= user_info.btc;
		
		double sell_amount	= amount * position / 10;
		
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		BTCTransAction btc_ta = new BTCTransAction();
	}

}
