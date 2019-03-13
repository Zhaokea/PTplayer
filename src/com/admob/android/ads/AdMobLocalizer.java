package com.admob.android.ads;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Properties;
import android.content.Context;
import android.util.*;

/**
 * This class serves as a way to cache and retrieve the AdMob branding text
 * in a localized language.
 * <p>
 * Ported from iPhone SDK's <a href="https://trac.admob.com/browser/trunk/dungeon/publisher_code/iphone_sdk/dev/src/AdMob/Models/AdMobLocalizer.m">AdMobLocalizer</a>.
 */
class AdMobLocalizer
{
    static private AdMobLocalizer singleton = null;
    static private Context staticContext = null;
    
    /**
     * Gets the strings for the current device's locale from AdMob servers.
     */
    static public void init(Context c)
    {
        if(staticContext == null)
        {
            staticContext = c;
        }
        
        initSingleton();    	
    }
    
    static private void initSingleton()
    {
      if(singleton == null)
      {
          singleton = new AdMobLocalizer(staticContext);
      }      
    }
    
    static public AdMobLocalizer singleton()
    {
      initSingleton();
      return singleton;
    }
    
    /**
     * Convenience method to access the singleton and localize
     * a given key.
     * @param key key for the localized string to retrieve.
     * @return localized string or key if key could not be found.
     */
    static public String localize(String key)
    {
        init(staticContext);
        return singleton.internalLocalize(key);
    }
        
    static private String ADMOB_LOCALIZATION_CACHE_DIR = "admob_cache";
    
    static private String ADMOB_LOCALIZATION_URL_STRING = 
      "http://mm.admob.com/static/android/i18n/" + AdManager.SDK_VERSION_DATE;

    static private String DEFAULT_LANGUAGE = "en";
    static private String PROPS_FILE_SUFFIX = ".properties";
    
    private String language;
    private Properties strings;
    private Context context; // needed for file access.
    
    private AdMobLocalizer(Context c)
    {
        context = c;
        // get the locale
        Locale locale = Locale.getDefault();
        language = null;
        String langCode = locale.getLanguage();
        
        setLanguage(langCode); 
    }
    
    
    public void setLanguage(String langCode)
    {
      if(langCode != language)
      {
    	  // clear out the strings
    	  strings = null;
	      language = langCode;
	      if(language == null || language.equals(""))
	      {
	          language = DEFAULT_LANGUAGE;
	      }
	      if(!loadStrings())
	      {
	          // we should spawn a thread to go out and retrieve the 
	          // localization assets.
	          new InitLocalizerThread().start();
	      }
      }
    }
    
    public String getLanguage()
    {
      return language;
    }
    
    // tries to load the strings and returns false if it fails.
    
    private boolean loadStrings()
    {
        // this will load the strings if necessary from the file system
        if(strings == null)
        {
            try
            {
                // get the strings from the file.
                Properties temp = new Properties();
                
                // load the directory.
                File dir = context.getDir(ADMOB_LOCALIZATION_CACHE_DIR, 0);
                // now load the directory for this version of code
                File versionedDir = new File(dir, AdManager.SDK_VERSION_DATE);
                if(!versionedDir.exists())
                {
                    versionedDir.mkdir();
                }
                // load the file.
                File propsFile = new File(versionedDir, language + PROPS_FILE_SUFFIX);
                if(propsFile.exists())
                {
                    InputStream is = new FileInputStream(propsFile);
                    temp.load(is);
                    strings = temp;
                }
            }
            catch(IOException e)
            {
                // we just keep the strings null.
                strings = null;
            }
        }
        return (strings != null);
    }
    
    private String internalLocalize(String key)
    {
        loadStrings();
        String localizedString = key;
        if(strings != null)
        {          
            localizedString = strings.getProperty(key);
            if(localizedString == null || localizedString.equals(""))
            {
                localizedString = key;
            }
        }
        return localizedString;
    }
    
    /**
     * A background thread that grabs the localized strings from the AdMob servers.
     */
    private class InitLocalizerThread extends Thread
    {
        @Override
        public void run()
        {
            // initiate the URL download.
            StringBuilder sb = new StringBuilder();
            sb.append(ADMOB_LOCALIZATION_URL_STRING);
            sb.append("/");
            sb.append(language);
            sb.append(PROPS_FILE_SUFFIX);
            try
            {
                URL url = new URL(sb.toString());
                URLConnection urlConnection = url.openConnection();
                urlConnection.connect();
                // read the contents of the url connection and save
                // to a file.
                BufferedInputStream is = new BufferedInputStream(urlConnection.getInputStream());
                
                // now we have a string file to save.
                File dir = context.getDir(ADMOB_LOCALIZATION_CACHE_DIR, 0);
                // now load the directory for this version of code
                File versionedDir = new File(dir, AdManager.SDK_VERSION_DATE);
                if(!versionedDir.exists())
                {
                    versionedDir.mkdir();
                }
                
                // Cache the file.
                File outputFile = new File(versionedDir, language + PROPS_FILE_SUFFIX);
                BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
                
                try
                {
	                byte[] buffer = new byte[512];
	                int bytes_read = 0;
	                while((bytes_read = is.read(buffer, 0, buffer.length)) > 0)
	                {
	                	// push it into the file.
	                	os.write(buffer, 0, bytes_read);
	                }
                }
                finally
                {
                    // we are done.  close off the file.
                    os.close();
                }
            }
            catch (Exception e)
            {
                // Could not connect to the AdMob servers.  We'll try time this application starts.
        		if ( Log.isLoggable( AdManager.LOG, Log.DEBUG ) )
        		{
        			Log.d( AdManager.LOG, "Could not get localized strings from the AdMob servers." );
        		}
            }
        }
    }
}
