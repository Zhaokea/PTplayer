package com.genius.demo;



import com.genius.demo.MusicPlayActivity;
import com.genius.demo.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
/**
 * @author Google_acmer
 *
 */
public class function3Activity extends Activity implements OnClickListener {
    /** Called when the activity is first created. */
	
	private ImageButton next;

	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       
        
    }
   

		public void onClick(View v) {
			// TODO Auto-generated method stub
		    finish();
			Intent intent = new Intent(function3Activity.this,function4Activity.class);
			startActivity(intent);
			
		}
    	
    
}
    