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
	//���ѡ��ӭ����
		
		//�����߳�����
		  Thread thread = new Thread() {
	        	@Override
	        	public void run() {
	        	
	            preferences = getSharedPreferences("data", 0);
	          //�ж��ǲ����״ε�¼��  
	            if (preferences.getInt("firststart", 0)!=1) {   
	             //����¼��־λ����Ϊfalse���´ε�¼ʱ������ʾ�״ε�¼����  
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
