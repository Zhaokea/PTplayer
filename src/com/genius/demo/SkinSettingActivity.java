package com.genius.demo;

import com.genius.adapter.ImageAdapter;
import com.genius.widget.Settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.Toast;

/**
 * @author Google_acmer
 *
 */

public class SkinSettingActivity extends SettingActivity{
	private GridView gv_skin;			//网格视图
	private ImageAdapter adapter;		//图片适配器
	private Settings mSetting;			//设置引用
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.skinsetting_layout);		
		resultCode = 2;
		mSetting = new Settings(this, true);	
		SkinSettingActivity.this.getWindow().setBackgroundDrawableResource(Settings.SKIN_RESOURCES[6]);
		adapter = new ImageAdapter(this, mSetting.getCurrentSkinId());
		gv_skin = (GridView) findViewById(R.id.gv_skin);
		gv_skin.setAdapter(adapter);
		gv_skin.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				//更新GridView
				adapter.setCurrentId(position);
				//更新背景图片
				SkinSettingActivity.this.getWindow().setBackgroundDrawableResource(Settings.SKIN_RESOURCES[position]);
				//保存数据
				mSetting.setCurrentSkinResId(position);
				SharedPreferences preferences;
				 preferences = getSharedPreferences("data", 0);
				preferences.edit().putInt("background_id", position).commit();
				//Toast.makeText(SkinSettingActivity.this, "位置"+preferences.getInt("id", 0), Toast.LENGTH_SHORT);
				
			}
		});
	}
	
}
