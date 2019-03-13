package com.admob.android.ads;

import java.io.BufferedReader;   
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.Matrix;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.RelativeLayout;

/**
 * A view containing an advertisement.  Advertisements are best allowed to be the width of the entire
 * screen.  They are always 48 pixels tall.  This creates a consistent and visually appealing ad.
 * <p>
 * Your application can tailor an ad's background and font colors.  For example good-looking ads will
 * have the same font color as the background of the rest of the screen.  It is not possible to
 * change the appearance of an ad being clicked; it will be orange to match the Android experience.
 * <p>
 * To improve the relevance of this advertisement you can supply hints.  The <code>setKeywords</code>
 * method describes your application and the screen the ad is on.  For example if this page is a recipe
 * for chocolate cake you might set keywords to "cookbook recipe chocolate cake".  And if this ad
 * sits next to search results, which are more specific, you can use the <code>setSearchResults</code>
 * method.  There are additional methods that improve relevancy across the application in 
 * <code>AdManager</code>.
 * <p>
 * To have the view show a new ad call <code>requestFreshAd</code>.  Normally this is not good practice
 * because the mobile screens are short lived.  However if the screen is long running, such as a sports
 * scoreboard that might be shown for hours, it is appropriate to update the ad every minute or so. 
 * <p>
 * If a user clicks on this view (with the keyboard or by touch screen) it minimizes this application
 * and brings up the browser to the ad's landing page.  The user has to navigate back to this application
 * once they are done.
 */
