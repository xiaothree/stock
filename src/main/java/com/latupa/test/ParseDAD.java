package com.latupa.test;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ParseDAD {

	 public static float toFloat(byte[] b) { 
         // 4 bytes
         int accum = 0; 
         for ( int shiftBy = 0; shiftBy < 4; shiftBy++ ) { 
                 accum |= (b[shiftBy] & 0xff) << shiftBy * 8; 
         } 
         return Float.intBitsToFloat(accum); 
	 }	
	 
	 public static long byte2int(byte[] res) { 
		// 一个byte数据左移24位变成0xaa000000，再右移8位变成0x00aa0000 

		long targets = (res[0] & 0xff) | ((res[1] << 8) & 0xff00) // | 表示安位或 
		| ((res[2] << 24) >>> 8) | (res[3] << 24); 
		return targets; 
	 } 
	 
	public static int getInt(byte[] bytes)
	{
	    return (0xff & bytes[0]) | (0xff00 & (bytes[1] << 8)) | (0xff0000 & (bytes[2] << 16)) | (0xff000000 & (bytes[3] << 24));
	}
	
	public static short getShort(byte[] bytes)
    {
        return (short) ((0xff & bytes[0]) | (0xff00 & (bytes[1] << 8)));
    }
	

	public static byte[] parseStock(DataInputStream dis) throws IOException {
		
		byte[] byte4 = new byte[4];
		byte[] byte8 = new byte[8];
		byte[] byte12 = new byte[12];
		
		String date;
		Double open;
		Double close;
		long timestamp;
		
		Date date_t;
		SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
		
		//32-47
		dis.read(byte4);
		while (getInt(byte4) != 0xFFFFFFFF) {
			
			timestamp = byte2int(byte4);
			date_t = new Date(timestamp * 1000);
			date = fmt.format(date_t);
			
			dis.read(byte4);
			open = Double.parseDouble(toFloat(byte4) + "");
			
			dis.read(byte8);
			
			//48-63
			dis.read(byte4);
			close = Double.parseDouble(toFloat(byte4) + "");
			
			dis.read(byte12);
			
			System.out.println("date:" + date + " open:" + open + " close:" + close);
			
			dis.read(byte4);
		}
		
		return byte4;
	}
	 
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		DataInputStream dis = new DataInputStream(new FileInputStream(new File("D:\\Installed App\\dzh2\\SUPERSTK.DAD")));
		
		byte[] byte2 = new byte[2];
		byte[] byte4 = new byte[4];
		byte[] byte6 = new byte[6];
		byte[] byte8 = new byte[8];
		byte[] byte16 = new byte[16];
		
		String market;
		String code;
		String name;
		
		dis.read(byte16);
		while (dis.available() > 0) {
			
			//0-15
			dis.read(byte4);
			
			// new stock
			while (getInt(byte4) == 0xFFFFFFFF) {
				//4-15
				dis.read(byte2);
				if (getShort(byte2) == 0x0000) {
					break;
				}
				market = new String(byte2);
				
				dis.read(byte6);
				code = new String(byte6);

				dis.read(byte4);
				
				//16-31
				dis.read(byte4);
				
				dis.read(byte8);
				name = new String(byte8, "gbk");
				
				dis.read(byte4);
						
				System.out.println("market:" + market + " code:" + code + " name:" + name);
				byte4 = parseStock(dis);
			}
		}
		
		dis.close();
	}

}
