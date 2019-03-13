package com.admob.android.ads;

import java.math.*;
import java.security.*;
import java.util.*;
import android.content.*;
import android.content.pm.*;
import android.location.*;
import android.os.*;
import android.util.*;

/**
 * Provides data required to deliver ads.  If you know more about the user, such as their gender
 * or birthday, you can deliver more relevant advertisements to your users by setting those attributes here.
 */
public class AdManager
{
	/**
	 * Male and Female genders.
	 */
	public enum Gender
	{
		MALE,
		FEMALE
	}
	
	/**
	 * The release date of the SDK.  This is also the version number.
	 */
	static final String SDK_VERSION_DATE = "20091123";
	
	/**
	 * The checksum for this SDK version.
	 */
	private static final String SDK_VERSION_CHECKSUM = "3312276cc1406347";
	
	/**
	 * This SDK version.  It is passed in every ad request in the <code>client_sdk</code>
	 * field.  The server can use this for any version-specific logic.
	 */
	public static final String SDK_VERSION = SDK_VERSION_DATE + "-ANDROID-" + SDK_VERSION_CHECKSUM;
	
	/**
	 * The site ID used by our examples.  This should never be used by anything other than
	 * our examples or else the publisher won't be credited for ads.
	 */
	static final String SDK_SITE_ID = "a1496ced2842262";

	/**
	 * The category all AdMob SDK logging falls under in Android's logging console.
	 */
	static final String LOG = "AdMob SDK";

	/**
	 * The frequency to get location updates.
	 */
	private static final long LOCATION_UPDATE_INTERVAL = 15 * 60 * 1000;  // 15 minutes
	
	/**
	 * The User-Agent HTTP header for this phone.
	 * <p>
	 * By default Android makes this "Java0" when using HTTP classes directly.  However,
	 * we need it to be the browser's user agent so AdMob servers can optimize this ad.
	 */
	private static String userAgent;

	/**
	 * This application's AdMob ID.
	 */
	private static String publisherId;

	/**
	 * A flag indicating if test ads should be returned instead of regular ads.  This helps
	 * development because you always get the same ad back.  Normal ad requests might not
	 * fill which means the ad wouldn't show up at all.
	 */
	private static boolean testMode;
	
	/**
	 * A unique ID for this user that helps show a variety of ads instead of the same
	 * one over and over.
	 */
	private static String userId;
	
	/**
	 * The user's location to show geotargeted ads to.
	 */
	private static Location coordinates;

	/**
	 * The time that <code>coordinates</code> was obtained.  This is used to get a new location
	 * after some period of time.
	 * <p>
	 * The reason we need this instead of just passing in a minimum time to the location listener
	 * is that Android does not shut the GPS off between location updates.  Therefore we are responsible
	 * for turning the GPS off and back on.
	 */
	private static long coordinatesTimestamp;
	
	/**
	 * The user's birthday to show ads targeted to certain age ranges.
	 */
	private static GregorianCalendar birthday;
	
	/**
	 * The gender of the user to show ads targeted to men or women.
	 */
	private static Gender gender;

	/**
	 * Print out the SDK version.
	 */
	static
	{
		Log.i( LOG, "AdMob SDK version is " + SDK_VERSION );
	}
	
	/**
	 * Delivers a critical error message to the user.  These errors mean the ad cannot work.
	 * For example if the publisher ID is not configured correctly.
	 * 
	 * @param message is a the error message for the user.  It should be less than 50 characters
	 *  if possible.
	 */
	protected static void clientError( String message )
	{
		// Log it to the LogCat window.
		Log.e( LOG, message );

		// Raise an exception.
		throw new IllegalArgumentException( message );
	}
	