public class AdView
	extends RelativeLayout
{
	// This class, AdView, is mainly responsible for controlling animations between ads.
	// All it does is show an AdContainer within it.  And if requestFreshAd is called for
	// a new ad, it performs an animation to swap the old AdContainer out for the new one.
	
	/**
	 * The number of milliseconds the animation between an old and new ad should
	 * last.
	 */
	private static final int ANIMATION_DURATION = 700;

	/**
	 * The percentage of the ad's width the animation should go back into thescreen.
	 */
	private static final float ANIMATION_Z_DEPTH_PERCENTAGE = -0.40f;

    /**
     * The height of all ads.
     */
    public static final int HEIGHT = 48;

	/**
	 * The view that actually renders the ad.  It completely fills this view.
	 * <p>
	 * If a new ad is shown through <code>requestFreshAd</code> then <code>ad</code>
	 * is swapped out with a newer ad container.
	 */
	private AdContainer ad;

	/**
	 * A flag indicating if this view is trying to get an ad from AdMob servers or not.
	 * Only one request to the servers can be done at a time.
	 */
	private boolean requestingFreshAd;
	
	/**
	 * The number of milliseconds between calls to <code>requestFreshAd</code>.  By default this
	 * is 0 and fresh ads are only requested by calling the method directly.  However, publishers
	 * can set this value to have the ad refresh itself at regular intervals.
	 * 
	 * @see #requestIntervalTimer
	 */
	private int requestInterval;
	
	/**
	 * A timer used to request fresh ads every <code>requestInterval</code> in the background.
	 * 
	 * @see #requestInterval
	 */
	private Timer requestIntervalTimer;
	
	/**
	 * The color used in the ad background.  A "shine" is applied over this to make it look like
	 * light is being reflected from above the ad.
	 */
	private int backgroundColor;

	/**
	 * The color of the ad's text.
	 */
	private int textColor;
	
	/**
	 * Keywords are hints for the ad server; typically this is <code>null</code>.
	 * For example if this application is checkers you might supply "checkers game".
	 */
	private String keywords;
	
	/**
	 * The search phrase this ad appears next to; typically this is <code>null</code>.
	 * Search phrases are stricter than keywords and mean this ad is part of a search results set.
	 * For example if your application is a phone directory then a search might be "Chinese Food".
	 */
	private String searchQuery;

	/**
	 * A flag indicating if this view should set its visiblity to <code>GONE</code> when there
	 * is no ad to display.  Otheriwse it will take up space appearing as an empty rectangle.
	 */
	private boolean hideWhenNoAd;
	
	/**
	 * The client's object listening to requests or <code>null</code> if they don't care.
	 */
	private AdListener listener;
		
	/**
	 * instance variable kept to know when the adview is onscreen or not.
	 */
	
	private boolean isOnScreen;
	
	/**
	 * posts do not work on the default handler when the adview is in a list (it is detached from the window).
	 * We have to keep track of our own handler.
	 * http://groups.google.com/group/android-developers/browse_thread/thread/77e7676122bb973a/b17cea02b8daf8dd
	 */
	static private Handler uiThreadHandler;
	
	/**
	 * emulator url to check
	 */
	static private final String ADMOB_EMULATOR_NOTICE = "http://api.admob.com/v1/pubcode/android_sdk_emulator_notice";
	static private boolean checkedForMessages = false;
	
	/**
	 * Implement this interface to receive notifications of ad activity.  Then call <code>setListener</code>
	 * to register for the events.
	 */
	public interface AdListener
	{
		/**
		 * Deprecated in favor of <code>onReceiveAd</code>.  It is the same except the ad view making
		 * the request is passed in.  When multiple ads are on the screen this differentiates which
		 * is about to be updated.
		 */
		@Deprecated
		public void onNewAd();
		
		/**
		 * Called when a new ad has been received and is about to be displayed.
		 * 
		 * @param adView is the view that received and will show the new ad.
		 */
		public void onReceiveAd( AdView adView );
		
		/**
		 * Called when an ad request fails to get a new ad.  This can happen for a number of reasons.
		 * For instance if there is no Internet connection (i.e. out of cell range and not connected to
		 * wifi).  Another common reason is no fill which means there are no ads to show at this moment.
		 * 
		 * @param adView is the view that did not receive the new ad.
		 */
		public void onFailedToReceiveAd( AdView adView );
	}
	
	/**
	 * Constructs an advertisement view manually (not from a layout XML file).
	 * 
	 * @param context is the application's context.
	 * 
	 * @see android.view.View#View(Context)
	 */
	public AdView( Context context )
	{
		this( context, null, 0 );
	}

	/**
	 * Constructs an advertisment view from a layout XML file.
	 * 
	 * @param context is the application's context.
	 * @param attrs are attributes set in the XML layout for this view.
	 * 
	 * @see android.view.View#View(android.content.Context, android.util.AttributeSet)
	 */
	public AdView( Context context, AttributeSet attrs )
	{
		this( context, attrs, 0 );
	}

	/**
	 * Constructs an advertisment view from a layout XML file.
	 * 
	 * @param context is the application's context.
	 * @param attrs are attributes set in the XML layout for this view.
	 * @param defStyle is the theme ID to apply to this view.
	 * 
	 * @see android.view.View#View(Context, AttributeSet, int)
	 */
	public AdView( Context context, AttributeSet attrs, int defStyle )
	{
		super( context, attrs, defStyle );
		
		isOnScreen = false;
		
		// The user can interact with the ad by clicking on it.
		setFocusable( true );
		setDescendantFocusability( ViewGroup.FOCUS_AFTER_DESCENDANTS );
		setClickable( true );
		
		// Apply any custom attributes from the XML layout.
		int tc = AdContainer.DEFAULT_TEXT_COLOR;
		int bc = AdContainer.DEFAULT_BACKGROUND_COLOR;
		
		if ( attrs != null )
		{
			String namespace = "http://schemas.android.com/apk/res/" + context.getPackageName();
			
			boolean testing = attrs.getAttributeBooleanValue( namespace, "testing", false );
			if ( testing )
			{
				AdManager.setInTestMode( testing );
			}
			
			tc = attrs.getAttributeUnsignedIntValue( namespace, "textColor", AdContainer.DEFAULT_TEXT_COLOR );
			bc = attrs.getAttributeUnsignedIntValue( namespace, "backgroundColor", AdContainer.DEFAULT_BACKGROUND_COLOR );
			
			keywords = attrs.getAttributeValue( namespace, "keywords" );
			
			int freshAdsEvery = attrs.getAttributeIntValue( namespace, "refreshInterval", 0 );
			setRequestInterval( freshAdsEvery );
			
			boolean isGoneWithoutAd = attrs.getAttributeBooleanValue( namespace, "isGoneWithoutAd", this.isGoneWithoutAd() );
			setGoneWithoutAd( isGoneWithoutAd );
		}
		
		setTextColor( tc );
		setBackgroundColor( bc );
				
		// Get the first ad to display.
		if ( super.getVisibility() == View.VISIBLE )
		{
			requestFreshAd();
		}
	}
	
    /**
	 * Updates this view with a new ad.
	 */
	public void requestFreshAd()
	{
	    // Get a message from AdMob to the developer here.  Only run once and only run on the emulator.
	    Context context = getContext();
	    if(AdManager.getUserId(context) == null && !checkedForMessages) // null means this is emulator.
	    {
	        // go get the developer messages.
	        checkedForMessages = true;
	        retrieveDeveloperMessage( context );
	    }
	    
		// Only get an ad when this view is visible and not already getting an ad.
		if ( super.getVisibility() != View.VISIBLE )
		{
			// Getting an ad when the view is hidden means it might not be shown to the user.
			// This greatly impairs AdMob's ability to optimize ads.
			Log.w( AdManager.LOG, "Cannot requestFreshAd() when the AdView is not visible.  Call AdView.setVisibility(View.VISIBLE) first." );
		}
		else if ( requestingFreshAd )
		{
    		if ( Log.isLoggable( AdManager.LOG, Log.DEBUG ) )
    		{
    			Log.d( AdManager.LOG, "Ignoring requestFreshAd() because we are already getting a fresh ad." );
    		}
		}
		else
		{
			requestingFreshAd = true;
			
			// get a uithreadHandler for post callbacks.
			if ( uiThreadHandler == null )
			{
			    uiThreadHandler = new Handler();
			}
			
			// Get the new ad on a background thread so the UI thread isn't blocked.
			new Thread() 
			{
				public void run()
				{
					try
					{
						// Get a new Ad from AdMob.
						Context context = getContext();
						Ad newAd = AdRequester.requestAd( context, keywords, searchQuery );
						
						if ( newAd != null )  // we got an ad back
						{
							synchronized ( this )
							{
								// Did we get a different ad than we're already showing?
								// always rotate if in test mode.
								if ( (ad != null) && newAd.equals( ad.getAd() ) && !AdManager.isInTestMode())
								{
					        		if ( Log.isLoggable( AdManager.LOG, Log.DEBUG ) )
					        		{
					        			Log.d( AdManager.LOG, "Received the same ad we already had.  Discarding it." );
					        		}
					        		
									requestingFreshAd = false;
								}
								else  // let's show our brand new ad!
								{
									// Create a view for the ad.
									final boolean firstAd = ( ad == null );
									final int visibility = AdView.super.getVisibility();
									
									final AdContainer newAdContainer = new AdContainer( newAd, context );
									newAdContainer.setBackgroundColor( getBackgroundColor() );
									newAdContainer.setTextColor( getTextColor() );
									newAdContainer.setVisibility( visibility );
									LayoutParams params = new LayoutParams( LayoutParams.FILL_PARENT, HEIGHT );
									newAdContainer.setLayoutParams( params );
									
									// Notify the client event listener.
									if ( listener != null )
									{
										try
										{
											listener.onNewAd();
											listener.onReceiveAd( AdView.this );
										}
										catch (Exception e)
										{
											Log.w( AdManager.LOG, "Unhandled exception raised in your AdListener.onReceiveAd.", e );
										}
									}
									
									// Force this view to show the newAdContainer view.
									uiThreadHandler.post( new Runnable() 
											{
												public void run() 
												{
													try
													{
														// Place the ad container into this view group.
														addView( newAdContainer );
														
														// Perform an animation to swap the new ad for the old.
														if ( visibility == View.VISIBLE )
														{
															if ( firstAd )
															{
																applyFadeIn( newAdContainer );
															}
															else
															{
																applyRotation( newAdContainer );
															}
														}
														else
														{
															// Just record the new ad for when this becomes visible.
															ad = newAdContainer;
														}
													}
													catch (Exception e)
													{
														Log.e( AdManager.LOG, "Unhandled exception placing AdContainer into AdView.", e );
													}
													finally
													{
														// Done requesting a new ad.
														requestingFreshAd = false;
													}
												}
											} );
								}
							}
						}
						else  // Did not get an ad back
						{
							// Notify the client event listener.
							if ( listener != null )
							{
								try
								{
									listener.onFailedToReceiveAd( AdView.this );
								}
								catch (Exception e)
								{
									Log.w( AdManager.LOG, "Unhandled exception raised in your AdListener.onFailedToReceiveAd.", e );
								}
							}
							
							requestingFreshAd = false;
						}
					}
					catch (Exception e)
					{
						Log.e( AdManager.LOG, "Unhandled exception requesting a fresh ad.", e );
						requestingFreshAd = false;
					}
				}			
			}.start();
		}
	}
    
	/**
	 * @return The number of seconds between displaying new ads.  The default is 0 which
	 *  means new ads will not be displayed unless <code>requestFreshAd</code> is called.
	 */
	public int getRequestInterval()
	{
		int requestIntervalSecs = requestInterval / 1000;
		return requestIntervalSecs;
	}

	/**
	 * Makes this view display a new ad every <code>requestInterval</code> seconds.  This
	 * is a convenience method for ads on screens that are displayed for a long period of
	 * time.
	 * <p>
	 * <b>This is usually not a good idea.</b>  More ads != More money.  A user is unlikely
	 * to stop what they are doing.  It is best to create a fluid experience by placing ads
	 * at natural break points in your application.  For example ads in games perform
	 * best -- by far -- on game over screens or between levels.
	 * 
	 * @param requestInterval is how many seconds to wait before replacing this ad with a
	 *  new one.  If you use this method 60 seconds is a good value.  Set this to 0 to turn
	 *  this feature off (the default).
	 */
	public void setRequestInterval( int requestInterval )
	{
		final int minSecs = 15;
		final int maxSecs = 600;  // 10 minutes
		
		if ( requestInterval <= 0 )
		{
			requestInterval = 0;
		}
		else if ( requestInterval < minSecs )
		{
			// Too often.  Cannot request ads more frequently than minSecs apart.
			AdManager.clientError( "AdView.setRequestInterval(" + requestInterval + ") seconds must be >= " + minSecs );
		}
		else if ( requestInterval > maxSecs )
		{
			// Too infrequent.  If setting this value there is no reason to have ads update more than maxSecs apart.
			AdManager.clientError( "AdView.setRequestInterval(" + requestInterval + ") seconds must be <= " + maxSecs );
		}
		
		int requestIntervalMillisecs = requestInterval * 1000;
		this.requestInterval = requestIntervalMillisecs;
		
		// Set the timer to handle requesting fresh ads.
		if ( requestInterval == 0 )
		{
			// Stop the current timer (if there is one).
			manageRequestIntervalTimer( false );
		}
		else
		{
			// Start the timer.
			Log.i( AdManager.LOG, "Requesting fresh ads every " + requestInterval + " seconds." );
			manageRequestIntervalTimer( true );
		}
	}

	/**
	 * Manages the background task that requests fresh ads every so often.  Since the background
	 * task requires a timer resource calling this method will free it or use it as required.
	 * 
	 * @param start when <code>true</code> will start requesting ads every <code>requestInterval</code>
	 *  and when <code>false</code> will free the timer resource.
	 *  
	 * @see #requestIntervalTimer
	 */
	private void manageRequestIntervalTimer( boolean start )
	{
		synchronized ( this )
		{
			if ( start && (requestInterval > 0) )
			{
				// Start the timer.
				if ( requestIntervalTimer == null )  // otherwise the timer is already running
				{
					requestIntervalTimer = new Timer();
					requestIntervalTimer.schedule( new TimerTask()
							{
								public void run()
								{
									if ( Log.isLoggable( AdManager.LOG, Log.DEBUG ) )
									{
										int secs = requestInterval / 1000;
										
						        		if ( Log.isLoggable( AdManager.LOG, Log.DEBUG ) )
						        		{
						        			Log.d( AdManager.LOG, "Requesting a fresh ad because a request interval passed (" + secs + " seconds)." );
						        		}
									}
									
									requestFreshAd();
								}
							},
							requestInterval, requestInterval );
				}
			}
			else if ( (start == false) || (requestInterval == 0) )
			{
				// Stop the currently running timer.
				if ( requestIntervalTimer != null )
				{
					requestIntervalTimer.cancel();
					requestIntervalTimer = null;
				}
			}
		}
	}
	
	/**
	 * Called when the window containing this ad gains or loses focus.  The ad uses this event
	 * to clean up resources.
	 *
	 * @see android.view.View#onWindowFocusChanged(boolean)
	 */
	@Override
	public void onWindowFocusChanged( boolean hasWindowFocus )
	{
		// Start or stop the request fresh ad timer.
		manageRequestIntervalTimer( hasWindowFocus );
	}
	
	/**
	 * Sets the ad's text color.
	 * <p>
	 * This can also be set in the XML layout by using the <code>textColor</code> attribute.
	 * 
	 * @param color ARGB value for the ad text.
	 * 
	 * @see android.R.color
	 */
	public void setTextColor( int color )
	{
		this.textColor = 0xFF000000 | color;  // Remove the alpha channel
		
		if ( ad != null )
		{
			ad.setTextColor( color );
		}
		
		invalidate();
	}
	
	/**
	 * @return The text ARGB color value for the ad text.
	 * 
	 * @see android.R.color
	 */
	public int getTextColor()
	{
		return textColor;
	}
	
	/**
	 * Sets the ad's background color.  A "shine" is applied over it which appears as
	 * to reflect light from above the ad.
	 * <p>
	 * This can also be set in the XML layout by using the <code>backgroundColor</code> attribute.
	 * 
	 * @param color ARGB value for the ad.
	 * 
	 * @see android.R.color
	 */
	public void setBackgroundColor( int color )
	{
		//this.backgroundColor = 0xFF000000 | color;  // Remove the alpha channel
		this.backgroundColor = color;
		if ( ad != null )
		{
			ad.setBackgroundColor( color );
		}
		
		invalidate();
	}
	
	/**
	 * @return The background ARGB color value for the ad.
	 * 
	 * @see android.R.color
	 */
	public int getBackgroundColor()
	{
		return backgroundColor;
	}

	/**
	 * @return The optional set of keywords that describe this application and where the ad
	 *  appears or <code>null</code>.
	 */
	public String getKeywords()
	{
		return keywords;
	}

	/**
	 * Keywords are hints for the ad server that describe your application and the
	 * screen this ad appears on.  For example if the ad was at the bottom of a game
	 * of checkers you might set this to "checkers game".
	 * <p>
	 * This can also be set in the XML layout by using the <code>keywords</code> attribute.
	 * 
	 * @param keywords is an optional set of words that describe your application and where the ad appears.
	 *  Keywords can be removed by setting this to <code>null</code>.
	 */
	public void setKeywords( String keywords )
	{
		this.keywords = keywords;
	}

	/**
	 * @return The optional search query this ad appears within or <code>null</code>.
	 */
	public String getSearchQuery()
	{
		return searchQuery;
	}

	/**
	 * If this ad appears alonside search results provide the search query here.  Search queries
	 * differ from keywords in that they are more specific.
	 * 
	 * @param searchQuery is an optional search query if this ad appears within search results.
	 */
	public void setSearchQuery( String searchQuery )
	{
		this.searchQuery = searchQuery;
	}

	/**
	 * Sets the visibility of this view when there is no ad.  Reasons there might not be an
	 * ad include it has just been added, the server does not return an ad, or the user is not
	 * in cell range.  In these cases you may want to set this to <code>true</code> which 
	 * would make its visibililty <code>GONE</code>.  Then once an ad is received it will
	 * change to <code>setVisibility</code>.
	 * <p>
	 * If you want this view to always take up space, but want the color scheme to be different
	 * with and without an ad, implement <code>AdListener</code>.  Start with the color you
	 * want when there is no ad then change it when a new ad comes in.
	 * 
	 * @param hide when <code>true</code> will stop this view from taking up any screen space
	 *  until there is an ad.  When <code>false</code> (the default) it will appear as an
	 *  empty rectangle.
	 */
	public void setGoneWithoutAd( boolean hide )
	{
		this.hideWhenNoAd = hide;
	}

	/**
	 * Returns if <code>getVisibility</code> is <code>GONE</code> when there is no ad.  When
	 * an ad is obtained from the servers this means nothing.
	 * 
	 * @return <code>true</code> if this view is <code>GONE</code> until there is an ad or
	 *  <code>false</code> if <code>setVisibility</code> will be respected.
	 */
	public boolean isGoneWithoutAd()
	{
		return hideWhenNoAd;
	}

	/**
	 * Set the enabled state of this view.
	 * 
	 * @param visibility is one of <code>VISIBLE</code>, <code>INVISIBLE</code>, or <code>GONE</code>.
	 */
	@Override
	public void setVisibility( int visibility )
	{
		// For some reason FrameView does not forward visiblity to it children.  So we manually
		// override it to do so.  That way when this view is invisible or gone we don't show an ad.
		int originalVisiblity = super.getVisibility();
		
		if ( originalVisiblity != visibility )
		{
			synchronized ( this )
			{
				// Forward the visibility event to all the children.
				int children = getChildCount();
				
				for ( int i = 0; i < children; i++ )
				{
					View child = getChildAt( i );
					child.setVisibility( visibility );
				}
				
				// Continue processing the event.
				super.setVisibility( visibility );
				
				// Get or remove ads depending on visiblity.
				if ( visibility == View.VISIBLE )
				{
					requestFreshAd();
				}
				else
				{
					// Remove the old ad so we fade into the new one.
					removeView( ad );
					ad = null;
					invalidate();
				}
			}
		}
	}

	/**
	 * Gets the enabled state of this view.  This is not necessarily what was set in 
	 * <code>setVisibility</code>.  If there is no ad and if <code>isGoneWithoutAd</code> returns
	 * <code>true</code> this will be <code>GONE</code>.
	 * 
	 * @return The visibility is one of <code>VISIBLE</code>, <code>INVISIBLE</code>, or <code>GONE</code>.
	 *
	 * @see #isGoneWithoutAd()
	 */
	@Override
	public int getVisibility()
	{
		if ( hideWhenNoAd && (hasAd() == false) )
		{
			return GONE;
		}
		else
		{
			return super.getVisibility();
		}
	}

	/**
	 * Makes the <code>listener</code> receive notifications of when a new ad has arrived
	 * from AdMob and this view is about to display it.  The listener is a good place to
	 * change the colors of an ad or hide the view when there is no ad.
	 * 
	 * @param listener is the object to receive event notifications or <code>null</code>
	 *  to stop getting notifications.
	 */
	public void setListener( AdListener listener )
	{
		synchronized ( this )
		{
			this.listener = listener;
		}
	}

	/**
	 * Says whether this view is showing an ad or not.  Ads will not exist until they are
	 * downloaded from the servers which takes a fraction of a second.  It is also possible
	 * there are no ads to display or there is a connection problem (like the user does not
	 * have a signal).
	 * 
	 * @return <code>true</code> if this view is showing an ad or <code>false</code> if it
	 *  is empty.
	 */
	public boolean hasAd()
	{
		return ad != null;
	}
	
	/* (non-Javadoc)
	 * @see android.widget.RelativeLayout#onMeasure(int, int)
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		
		// what happens if I just assign myself a particular width and height?
		int width = getMeasuredWidth();
		setMeasuredDimension(width, HEIGHT);
	}

	/* (non-Javadoc)
     * @see android.view.View#onAttachedToWindow()
     */
    @Override
    protected void onAttachedToWindow()
    {
    	isOnScreen = true;
    	super.onAttachedToWindow();
    }
    
    /* (non-Javadoc)
	 * @see android.view.View#onDetachedFromWindow()
	 */
	@Override
	protected void onDetachedFromWindow()
	{
    	isOnScreen = false;
		super.onDetachedFromWindow();
	}

	/**
	 * private method to read from the developer message url and display it to the user
	 * in the emulator logcat output.
	 */
	private void retrieveDeveloperMessage(Context context)
	{
        // need to build full parameter list for the URL.
        BufferedReader reader = null;
        try
        {
            String paramString = AdRequester.buildParamString(context, null, null);
            
            StringBuilder sb = new StringBuilder();
            sb.append(ADMOB_EMULATOR_NOTICE);
            sb.append("?");
            sb.append(paramString);
            
            URL devMessageURL = new URL(sb.toString());
            
            URLConnection connection = devMessageURL.openConnection();
            
            connection.connect();
            // now get the body as JSON.
            StringBuilder content = new StringBuilder();
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while((line = reader.readLine()) != null)
            {
                content.append(line);
            }
            JSONObject jsonObject = new JSONObject(new JSONTokener(content.toString()));
            
            // get the data key.
            String message = jsonObject.getString("data");
            if(message != null)
            {
                Log.e(AdManager.LOG, message);
            }
        }
        catch(Exception e)
        {
        	// Ignore.
        }
        finally
        {
            try
            {
                if(reader != null)
                {
                    reader.close();
                }
            }
            catch(Exception e)
            {
            	// deliberately empty
            }
        }
	}
	
	/**
	 * Creates an animation that fades the first ad into visibility.  Since the ad has to be
	 * fetched from the servers it is unlikely to appear when the screen is first rendered.
	 * This effect makes it appear on the screen gracefully rather than just suddenly appearing.
	 * 
	 * @param newAd is the first ad to show and will become <code>this.ad</code>.
	 */
	private void applyFadeIn( final AdContainer newAd )
	{
		this.ad = newAd;
		
		if(isOnScreen)
		{
			AlphaAnimation animation = new AlphaAnimation( 0.0f, 1.0f );
			animation.setDuration( ANIMATION_DURATION / 3 );
			animation.startNow();
			animation.setFillAfter( true );
			animation.setInterpolator( new AccelerateInterpolator() );
	
			startAnimation( animation );
		}			
	}
	
	/**
	 * Performs an animation that switches out the current ad for a new one.
	 * 
	 * @param newAd is the new ad which will replace the current one (i.e. <code>ad</code>).
	 */
	private void applyRotation( final AdContainer newAd )
	{
		// Don't show the new view until it rotates in (part of SwapViews).
		newAd.setVisibility( View.GONE );

		// Find the center of the container
		final float centerX = getWidth() / 2.0f;
		final float centerY = getHeight() / 2.0f;
		final float zDepth = ANIMATION_Z_DEPTH_PERCENTAGE * getWidth();

		// Create a new 3D rotation with the supplied parameter
		// The animation listener is used to trigger the next animation
		final Rotate3dAnimation rotation = new Rotate3dAnimation( 0, -90,
				centerX, centerY, zDepth, true );
		rotation.setDuration( ANIMATION_DURATION );
		rotation.setFillAfter( true );
		rotation.setInterpolator( new AccelerateInterpolator() );
		rotation.setAnimationListener( new Animation.AnimationListener()
				{
					public void onAnimationStart( Animation animation )
					{
					}
		
					public void onAnimationEnd( Animation animation )
					{
						// At this point the animation is half way done.  The old ad
						// disappeared and the new ad needs to come into view.
						post( new SwapViews( newAd ) );
					}
		
					public void onAnimationRepeat( Animation animation )
					{
					}
				} );

		startAnimation( rotation );
	}

	/**
	 * This class is responsible for swapping the views and start the second
	 * half of the animation.
	 */
	private final class SwapViews
		implements Runnable
	{
		private AdContainer newAd;
		private AdContainer oldAd;
		
		public SwapViews( AdContainer newAd )
		{
			this.newAd = newAd;
		}
		
		public void run()
		{
		    // we need to worry about other threads affecting ad var
		    // so we save our own reference here.
		    oldAd = ad;
		    
			// Swap new ad for old.
		    if (oldAd != null)
			{
		        oldAd.setVisibility( View.GONE );
			}
			newAd.setVisibility( View.VISIBLE );

			// Set rotation effects.
			final float centerX = getWidth() / 2.0f;
			final float centerY = getHeight() / 2.0f;
			final float zDepth = ANIMATION_Z_DEPTH_PERCENTAGE * getWidth();
			Rotate3dAnimation rotation = new Rotate3dAnimation( 90, 0,
					centerX, centerY, zDepth, false );
			rotation.setDuration( ANIMATION_DURATION );
			rotation.setFillAfter( true );
			rotation.setInterpolator( new DecelerateInterpolator() );
			rotation.setAnimationListener( new Animation.AnimationListener()
			{
				public void onAnimationStart( Animation animation )
				{
				}
	
				public void onAnimationEnd( Animation animation )
				{
					// At this point the new ad is visible and the old ad can be thrown out.
				    // check against null in case ad was already set to GONE and set to null
				    if(oldAd != null)
				    {
				        removeView( oldAd );
				        oldAd.recycleBitmaps();
				    }
					// now we can write over 
					ad = newAd;
				}
	
				public void onAnimationRepeat( Animation animation )
				{
				}
			} );

			startAnimation( rotation );
		}
	}
}

