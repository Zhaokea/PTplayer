package com.genius.demo;




import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class WelcomActivity extends Activity {

	SharedPreferences preferences;
	@Override
	
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
	//随机选择欢迎界面
		
		//利用线程休眠
		  Thread thread = new Thread() {
	        	@Override
	        	public void run() {
	        	
	            preferences = getSharedPreferences("data", 0);
	          //判断是不是首次登录，  
	            if (preferences.getInt("firststart", 0)!=1) {   
	             //将登录标志位设置为false，下次登录时不在显示首次登录界面  
	             preferences.edit().putInt("firststart", 1).commit();  
	            finish();
	             Intent intent = new Intent(WelcomActivity.this,function1Activity.class);
	         	startActivity(intent);
	             }else
	             {
	        	Intent intent = new Intent(WelcomActivity.this,MusicPlayActivity.class);
	        	startActivity(intent);
	            }
	        	}
	        	
	        	};
	        	thread.start();
	        	
	}
	

}