	/**
	 * Gets the unique ID assigned to this application by AdMob.
	 * 
	 * @param context is the application's context used to get the user's location.
	 * @return The publisher ID for this application.
	 */
	public static String getPublisherId( Context context )
	{
		if ( publisherId == null )
		{
			// Read the Publisher ID from AndroidManifest.xml.  It is under the <application> tag and looks like:
			//	<meta-data android:name="ADMOB_PUBLISHER_ID" android:value="a1496ced2842262" />
			try
			{
				PackageManager manager = context.getPackageManager();
				ApplicationInfo info = manager.getApplicationInfo( context.getPackageName(), PackageManager.GET_META_DATA );
				
				if ( info != null )
				{
					String id = info.metaData.getString( "ADMOB_PUBLISHER_ID" );
					Log.d( LOG, "Publisher ID read from AndroidManifest.xml is " + id );

					if ( id.equals( SDK_SITE_ID ) &&
						 (context.getPackageName().equals( "com.admob.android.test" ) ||
						  context.getPackageName().equals( "com.example.admob.lunarlander" )) )
					{
						// Special case.  This is the sample application so allow the sample publisher ID.
						Log.i( LOG, "This is a sample application so allowing sample publisher ID." );
						publisherId = id;
					}
					else
					{
						setPublisherId( id );
					}
				}
			}
			catch (Exception e)
			{
				Log.e( LOG, "Could not read ADMOB_PUBLISHER_ID meta-data from AndroidManifest.xml.", e );
			}
		}
		
		return publisherId;
	}
	
	/**
	 * Programatic way to sets the unique publisher ID assigned to this application by AdMob.
	 * Typically, however, it is set in your AndroidManifest.xml by adding it within the
	 * "<code>&lt;application&gt</code>" tag:
	 * <pre><code>
	 * 	&lt;meta-data android:name="ADMOB_PUBLISHER_ID" android:value="Your Publisher ID here" /&gt;
	 * </code></pre>
	 * 
	 * @param id is the publisher ID for this application.
	 */
	public static void setPublisherId( String id )
	{
		if ( (id == null) || (id.length() != 15) )
		{
			clientError( "SETUP ERROR:  Incorrect AdMob publisher ID.  Should 15 [a-f,0-9] characters:  " + publisherId );
		}

		if ( id.equalsIgnoreCase( SDK_SITE_ID ) )
		{
			// Do not allow publishers to use the sample ID by accident.
			clientError( "SETUP ERROR:  Cannot use the sample publisher ID (" + SDK_SITE_ID + ").  Yours is available on www.admob.com." );
		}
		
		Log.i( LOG, "Publisher ID set to " + id );
		
		AdManager.publisherId = id;
	}
	
	/**
	 * Returns if the SDK is in test mode or not.  Test mode is helpful in development
	 * because the same test ad is always returned.  Normal ad requests might time out
	 * or might not fill so it is possible no ad is returned.
	 * <p>
	 * <i>Never release you application with test mode enabled!</i>  You can only make
	 * money by showing real ads so when placing your application in the Market test
	 * mode must be <code>false</code>.
	 * 
	 * @return <code>true</code> if the test ad is always returned or <code>false</code>
	 *  if normal ad requests are made.
	 */
	public static boolean isInTestMode()
	{
		return testMode;
	}
	
	/**
	 * Sets if ad requests should fetch the test ad or make a real ad request.  Setting this
	 * to <code>true</code> means you should always see the same ad returned.  This is useful
	 * to make sure you AdMob SDK integration is working.  Once you see the ad displayed as
	 * you'd expect you should make sure this is <code>false</code> (the default).
	 * <p>
	 * Typically this is set through your XML layout file using the "<code>admob:testing</code>"
	 * attribute.  Note that setting test mode on one ad view will enable it for all of them.
	 * For example:
	 * <pre><code>
	 *	&lt;com.admob.android.ads.AdView
	 *	    android:layout_width="fill_parent"
	 *	    android:layout_height="wrap_content"
	 *	    admob:testing="true"
	 *	/&gt;
	 * </code></pre>
	 * 
	 * @param testing should be set to <code>true</code> to always get the same test ad back.
	 */
	public static void setInTestMode( boolean testing )
	{
		AdManager.testMode = testing;
	}
	
