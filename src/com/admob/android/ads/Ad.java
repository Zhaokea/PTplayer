package com.admob.android.ads;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

/**
 * Encapsulates an AdMob ad.  Normally AdMob ads are just HTML inserted into a xHTML page.
 * However, in an application we have to do the things a browser would with that HTML.
 * This class takes care of those details (like fetching the ad image).
 */
class Ad
{
	/**
	 * The number of milliseconds after which automatically following click URL
	 * redirects timeout.  During this time a spinner appears on the ad to give the
	 * user a visual indicator something is going on.  This number can and should
	 * be fairly big because sometimes landing pages take a long time to load.
	 */
	private static int CLICK_REQUEST_TIMEOUT = 5000;  // 5 seconds
	
	/**
	 * The application context.
	 */
	private Context context;
	
	/**
	 * The original HTML returned from an AdMob ad request.
	 */
	private String html;
	
	/**
	 * The ad's text.  This will be <code>null</code> if the ad is only an image.
	 */
	private String text;
	
	/**
	 * The URL to the ad image.  This will be <code>null</code> if the ad is text only.
	 */
	private String imageURL;

	/**
	 * If there is an image, this is the number of pixels wide it is.  This number comes
	 * from the ad HTML's width property.
	 */
	private int imageWidth;
	
	/**
	 * If there is an image, this is the number of pixels high it is.  This number comes
	 * from the ad HTML's height property.
	 */
	private int imageHeight;
	
	/**
	 * The URL to go to if the ad is clicked on.  This will never be <code>null</code>.
	 */
	private String clickURL;
	
	/**
	 * A handle to the image itself if it has been download.  This is populated by the
	 * <code>fetchImage</code> method which uses it to cache the image in case the
	 * method is called multiple times.
	 */
	private Bitmap image;
	
	/**
	 * The icon image that appears to the left of this ad.  It gives the user a visual
	 * hint about the ad and makes it look nicer.
	 */
	private Bitmap icon;
	
	/**
	 * The URL to the <code>icon</code> image for this ad.  It comes from the ad response.
	 * If this is <code>null</code> then no icon image will be displayed with the ad.
	 */
	private String iconURL;
	
    /**
     * Internal interface so that the Ad can communicate with the parent AdView that contains it.
     */
	interface NetworkListener
	{
	    public void onNetworkActivityStart();
	    
	    public void onNetworkActivityEnd();
	}
	
	private NetworkListener networkListener;
	
	/**
	 * Constructs an ad object from the HTML returned from an AdMob ad request.
	 * 
	 * @param context is the Android application context.
	 * @param html is the code returned by AdMob.
	 * @param iconURL is the address of the icon image to display alongside the ad's text.
	 *  If this is <code>null</code> then no icon image will be displayed with the ad.
	 */
	public static Ad createAd( Context context, String html, String iconURL )
	{
		if ( (html == null) || html.equals( "" ) )
		{
			return null;
		}
		
		Ad ad = new Ad();
		ad.context = context;
		ad.html = html;
		ad.iconURL = iconURL;
		
		// Parse the HTML.
		try
		{
			// Example HTML for a CPM or Text-Plus ad (with an image):
			//	<a href="http://cpm2.admob.com/c.php/3/a4d99e79f/1/1/48F8D20E/6cc65af6ce?s=l&amp;if=png">
			//		<img alt="Try voice navigation now " src="http://cpm.admob.com/img/3/a4d99e79f/1/1/48F8D20E/6cc65af6ce?s=l&amp;if=png" height="36" width="" />
			//	</a>
			//	<br />
			//	<a href="http://cpm2.admob.com/c.php/3/a4d99e79f/1/1/48F8D20E/6cc65af6ce?s=l&amp;if=png">
			//		Try voice navigation now 
			//	</a>
			
			// Get the click URL.
			int i = html.indexOf( "<a " );
			
			if ( i >= 0 )
			{
				int start = html.indexOf( " href=\"", i ) + 7;
				int end = html.indexOf( "\"", start );
				ad.clickURL = html.substring( start, end );
	
				// Go to the end of the opening anchor tag.
				i = skipToNext( html, end + 1 );
				
				if ( i < 0 )
				{
					// No click URL, no ad.
					return null;
				}
			}
	
			// Is there an image?
			if ( i >= 0 )
			{
				int j = html.indexOf( "<img", i );
				
				if ( j == i )
				{
					int start = html.indexOf( " src=\"", i ) + 6;
					int end = html.indexOf( "\"", start );
					ad.imageURL = html.substring( start, end );
					
					int startHeight = html.indexOf( " height=\"", i ) + 9;
					int endHeight = html.indexOf( "\"", startHeight );
					String height = html.substring( startHeight, endHeight );
					ad.imageHeight = Integer.valueOf( height );
					
					int startWidth = html.indexOf( " width=\"", i ) + 8;
					int endWidth = html.indexOf( "\"", startWidth );
					String width = html.substring( startWidth, endWidth );
					ad.imageWidth = Integer.valueOf( width );
					
					// Advance to the ad text.
					i = html.indexOf( "<a", endWidth + 1 );
					
					if ( i >= 0 )
					{
						i = skipToNext( html, i + 2 );  // Skip over the "<a"
					}
				}
			}
			
			// Get the ad text.
			if ( i >= 0 )
			{
				int end = html.indexOf( "<", i );
				ad.text = html.substring( i, end ).trim();
				ad.text = Html.fromHtml( ad.text ).toString();
			}
		}
		catch (Exception e)
		{
			Log.e( AdManager.LOG, "Failed to parse ad response:  " + html, e );
			return null;
		}

		// If the ad had an image get that now too.  An Ad object is not complete until
		// the image is downloaded and can be shown.
		if ( ad.hasImage() )
		{
			Bitmap img = ad.getImage();
			
			if ( img == null )
			{
				// If there was a problem getting the image we can't show the ad.  The
				// problem will have already been logged so no need to do it again.
				return null;
			}
		}
		
		// Prefetch the icon image.  We don't want to show the ad until we've also got
		// the icon image.
		if ( ad.iconURL != null )
		{
			ad.getIcon();
		}
		
		return ad;
	}
	
