package com.admob.android.ads;

import java.io.*;
import java.net.*;
import android.content.*;
import android.content.pm.*;
import android.util.*;

/**
 * Makes requests to the AdMob ad servers to get an <code>Ad</code> back.
 * <p>
 * Functionally this class is like normal AdMob Pub Code.  In fact it is based on
 * the Java 1.5 Pub Code version 20080401.  One big difference is the website
 * Pub Code stuffs the original phone's HTTP headers into "h[header]" parameters.
 * Since we contact the servers directly, and are not a proxy like websites, we
 * do not do that.  However, for the servers to recognize that we must set the 
 * <code>client_sdk</code> field.
 */
class AdRequester
{
	/**
	 * The URL to POST to and get ads back from.
	 */
	private static final String ADMOB_ENDPOINT = "http://r.admob.com/ad_source.php";
	
	/**
	 * The number of milliseconds after which ad requests timeout.
	 */
	private static int REQUEST_TIMEOUT = 3000;  // 3 seconds
	
	/**
	 * Requests an ad from AdMob.
	 * <p>
	 * This method blocks until the request has been satisfied which can take several seconds.
	 * Make sure to only run it from a background thread.
	 * 
	 * @param context is the application's context.
	 * @param keywords are hints for the ad server; typically this is <code>null</code>.
	 *  For example if this application is checkers you might supply "checkers game".
	 * @param searchQuery is the search phrase this ad appears next to; typically this is <code>null</code>.
	 *  Search queries are stricter than keywords and mean this ad is part of a search results set.
	 *  For example if your application is a phone directory then a search might be "Chinese Food".
	 * @return The ad returned from AdMob.
	 */
	public static Ad requestAd( Context context, String keywords, String searchQuery )
	{
		// Verify Internet permission is available.  Otherwise our HTTP request will fail with
		// an unhelpful NPE when writing out to the server.
		if ( context.checkCallingOrSelfPermission( android.Manifest.permission.INTERNET ) == PackageManager.PERMISSION_DENIED )
		{
			AdManager.clientError( "Cannot request an ad without Internet permissions!  " +
					"Open manifest.xml and just before the final </manifest> tag add:  <uses-permission android:name=\"android.permission.INTERNET\" />" );
		}
	
		// start off the thread to internationalize.
		AdMobLocalizer.init(context);
		
		// Call AdMob to get an ad.
		Ad ad = null;
		long start = System.currentTimeMillis();
		
		StringBuilder contents = new StringBuilder();
		String iconURL = null;
		
		try
		{
		    String postString = AdRequester.buildParamString(context, keywords, searchQuery);

			// Make the HTTP request for the ad.
			BufferedWriter writer = null;
			BufferedReader reader = null;
			
			try
			{
				URL admob_url = new URL( ADMOB_ENDPOINT );
				HttpURLConnection connection = (HttpURLConnection) admob_url.openConnection();
				connection.setRequestMethod( "POST" );
				connection.setDoOutput( true );
				connection.setRequestProperty( "User-Agent", AdManager.getUserAgent() );  // Android makes this "Java0" by default
				connection.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded" );
				connection.setRequestProperty( "Content-Length", Integer.toString( postString.length() ) );
				connection.setConnectTimeout( REQUEST_TIMEOUT );
				connection.setReadTimeout( REQUEST_TIMEOUT );
				String body = postString;

				if ( Log.isLoggable( AdManager.LOG, Log.DEBUG ) )
				{
					Log.d( AdManager.LOG, "Requesting an ad with POST parmams:  " + body );
				}
				
				OutputStream out = connection.getOutputStream();
				writer = new BufferedWriter( new OutputStreamWriter( out ) );
				writer.write( body );
				writer.close();
				
				reader = new BufferedReader( new InputStreamReader( connection.getInputStream() ) );
				for ( String line; (line = reader.readLine()) != null; )
				{
					contents.append( line );
				}
			
				// Get the URL of the icon image to display with the ad text.
				iconURL = connection.getHeaderField( "X-AdMob-Android-Category-Icon" );
				
				if ( iconURL == null )
				{
					// Use the default icon if there was some problem.
					iconURL = "http://mm.admob.com/static/android/tiles/default.png";
				}
			}
			finally
			{
				// Clean up.
				try
				{
					if ( writer != null )
					{
						writer.close();
					}
					
					if ( reader != null )
					{
						reader.close();
					}
				}
				catch (Exception e)
				{
				}
			}
		}
		catch (Exception ex)
		{
			Log.w( AdManager.LOG, "Could not get ad from AdMob servers.", ex );
		}

		// Turn the response into an Ad object and return it.
		String html = contents.toString();
		
		if ( html != null )
		{
			ad = Ad.createAd( context, html, iconURL );
		}

		if ( Log.isLoggable( AdManager.LOG, Log.INFO ) )
		{
			long time = System.currentTimeMillis() - start;
			
			if ( ad == null )
			{
				Log.i( AdManager.LOG, "Server replied that no ads are available (" + time + "ms)" );
			}
			else
			{
				Log.i( AdManager.LOG, "Ad returned in " + time + "ms:  " + ad );
			}
		}
		
		return ad;
	}
	