	/**
	 * Returns a unique user ID.
	 * 
	 * @param context is the application's context used to get a unique ID for this user.
	 * @return The unique user ID.
	 */
	public static String getUserId( Context context )
	{
		if ( userId == null )
		{
			// Get the unique identifier for the phone.
			//   Recommended way is Android 1.5 but not available in 1.1:	userId = Settings.Secure.getString( context.getContentResolver(), Settings.Secure.ANDROID_ID );
			userId = android.provider.Settings.System.getString( context.getContentResolver(), android.provider.Settings.System.ANDROID_ID );
			
			// Make the ID the standard AdMob 32 character length.
			userId = md5( userId );
			
			Log.i( LOG, "The user ID is " + userId );
		}
		
		return userId;
	}
	
	/**
	 * Utility method for hashing any string into a 32 character string.  Useful for
	 * creating a unique phone signature.
	 * <p>
	 * Method taken directly from the Pub Code.
	 */
	private static String md5( String val )
	{
		String result = null;
		
		if ( val != null && val.length() > 0 )
		{
			try
			{
				MessageDigest md5 = MessageDigest.getInstance( "MD5" );
				md5.update( val.getBytes(), 0, val.length() );
				result = String.format( "%032X", new BigInteger( 1, md5.digest() ) );
			}
			catch (Exception e)
			{
				Log.d( LOG, "Could not generate hash of " + val, e );
				
				// Not perfect, but trim to 32 chars.
				userId = userId.substring( 0, 32 );
			}
		}
		
		return result;
	}
	