	/**
	 * Constructs an ad object.  Only the <code>createAd</code> method should call this.
	 */
	private Ad()
	{
	}

	/**
	 * @return the networkListener
	 */
	public NetworkListener getNetworkListener()
	{
	    return networkListener;
	}
	
	/**
	 * @param networkListener the networkListener to set
	 */
	public void setNetworkListener(NetworkListener networkListener)
	{
	    this.networkListener = networkListener;
	}

	/**
	 * Returns the position of the next opening HTML tag or text.
	 * For example if within an opening anchor tag this will go to thing it
	 * surrounds (either text or an embedded img tag).
	 * 
	 * @param html is the HTML string to walk.
	 * @param pos is the starting position.  If this is on "<" it will
	 *  get returned.
	 * @return The position of the first "<" or text at <code>pos</code> or after.
	 *  This will be -1 if the end of the HTML string is reached.
	 */
	private static int skipToNext( String html, int pos )
	{
		int end = html.length();
		
		if ( (pos < 0) || (pos >= end) )
		{
			return -1;
		}
		
		// Go to the end of the current tag/text.
		char c = html.charAt( pos );
		
		while ( (c != '>') && (c != '<') )
		{
			pos++;
			
			if ( pos >= end )
			{
				return -1;
			}
			
			c = html.charAt( pos );
		}

		// Skip over the whitespace.
		if ( c == '>' )
		{
			pos++;  // +1 to advance over '>'
			c = html.charAt( pos );
			
			while ( Character.isWhitespace( c ) )
			{
				pos++;
				
				if ( pos >= end )
				{
					return -1;
				}
				
				c = html.charAt( c );
			}
		}
		
		return pos;
	}
	
	/**
	 * @return The HTML ad response.
	 */
	public String getHTML()
	{
		return html;
	}
	
	/**
	 * @return The ad's text.  This will be <code>null</code> if the ad is only an image.
	 */
	public String getText()
	{
		return text;
	}

	/**
	 * @return <code>true</code> if this ad has an image (i.e. CPM or text-plus) and
	 *  <code>false</code> if it is text-only.
	 */
	public boolean hasImage()
	{
		return imageURL != null;
	}
	
	/**
	 * @return The URL to the ad image.  This will be <code>null</code> if the ad is text only.
	 */
	public String getImageURL()
	{
		return imageURL;
	}
	
	/**
	 * Returns the ad image from AdMob.
	 * 
	 * @return The ad's image.
	 */
	public Bitmap getImage()
	{
		if ( image == null )
		{
			if ( imageURL != null )
			{
				image = fetchImage( imageURL, false );  // Do not cache the image b/c it will mess with accounting
			}
		}
		
		return image;
	}

	/**
	 * Downloads and returns an image.
	 * 
	 * @param imageURL is the URL where the image is found.
	 * @param useCaches is a flag to indicate if the image should be cached or not.
	 * @return The image.
	 */
	private static Bitmap fetchImage( String imageURL, boolean useCaches )
	{
		Bitmap image = null;
		
		if ( imageURL != null )
		{
			InputStream is = null;
			
	        try
			{
				// Open a new URL and get the InputStream to load data from it.
				URL url = new URL( imageURL );
				URLConnection conn = url.openConnection();
				conn.setConnectTimeout( 0 );
				conn.setReadTimeout( 0 );
				conn.setUseCaches( useCaches );
				conn.connect();
				is = conn.getInputStream();
				
				// Create a bitmap.
				image = BitmapFactory.decodeStream( is );
				
				// Note we do not want to save the image to the file system.  AdMob
				// servers may use the download for accounting and optimization purposes.
			}
			catch (Throwable e)
			{
				Log.w( AdManager.LOG, "Problem getting image:  " + imageURL, e );
			}
			finally
			{
				if ( is != null )
				{
					try
					{
						is.close();
					}
					catch (IOException e)
					{
						// Ignore and continue.
					}
				}
			}
		}
		
		return image;
	}
	
