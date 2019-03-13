package com.example.sqdance;

import com.grape.videoplayer.VideoPlayerActivity;
import com.genius.demo.MusicPlayActivity;
import com.genius.demo.R;
import com.genius.demo.function1Activity;
import com.genius.service.ServiceManager;

import android.os.Bundle;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity {
	private Button btn11,btn22;

	

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        btn11 = (Button) findViewById(R.id.button11);
    	btn22 = (Button) findViewById(R.id.button22);
    	
    	String fonts="fonts/STXINWEI.TTF";
		Typeface typeface = Typeface.createFromAsset(getAssets(),fonts);
		btn11.setTypeface(typeface);
		btn22.setTypeface(typeface);
		

		
    	 btn11.setOnClickListener(new OnClickListener(){
	        	
	        	public void onClick(View arg0){
	        		Intent intent=new Intent(MainActivity.this,MusicPlayActivity.class);
	        		startActivity(intent);
	        		
	        		finish();
	        	}
	        	
	        	
	        });
 	 btn22.setOnClickListener(new OnClickListener(){
	        	
	        	public void onClick(View arg1){
	        		Intent intent=new Intent(MainActivity.this,VideoPlayerActivity.class);
	        		startActivity(intent);
	        		
	        		finish();
	        	}
	        	
	        	
	        });
    		
    	
    }


   

    
    
}