	/**
	 * Returns the last known location of the user.
	 * <p>
	 * For this to return data the application needs permission to access the location
	 * services of the phone.  Copy-and-paste one of the following lines into manifest.xml just before the
	 * final tag "<code>&lt;/manifest&gt</code>":
	 * <pre><code>
	 * 	&lt;uses-permission android:name="android.permission.ACCESS_COURSE_LOCATION" /&gt;
	 * 	&lt;uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" /&gt;
	 * </code></pre>
	 * <p>
	 * For the purposes of serving ads the course location is great.  It also uses much
	 * less battery.  Fine location is only recommended if your application already needs
	 * the GPS (in which case adding course location is optional).
	 * 
	 * @param context is the application's context used to get the user's location.
	 * @return The last known location of the user or <code>null</code> if
	 *  it is not known.
	 */
	public static Location getCoordinates( Context context )
	{
		if ( context != null )
		{
			// Should we try to get a location?
			if ( (coordinates == null) || (System.currentTimeMillis() > coordinatesTimestamp + LOCATION_UPDATE_INTERVAL) )
			{
				// Only one thread should try to get the location.
				synchronized ( context )
				{
					// Recheck if we should get a location.  Another thread might have aquired the lock first and done
					// it already.
					if ( (coordinates == null) || (System.currentTimeMillis() > coordinatesTimestamp + LOCATION_UPDATE_INTERVAL) )
					{
						// Claim we got coordinates now.  Other threads will hopefully skip the synchronization block.
						AdManager.coordinatesTimestamp = System.currentTimeMillis();

						boolean permissions = false;
						LocationManager manager = null;
						String provider = null;
						
						// First try to get access to the network triangulation location.
						if ( context.checkCallingOrSelfPermission( android.Manifest.permission.ACCESS_COARSE_LOCATION ) == PackageManager.PERMISSION_GRANTED )
						{
			        		if ( Log.isLoggable( LOG, Log.DEBUG ) )
			        		{
			        			Log.d( LOG, "Trying to get locations from the network." );
			        		}
							
							permissions = true;
							manager = (LocationManager) context.getSystemService( Context.LOCATION_SERVICE );
							
							if ( manager != null )
							{
								Criteria criteria = new Criteria();
								criteria.setAccuracy( Criteria.ACCURACY_COARSE );
								criteria.setCostAllowed( false );
								provider = manager.getBestProvider( criteria, true );
							}
						}
						
						// If we did not get GPS access, try to get location through GPS.
						if ( provider == null )
						{
							if ( context.checkCallingOrSelfPermission( android.Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED )
							{
				        		if ( Log.isLoggable( LOG, Log.DEBUG ) )
				        		{
				        			Log.d( LOG, "Trying to get locations from GPS." );
				        		}
								
								permissions = true;
								manager = (LocationManager) context.getSystemService( Context.LOCATION_SERVICE );
								
								if ( manager != null )
								{
									Criteria criteria = new Criteria();
									criteria.setAccuracy( Criteria.ACCURACY_FINE );
									criteria.setCostAllowed( false );
									provider = manager.getBestProvider( criteria, true );
								}
							}
						}
			
						// Did we get an active location provider?
						if ( permissions == false )
						{
							if ( Log.isLoggable( LOG, Log.DEBUG ) )
							{
								Log.d( LOG, "Cannot access user's location.  Permissions are not set." );
							}
						}
						else if ( provider == null )
						{
			        		if ( Log.isLoggable( LOG, Log.DEBUG ) )
			        		{
			        			Log.d( LOG, "No location providers are available.  Ads will not be geotargeted." );
			        		}
						}
						else  // provider != null
						{
							Log.i( LOG, "Location provider setup successfully." );
							
							// Add a location listener to get coordinates if needed.
							final LocationManager mgr = manager;
							manager.requestLocationUpdates( provider, 0, 0, new LocationListener()
								{
									public void onLocationChanged( Location location )
									{
										// Update the user's location.
										AdManager.coordinates = location;
										AdManager.coordinatesTimestamp = System.currentTimeMillis();
 
										// Stop getting location updates.  This will drain the battery and one
										// location is good enough.
										mgr.removeUpdates( this );
										
										Log.i( LOG, "Aquired location " + 
												AdManager.coordinates.getLatitude() + "," + AdManager.coordinates.getLongitude() +
												" at " + new Date(AdManager.coordinatesTimestamp).toString() + "." );
									}
									
									public void onProviderDisabled( String provider )
									{
										// Don't care if we stop getting location updates.
									}
									
									public void onProviderEnabled( String provider )
									{
										// Don't care until we get a location update.
									}
									
									public void onStatusChanged( String provider, int status, Bundle extras )
									{
										// Don't care.
									}
								},
								context.getMainLooper() );
						}
					}
				}
			}
		}
		
		return coordinates;
	}
	
	/**
	 * Returns the last known location of the user as a string suitable for
	 * consumption by AdMob servers.
	 * 
	 * @param context is the application's context used to get the user's location.
	 * @return The last known location of the user or <code>null</code> if
	 *  it is not known.
	 */
	static String getCoordinatesAsString( Context context )
	{
		String result = null;
		Location l = getCoordinates( context );
		
		if ( l != null )
		{
			result = l.getLatitude() + "," + l.getLongitude();
		}

		if ( Log.isLoggable( LOG, Log.DEBUG ) )
		{
			Log.d( LOG, "User coordinates are " + result );
		}
	
		return result;
	}
	
	/**
	 * Returns the birthday of the user.  This is used to calculate their age.
	 * 
	 * @return The birthday of the user or <code>null</code> if it is not known.
	 */
	public static GregorianCalendar getBirthday()
	{
		return birthday;
	}
	
	/**
	 * Returns the birthday of the user as a string suitable for consumption by 
	 * AdMob servers.
	 * 
	 * @return The birthday of the user or <code>null</code> if it is not known.
	 */
	static String getBirthdayAsString()
	{
		String result = null;
		GregorianCalendar dob = getBirthday();
		
		if ( dob != null )
		{
			result = String.format( "%04d%02d%02d", 
					dob.get( Calendar.YEAR ),
					dob.get( Calendar.MONTH ) + 1,  // January starts at 0
					dob.get( Calendar.DAY_OF_MONTH ) );
		}
		
		return result;
	}
	
	/**
	 * Sets the birthday of the user.
	 * 
	 * @param birthday is the user's birthday or <code>null</code> if it is not known.
	 */
	public static void setBirthday( GregorianCalendar birthday )
	{
		AdManager.birthday = birthday;
	}
	
	/**
	 * Sets the birthday of the user.
	 * 
	 * @param year is the year they were born in (e.g. 1976).
	 * @param month is the month they were born in (e.g. 3 for March).
	 * @param day is the day they were born on (e.g. 13 for the 13th).
	 */
	public static void setBirthday( int year, int month, int day )
	{
		GregorianCalendar cal = new GregorianCalendar();
		cal.set( year, month - 1, day );
		setBirthday( cal );
	}

	/**
	 * Returns the user's gender.
	 * 
	 * @return The gender of the user or <code>null</code> if it is not known.
	 */
	public static Gender getGender()
	{
		return gender;
	}
	
	/**
	 * Returns the user's gender as a string suitable for consumption by 
	 * AdMob servers.
	 * 
	 * @return The gender of the user or <code>null</code> if it is not known.
	 */
	static String getGenderAsString()
	{
		if ( gender == Gender.MALE )
		{
			return "m";
		}
		else if ( gender == Gender.FEMALE )
		{
			return "f";
		}
		else
		{
			return null;
		}
	}
	
	/**
	 * Sets the gender of the user to either male or female.
	 * 
	 * @param gender is the user's gender or <code>null</code> if it is not known.
	 */
	public static void setGender( Gender gender )
	{
		AdManager.gender = gender;
	}
	
	/**
	 * Generates the User-Agent HTTP header used by the browser on this phone.
	 * 
	 * @return The user-agent for the WebKit browser specific to this phone.
	 */
	static String getUserAgent()
	{
		if ( userAgent == null )
		{
			// This is copied from the Android source frameworks/base/core/java/android/webkit/WebSettings.java.
			// It is somewhat of a hack since Android does not expose this to us but AdMob servers need it
			// for ad picking.
			
			StringBuffer arg = new StringBuffer();
			
			// Add version
			final String version = Build.VERSION.RELEASE;
			if ( version.length() > 0 )
			{
				arg.append( version );
			}
			else
			{
				// default to "1.0"
				arg.append( "1.0" );
			}
			arg.append( "; " );
			
			// Initialize the mobile user agent with the default locale.
			final Locale l = Locale.getDefault();
			final String language = l.getLanguage();
			if ( language != null )
			{
				arg.append( language.toLowerCase() );
				final String country = l.getCountry();
				if ( country != null )
				{
					arg.append( "-" );
					arg.append( country.toLowerCase() );
				}
			}
			else
			{
				// default to "en"
				arg.append( "en" );
			}

			// Add the device model name and Android build ID.
	        final String model = Build.MODEL;
			if ( model.length() > 0 )
			{
				arg.append( "; " );
				arg.append( model );
			}
			final String id = Build.ID;
			if ( id.length() > 0 )
			{
				arg.append( " Build/" );
				arg.append( id );
			}
			
			// Mozilla/5.0 (Linux; U; Android 1.0; en-us; dream) AppleWebKit/525.10+ (KHTML, like Gecko) Version/3.0.4 Mobile Safari/523.12.2 (AdMob-ANDROID-20090701)
			//final String base = context.getResources().getText(com.android.internal.R.string.web_user_agent).toString();
			final String base = "Mozilla/5.0 (Linux; U; Android %s) AppleWebKit/525.10+ (KHTML, like Gecko) Version/3.0.4 Mobile Safari/523.12.2 (AdMob-ANDROID-%s)";
			userAgent = String.format( base, arg, SDK_VERSION_DATE );
			
    		if ( Log.isLoggable( LOG, Log.DEBUG ) )
    		{
    			Log.d( LOG, "Phone's user-agent is:  " + userAgent );
    		}
		}
		
		return userAgent;
	}
}
