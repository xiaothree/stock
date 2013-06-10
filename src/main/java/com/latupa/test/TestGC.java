package com.latupa.test;

import java.util.ArrayList;
import java.util.Scanner;

class ThreadTest extends Thread {
	
	ArrayList<ArrayList<Integer>> list = new ArrayList<ArrayList<Integer>>();
	
	Scanner in = new Scanner(System.in);
	
	public void malloc() {
		System.out.println("mallocing ...");
		//ArrayList<Integer> sub_list = new ArrayList<Integer>(10000000); //40MB
		ArrayList<Integer> sub_list = new ArrayList<Integer>(100); //40MB
		sub_list.add(9999);
		list.add(sub_list);
		System.out.println(list.size() + "lists left");
	}
	
	public void free() {
		System.out.println("freeing ...");
		if (list.size() >= 1) {
			list.remove(list.size() - 1);
			
		}
		System.out.println(list.size() + "lists left");
		
	}
	
	public void input() {
		try {
			System.out.println("malloc now? [a|d|n]:");

			if (in.hasNext()) {
				String readLine = in.nextLine();
				if (readLine.equals("a")) {
					malloc();
				}
				else if (readLine.equals("d")) {
					free();
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void run() {
		while (true) {
			input();
		}
	}
}

public class TestGC {
	/**
	 * @param args
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InterruptedException {
		// TODO Auto-generated method stub
		System.out.println("start ...");
//		Scanner in = new Scanner(System.in);
//		while (true) {
//			System.out.println("?");
//			String read = in.nextLine();
//			System.out.println(read);
//			if (read.equals("finish")) {
//				break;
//			}
//		}
//		in.close();
		ThreadTest t = new ThreadTest();
		t.start();
	}
}