	/**
	 * Builds a string for use as a post or query parameter string when making an ad request.
	 * 
	 * @param context is the Android context.
	 * @param keywords are hints for the ad server; typically this is <code>null</code>.
	 *  For example if this application is checkers you might supply "checkers game".
	 * @param searchQuery is the search phrase this ad appears next to; typically this is <code>null</code>.
	 *  Search queries are stricter than keywords and mean this ad is part of a search results set.
	 *  For example if your application is a phone directory then a search might be "Chinese Food".
	 * @return An ad request's GET query parameters or POST body.
	 */
	static String buildParamString(Context context, String keywords, String searchQuery) 
	{
        StringBuilder post = new StringBuilder();

        // The time of this request.
        long now = System.currentTimeMillis();
        post.append( "z" ).append( "=" ).append( now / 1000 ).append( "." ).append( now % 1000 );
        
        // This is an ad request (not an Analytics request).
        appendParams( post, "rt", "0" );

        // The ID specific to this application.
        String publisherID = AdManager.getPublisherId( context );
        
        if ( publisherID == null )
        {
            throw new IllegalStateException( "Publisher ID is not set!  To serve ads you must set your publisher ID assigned from www.admob.com.  " +
                    "Either add it to AndroidManifest.xml under the <application> tag or call AdManager.setPublisherId().");
        }
        
        appendParams( post, "s", publisherID );

        // Always serve just HTML.
        appendParams( post, "f", "html_no_js" );

        // Always serve just text ads (no Text-Plus or CPM).
        appendParams( post, "y", "text" );

        // Parameter specifically for the Android SDK.  (Not in the normal Pub Code).
        appendParams( post, "client_sdk", "1" );
        
        // Do not treat the ad request as proxied by a publisher.  It is coming directly from a device.
        appendParams( post, "ex", "1" );
        
        // The normal version parameter is the same as our client_sdk one.
        appendParams( post, "v", AdManager.SDK_VERSION );
        
        // Give the ID unique to this phone to identify this user.
        appendParams( post, "t", AdManager.getUserId( context ) );

        // Give the location of the user.
        appendParams( post, "d[coord]", AdManager.getCoordinatesAsString( context ) );
        
        // Give the user's age.
        appendParams( post, "d[dob]", AdManager.getBirthdayAsString() );

        // Supply any hints to improve relevancy.
        appendParams( post, "k", keywords );
        
        // Supply any search terms.
        appendParams( post, "search", searchQuery );
        
        // When set this request always returns the test ad.
        if ( AdManager.isInTestMode() )
        {
            appendParams( post, "m", "test" );
        }
	 
        return post.toString();
	}
	
	/**
	 * Utility method for assembling our POST body.
	 */
	private static void appendParams( StringBuilder param, String key, String val ) 
	{
		if ( val != null && val.length() > 0 )
		{
			try
			{
				param.append( "&" ).append( URLEncoder.encode( key, "UTF-8" ) )
					 .append( "=" ).append( URLEncoder.encode( val, "UTF-8" ) );
			}
			catch (UnsupportedEncodingException e)
			{
				Log.e( AdManager.LOG, "UTF-8 encoding is not supported on this device.  Ad requests are impossible.", e );
			}
		}
	}
}