/**
 * An animation that rotates the view on the Y axis between two specified
 * angles. This animation also adds a translation on the Z axis (depth) to
 * improve the effect.
 */
class Rotate3dAnimation
	extends Animation
{
	// This entire class was taken from the Android sample ApiDemos.  It is found
	// in Transitions3D.java.
	
	private final float mFromDegrees;
	private final float mToDegrees;
	private final float mCenterX;
	private final float mCenterY;
	private final float mDepthZ;
	private final boolean mReverse;
	private Camera mCamera;

	/**
	 * Creates a new 3D rotation on the Y axis. The rotation is defined by its
	 * start angle and its end angle. Both angles are in degrees. The rotation
	 * is performed around a center point on the 2D space, definied by a pair of
	 * X and Y coordinates, called centerX and centerY. When the animation
	 * starts, a translation on the Z axis (depth) is performed. The length of
	 * the translation can be specified, as well as whether the translation
	 * should be reversed in time.
	 * 
	 * @param fromDegrees the start angle of the 3D rotation
	 * @param toDegrees the end angle of the 3D rotation
	 * @param centerX the X center of the 3D rotation
	 * @param centerY the Y center of the 3D rotation
	 * @param reverse true if the translation should be reversed, false otherwise
	 */
	public Rotate3dAnimation( float fromDegrees, float toDegrees,
			float centerX, float centerY, float depthZ, boolean reverse )
	{
		mFromDegrees = fromDegrees;
		mToDegrees = toDegrees;
		mCenterX = centerX;
		mCenterY = centerY;
		mDepthZ = depthZ;
		mReverse = reverse;
	}

	@Override
	public void initialize( int width, int height, int parentWidth,
			int parentHeight )
	{
		super.initialize( width, height, parentWidth, parentHeight );
		mCamera = new Camera();
	}

	@Override
	protected void applyTransformation( float interpolatedTime, Transformation t )
	{
		final float fromDegrees = mFromDegrees;
		float degrees = fromDegrees
				+ ((mToDegrees - fromDegrees) * interpolatedTime);

		final float centerX = mCenterX;
		final float centerY = mCenterY;
		final Camera camera = mCamera;

		final Matrix matrix = t.getMatrix();

		camera.save();
		if ( mReverse )
		{
			camera.translate( 0.0f, 0.0f, mDepthZ * interpolatedTime );
		}
		else
		{
			camera.translate( 0.0f, 0.0f, mDepthZ * (1.0f - interpolatedTime) );
		}
		camera.rotateY( degrees );
		camera.getMatrix( matrix );
		camera.restore();

		matrix.preTranslate( -centerX, -centerY );
		matrix.postTranslate( centerX, centerY );
	}
}