	/**
	 * @return The number of pixels wide the ad's image is.
	 */
	public int getImageWidth()
	{
		if ( image != null )
		{
			return image.getWidth();
		}
		else
		{
			return imageWidth;
		}
	}
	
	/**
	 * @return The number of pixels tall the ad's image is.
	 */
	public int getImageHeight()
	{
		if ( image != null )
		{
			return image.getHeight();
		}
		else
		{
			return imageHeight;
		}
	}
	
	/**
	 * Returns the icon for this ad.  Icons are images for the dozen or so
	 * categories that an ad falls into.  This give the user a visual hint about
	 * the ad contents so they can process it by skimming the ad.
	 * 
	 * @return The icon image to use for this ad or <code>null</code> if none
	 *  should be used.
	 */
	public Bitmap getIcon()
	{
		// Fetch the image, store it in the HTTP cache and in memory.
		if ( icon == null )
		{
			icon = fetchImage( iconURL, true );

			if ( icon == null )
			{
				Log.w( AdManager.LOG, "Could not get icon for ad from " + iconURL );
			}
		}
		
		return icon;
	}
	
	/**
	 * @return The click URL for this ad.
	 */
	public String getClickURL()
	{
		return clickURL;
	}
	
	/**
	 * Records that this ad has been clicked.  The current application is swapped
	 * out with a browser that goes to the ad's click URL.
	 */
	public void clicked()
	{
		Log.i( AdManager.LOG, "Ad clicked." );
		
		if ( clickURL != null )
		{
			// Open up the browser with the click URL.
			//   This can also open the Market application.  For example the click URL
			//   to Ringer Mobile is:  http://market.android.com/details?id=6278196384529742763			
			//   The ID in the URL can be obtained on the Market's main developer console by
			//   hovering over the link of your application.
		    
		    // walk down the 302 redirects on the clickurl until we get a real url.
		    // we need to do this asynchronously.
		    new Thread()
		    {
		        public void run()
		        {
		        	URL destinationURL = null;
		        	try
		        	{ 
		        		if ( networkListener != null )
		        		{
		        			networkListener.onNetworkActivityStart();
		        		}
		        		
		        		URL url = new URL( clickURL );
		        		destinationURL = url;

		        		HttpURLConnection.setFollowRedirects(true);

		        		HttpURLConnection redirectConnection = (HttpURLConnection) url.openConnection();
		        		redirectConnection.setConnectTimeout( CLICK_REQUEST_TIMEOUT );
		        		redirectConnection.setReadTimeout( CLICK_REQUEST_TIMEOUT );
		        		redirectConnection.setRequestProperty( "User-Agent", AdManager.getUserAgent() );  
		        		redirectConnection.setRequestProperty( "X-ADMOB-ISU", AdManager.getUserId( context ) );
		        		redirectConnection.connect();
		        		// getResponseCode ensures the connection follows the redirects.
		        		redirectConnection.getResponseCode();
		        		// after successful connection check the new redirected URL
		        		destinationURL = redirectConnection.getURL();

		        		if ( Log.isLoggable( AdManager.LOG, Log.DEBUG ) )
		        		{
		        			Log.d( AdManager.LOG, "Final click destination URL:  " + destinationURL );
		        		}
		        	}
		        	catch (MalformedURLException e)
		        	{
		        		Log.w( AdManager.LOG, "Malformed click URL.  Will try to follow anyway.  " + clickURL, e );
		        	}
		        	catch (IOException e)
		        	{
		        		Log.w( AdManager.LOG, "Could not determine final click destination URL.  Will try to follow anyway.  " + clickURL, e );
		        	}

		        	if ( destinationURL != null )  // Should always be non-null
		        	{
		        		if ( Log.isLoggable( AdManager.LOG, Log.DEBUG ) )
		        		{
		        			Log.d( AdManager.LOG, "Opening " + destinationURL );
		        		}
		        		
		        		// Uri doesn't understand java.net.URI so we have to URL->String->Uri
		        		Intent i = new Intent( Intent.ACTION_VIEW, Uri.parse(destinationURL.toString()) );
		        		i.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );

		        		try
		        		{
		        			context.startActivity( i );
		        		}
		        		catch (Exception e)
		        		{
		        			Log.e( AdManager.LOG, "Could not open browser on ad click to " + destinationURL, e );
		        		}
		        	}
		        	
		        	if ( networkListener != null )
		        	{
		        		networkListener.onNetworkActivityEnd();
		        	}
		        }
		    }.start();		    
		}
	}
	
	/**
	 * Returns the HTML for this ad.
	 *
	 * @return The HTML representation of this ad.
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		String s = getText();
		
		if ( s == null )
		{
			s = "";
		}
		
		return s;
	}

	/**
	 * Determines if two ads are identical (i.e. same text and click URLs).
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals( Object o )
	{
		if ( o instanceof Ad )
		{
			Ad other = (Ad) o;
			return toString().equals( other.toString() );
		}
		else
		{
			return false;
		}
	}

	/**
	 * Returns a hash code for this ad.
	 *
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode()
	{
		return toString().hashCode();
	}
}
