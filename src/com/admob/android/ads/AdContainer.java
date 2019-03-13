package com.admob.android.ads;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * The actual view containing an advertisment.  It is always embedded within an <code>AdView</code>
 * (which is responsible for animations).  This class contains all of the ad rendering logic.
 * 
 * @see AdView
 */
class AdContainer
	extends RelativeLayout 
	implements AnimationListener, Ad.NetworkListener
{
    /**
     * The maximum number of pixels wide the interior of an ad can be. Screens
     * wider than this will have the background stretch across them with the ad
     * horizontally centered and drawn to this size.
     */
    public static final int MAX_WIDTH = 320;

    /**
     * The starting color for the ad background. A gradient is applied over this
     * to give it a shine.
     */
    private static final int GRADIENT_BACKGROUND_COLOR = Color.WHITE;

    /**
     * Unless <code>setBackgroundColor</code> is called the ad's background,
     * what is behind the gradient, will be this color.
     */
    public static final int DEFAULT_BACKGROUND_COLOR = Color.BLACK;

    /**
     * The alpha value at the very top of the gradient creating the shine for
     * the ad. The alpha gradually becomes opaque once it gets
     * <code>GRADIENT_STOP</code> of the way down the ad. This is 50% for the
     * iPhone.
     */
    private static final int GRADIENT_TOP_ALPHA = (int) (255 * 0.50); // 50% opaque

    /**
     * The percentage of the view's height from the top at which the gradient
     * background becomes fully opaque. This makes what is above this point look
     * like a light is shinging from above and reflecting. What is below this
     * point is in the shadow. This is 0.4375 for the iPhone.
     */
    private static final double GRADIENT_STOP = 0.4375;

    /**
     * The number of pixels surrounding the icon image and text on all sides.
     */
    private static final int PADDING_DEFAULT = 8;

    /**
     * Unless <code>setTextColor</code> is called the ad's text will be this
     * color.
     */
    public static final int DEFAULT_TEXT_COLOR = Color.WHITE;

    /**
     * The font used to render the ad's text. iPhone ads use Helvetica-Bold
     * which is in the Sans Serif family.
     */
    private static final Typeface AD_FONT = Typeface.create(
            Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * The font size used to render the ad's text. iPhone ads uses 13 but the
     * actual height is 16 pixels because of letters like 'y' which dip below
     * the font.
     */
    private static final float AD_FONT_SIZE = 13.0f;

    /**
     * The "Ads by AdMob" text that appears below text ads that are one line
     * long.
     */
    private static final String ADS_BY_ADMOB = "Ads by AdMob";

    /**
     * The font used to render "Ads by AdMob". iPhone ads use Helvetica.
     */
    private static final Typeface ADS_BY_ADMOB_FONT = Typeface.create(
            Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * The font size used to render the ad's text. iPhone ads uses 9.5 pixels.
     */
    private static final float ADS_BY_ADMOB_FONT_SIZE = 9.5f;

    /**
     * The color of a mask applied over the entire view when the user presses
     * down on the ad to signal they are going to click it. iPhone uses a 0.5
     * alpha blended with grey 1/2 way on the greyscale (0x88888888).
     */
    private static final int HIGHLIGHT_COLOR = 0xFFFFB400; // Android yellow

    /**
     * The color applied to a highlighted ad before the "shine" of
     * <code>HIGHLIGHT_COLOR</code> is applied. This color is analygous to
     * <code>GRADIENT_BACKGROUND_COLOR</code>.
     */
    private static final int HIGHLIGHT_BACKGROUND_COLOR = 0xFFEE7F27; // android orange
    
    /**
     * The color of the ad text when it is being clicked. This will be writter
     * over the <code>HIGHLIGHT_COLOR</code>.
     */
    private static final int HIGHLIGHT_TEXT_COLOR = Color.BLACK;

    /**
     * Color of the ring around the ad when it has the focus.
     */
    private static final int FOCUS_COLOR = HIGHLIGHT_BACKGROUND_COLOR;

    /**
     * Width of the ring around the ad when it has the focus.
     */
    private static final float FOCUS_WIDTH = 3.0f;

    /**
     * Radius of the rounded corners to the ring around the ad when it has the
     * focus.
     */
    private static final float FOCUS_CORNER_ROUNDING = 3.0f;

    /**
     * IDs used for alignment by relative layout.
     */
    private static final int ICON_ID = 1;
    private static final int AD_TEXT_ID = 2;
    private static final int ADMOB_TEXT_ID = 3;

    /**
     * these following values for use by the animation to grow/shrink the action
     * icon.
     */
    private static final float PULSE_INITIAL_SCALE = 1.0f;
    private static final float PULSE_ANIMATION_DURATION = 0.5f;
    private static final float PULSE_SHRUNKEN_SCALE = 0.001f;
    private static final float PULSE_GROWN_SCALE = 1.2f;
    private static final float PULSE_GROW_KEY_TIME = 0.4f;

    private static final int NUM_MILLIS_IN_SECS = 1000;
    
    /**
     * The color used in the background shine of the ad. The user can set this
     * to make the ad appear greener, for example.
     */
    private int backgroundColor = DEFAULT_BACKGROUND_COLOR;

    /**
     * The color of the ad's text and of "Ads by AdMob".
     */
    private int textColor = DEFAULT_TEXT_COLOR;

    private BitmapDrawable defaultBackground;
    private BitmapDrawable focusedBackground;
    private BitmapDrawable pressedBackground;
    // this needed because view could have been pressed from focused or 
    // default state.
    private Drawable lastBackground; 

    /**
     * The ad displayed by this view. If this is <code>null</code> this view
     * will not be visible.
     */
    private Ad ad;

    private TextView adTextView;
    private TextView adMobBrandingTextView;
    private ImageView iconView;
    private ProgressBar activityIndicator;
    private int padding;
    private boolean clickInProgress;
    
    /**
     * Constructs an advertisement view manually (not from a layout XML file).
     * 
     * @param ad
     *            is the object containing an ad fetched from AdMob servers.
     * @param context
     *            is the application's context.
     * 
     * @see android.view.View#View(Context)
     */
    public AdContainer(Ad ad, Context context)
    {
        super(context);
        init(ad, context, null, 0);
    }

    /**
     * Constructs an advertisment view from a layout XML file.
     * 
     * @param ad
     *            is the object containing an ad fetched from AdMob servers.
     * @param context
     *            is the application's context.
     * @param attrs
     *            are attributes set in the XML layout for this view.
     * 
     * @see android.view.View#View(android.content.Context,
     *      android.util.AttributeSet)
     */
    public AdContainer(Ad ad, Context context, AttributeSet attrs)
    {
        this(ad, context, attrs, 0);
    }

    /**
     * Constructs an advertisment view from a layout XML file.
     * 
     * @param ad
     *            is the object containing an ad fetched from AdMob servers.
     * @param context
     *            is the application's context.
     * @param attrs
     *            are attributes set in the XML layout for this view.
     * @param defStyle
     *            is the theme ID to apply to this view.
     * 
     * @see android.view.View#View(Context, AttributeSet, int)
     */
    public AdContainer(Ad ad, Context context,
            AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        init(ad, context, attrs, defStyle);
    }

    /**
     * The constructor implementation for this class.
     * <p>
     * This was extracted from the <code>AdContainer</code> constructors because
     * of a bug in the Android Eclipse plug-in visual layout editor. In the
     * editor we cannot call the constructor
     * <code>View(Context, AttributeSet, int)</code> because it throws a
     * <code>NullPointerException</code>.
     * 
     * @param ad
     *            is the object containing an ad fetched from AdMob servers.
     * @param context
     *            is the application's context.
     * @param attrs
     *            are attributes set in the XML layout for this view.
     * @param defStyle
     *            is the theme ID to apply to this view.
     */
    private void init(Ad ad, Context context, AttributeSet attrs, int defStyle)
    {
        this.ad = ad;
        ad.setNetworkListener(this);

        defaultBackground = null;
        pressedBackground = null;
        focusedBackground = null;
        activityIndicator = null;
        clickInProgress = false;
        
        // The user can interact with the ad by clicking on it.
        if (ad != null)
        {
            setFocusable(true);
            setClickable(true);

            Bitmap icon = ad.getIcon();
            iconView = null;
            padding = PADDING_DEFAULT;
            if(icon != null)
            {
                padding = (AdView.HEIGHT - icon.getHeight())/2;
                // put it into a view and push it into the ad.
                iconView = new ImageView(context);
                iconView.setImageBitmap(icon);
                // set the attributes.
                LayoutParams params = new LayoutParams(icon.getWidth(), icon
                        .getHeight());
                params.setMargins(padding, padding, 0, padding);
                iconView.setLayoutParams(params);
                iconView.setId(ICON_ID);
                addView(iconView);

                // create the activity indicator here as well but do not 
                // muck with it.
                activityIndicator = new ProgressBar(context);
                activityIndicator.setIndeterminate(true);
                activityIndicator.setId(ICON_ID); // for alignment.
                activityIndicator.setLayoutParams(params);
                activityIndicator.setVisibility(INVISIBLE);
                addView(activityIndicator);
            }
            // need to get things oriented vertically here.

            // now build the text layout with the two adTextViews, right oriented.
            adTextView = new TextView(context);
            adTextView.setText(ad.getText());
            adTextView.setTypeface(AD_FONT);
            adTextView.setTextColor(textColor);
            adTextView.setTextSize(AD_FONT_SIZE);
            adTextView.setId(AD_TEXT_ID);

            LayoutParams adTextViewParams = new LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT,
                    ViewGroup.LayoutParams.FILL_PARENT);
            // if the icon was not null then align with that too.
            if(icon != null)
            {
                adTextViewParams.addRule(RIGHT_OF, ICON_ID);
            }
            adTextViewParams.setMargins(padding, padding, padding, padding);
            adTextViewParams.addRule(ALIGN_PARENT_RIGHT);
            adTextViewParams.addRule(ALIGN_PARENT_TOP);
            adTextView.setLayoutParams(adTextViewParams);

            addView(adTextView);

            // now text layout for the ads by admob.
            adMobBrandingTextView = new TextView(context);
            adMobBrandingTextView.setGravity(Gravity.RIGHT);
            adMobBrandingTextView.setText(AdMobLocalizer.localize(ADS_BY_ADMOB));
            adMobBrandingTextView.setTypeface(ADS_BY_ADMOB_FONT);
            adMobBrandingTextView.setTextColor(textColor);
            adMobBrandingTextView.setTextSize(ADS_BY_ADMOB_FONT_SIZE);
            adMobBrandingTextView.setId(ADMOB_TEXT_ID);
            LayoutParams adMobBrandingTextViewParams = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            adMobBrandingTextViewParams.setMargins(0, 0, padding, padding);
            adMobBrandingTextViewParams.addRule(ALIGN_PARENT_RIGHT);
            adMobBrandingTextViewParams.addRule(ALIGN_PARENT_BOTTOM);
            adMobBrandingTextView.setLayoutParams(adMobBrandingTextViewParams);

            addView(adMobBrandingTextView);
        }

        // Apply any custom attributes from the XML layout.
        int tc = DEFAULT_TEXT_COLOR;
        int bc = DEFAULT_BACKGROUND_COLOR;

        if ( attrs != null )
        {
            String namespace = "http://schemas.android.com/apk/res/" +
            context.getPackageName();

            tc = attrs.getAttributeUnsignedIntValue( namespace, "textColor",
                    DEFAULT_TEXT_COLOR );
            bc = attrs.getAttributeUnsignedIntValue( namespace,
                    "backgroundColor", DEFAULT_BACKGROUND_COLOR );
        }

        setTextColor( tc );
        setBackgroundColor( bc );
    }

    /**
     * Sets the ad's text color.
     * 
     * @param color
     *            ARGB value for the ad text.
     * 
     * @see android.R.color
     */
    public void setTextColor(int color)
    {
        this.textColor = 0xFF000000 | color; // Remove the alpha channel
        if(adTextView != null)
        {
        	adTextView.setTextColor(textColor);
        }
        if(adMobBrandingTextView != null)
        {
        	adMobBrandingTextView.setTextColor(textColor);
        }
        postInvalidate();
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
     * Sets the ad's background color. A "shine" is applied over it which
     * appears as to reflect light from above the ad.
     * 
     * @param color
     *            ARGB value for the ad.
     * 
     * @see android.R.color
     */
    public void setBackgroundColor(int color)
    {
    	// Remove the alpha channel
        //this.backgroundColor = 0xFF000000 | color;
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
     * @return The ad object shown by this view or <code>null</code> if this
     *         view is empty.
     */
    protected Ad getAd()
    {
        return ad;
    }

    /* (non-Javadoc)
     * @see android.view.View#onSizeChanged(int, int, int, int)
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);

        if(adMobBrandingTextView != null && adTextView != null)
        {
	        int brandingVisibility = adMobBrandingTextView.getVisibility();
	        if (w > 0)
	        {
	            if (w <= 128)
	            {
	                adTextView.setTextSize(AD_FONT_SIZE * 0.70f);
	                brandingVisibility = GONE;
	            } else if (w <= 176)
	            {
	                adTextView.setTextSize(AD_FONT_SIZE * 0.8f);
	                brandingVisibility = VISIBLE;
	                adMobBrandingTextView.setTextSize(ADS_BY_ADMOB_FONT_SIZE * 0.8f);
	            } else
	            {
	                adTextView.setTextSize(AD_FONT_SIZE);
	                brandingVisibility = VISIBLE;
	                adMobBrandingTextView.setTextSize(ADS_BY_ADMOB_FONT_SIZE);
	            }
	        }
	        // we have to do a bit of calculation ourselves here?  we were having trouble with 
	        // the 176 wide ad having multiple lines of ad text but not killing off the 
	        // branding text.
	        // adTextView.getWidth returns 0.0
	        // so we must calculate the estimated width (our width minus the
	        // icon width minus the padding.
	
	        if(brandingVisibility == VISIBLE)
	        {
	            Typeface tf = adTextView.getTypeface();
	            String text = ad.getText();
	            if(text != null)
	            {
	                Paint paint = new Paint();
	                paint.setTypeface(tf);
	                float textSize = adTextView.getTextSize();
	                paint.setTextSize(textSize);
	                float textViewWidth = paint.measureText(text);
	                float adTextViewWidth = w - padding * 2;
	                if(iconView != null)
	                {
	                    adTextViewWidth -= (iconView.getWidth() + padding);
	                }
	                if(textViewWidth > adTextViewWidth)
	                {
	                    // let's hide the adMobBrandingTextView.
	                    brandingVisibility = GONE;
	                }
	            }
	        }
	        adMobBrandingTextView.setVisibility(brandingVisibility);
        }
        
        // generate the drawable background.
        if(w != 0 && h != 0)
        {
        	Rect r = new Rect(0,0,w,h);

    		//defaultBackground = generateBackgroundDrawable(r, GRADIENT_BACKGROUND_COLOR, backgroundColor);
        	defaultBackground = generateBackgroundDrawable(r, 0x00000000, 0x00000000);
            pressedBackground = generateBackgroundDrawable(r, HIGHLIGHT_BACKGROUND_COLOR, HIGHLIGHT_COLOR);
            focusedBackground = generateBackgroundDrawable(r, GRADIENT_BACKGROUND_COLOR, backgroundColor, true);
	            
            setBackgroundDrawable(defaultBackground);
        }
    }

    void recycleBitmaps()
    {
    	if(defaultBackground != null)
    	{
    		BitmapDrawable temp = defaultBackground;
    		defaultBackground = null;
    		recycleBitmapDrawable(temp);
    	}
    	if(pressedBackground != null)
    	{
    		BitmapDrawable temp = pressedBackground;
    		pressedBackground = null;
    		recycleBitmapDrawable(temp);
    	}
    	if(focusedBackground != null)
    	{
    		BitmapDrawable temp = focusedBackground;
    		focusedBackground = null;
    		recycleBitmapDrawable(temp);
    	}
    }
    
    private void recycleBitmapDrawable(BitmapDrawable bitmapDrawable)
    {
    	if(bitmapDrawable != null)
    	{
    		Bitmap b = bitmapDrawable.getBitmap();
    		if(b != null)
    		{
    			b.recycle();
    		}
    	}
    }
    
    
    /**
     * Draws the background for the ad. Drawing this is the first step in
     * drawing an ad.
     * 
     * @param c
     *            is the canvas the background is drawn onto.
     * @param r
     *            is the dimensions to draw the background into.
     * @param background
     *            color is the first color painted with the <code>color</code>
     *            being laid over it in the gradient. It is the color seen at
     *            the very top of the ad.
     * @param color
     *            is the background color of the ad. If this ad is being clicked
     *            it should be the standard Android orange highlight color.
     */
    private static void drawBackground(Canvas c, Rect r, int backgroundColor,
            int color)
    {
        // First draw an opaque background.
        /*Paint paint = new Paint();
        paint.setColor(backgroundColor);
        paint.setAntiAlias(true);
        c.drawRect(r, paint);

        // Draw a "shine" or reflection from above on the top half. It is a
        // gradient that
        // becomes fully opaque part way down the ad.
        int topColor = Color.argb(GRADIENT_TOP_ALPHA, Color.red(color), Color
                .green(color), Color.blue(color));
        int[] gradientColors = { topColor, color };
        GradientDrawable shine = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, gradientColors);

        int stop = (int) (r.height() * GRADIENT_STOP) + r.top;
        shine.setBounds(r.left, r.top, r.right, stop);
        shine.draw(c);

        // Draw the "shadow" below the reflection. It is a fully opaque section
        // below
        // where the gradient stops.
        Rect shadowRect = new Rect(r.left, stop, r.right, r.bottom);
        Paint shadowPaint = new Paint();
        shadowPaint.setColor(color);
        c.drawRect(shadowRect, shadowPaint);*/
    }

    private BitmapDrawable generateBackgroundDrawable(Rect r, int backgroundColor, int color)
    {
        return generateBackgroundDrawable(r, backgroundColor, color, false);
    }

    private BitmapDrawable generateBackgroundDrawable(Rect r, int backgroundColor, int color, boolean addFocusRing)
    {
        // Create a bitmap, pop the background color plus the shine on top, 
        // create a bitmap drawable, return.
    	try
    	{
	        Bitmap bitmap = Bitmap.createBitmap(r.width(), r.height(), Bitmap.Config.ARGB_8888);
	        Canvas canvas = new Canvas(bitmap);
	
	        drawBackground(canvas, r, backgroundColor, color);
	
	        if(addFocusRing)
	        {
	            drawFocusRing(canvas, r);
	        }
	
	        return new BitmapDrawable(bitmap);
    	}
    	catch(Throwable t)
    	{
    		return null;
    	}
    }

    /* (non-Javadoc)
     * @see com.admob.android.ads.Ad.NetworkListener#hideNetworkActivity()
     */
    public void onNetworkActivityEnd()
    {
        // replace the spinner with the image view.
        post(new Thread(){
            public void run()
            {
            	if (activityIndicator != null)
            	{
            		activityIndicator.setVisibility(INVISIBLE);
            	}
            	
            	if (iconView != null)
            	{
	                iconView.setImageMatrix(new Matrix());
	                iconView.setVisibility(VISIBLE);
            	}
            	
                clickInProgress = false;
            }
        });
    }

    /* (non-Javadoc)
     * @see com.admob.android.ads.Ad.NetworkListener#showNetworkActivity()
     */
    public void onNetworkActivityStart()
    {
        post(new Thread(){
            public void run()
            {
                // replace the icon with the activity spinner.
            	if (iconView != null)
            	{
            		iconView.setVisibility(INVISIBLE);
            	}
            	
            	if (activityIndicator != null)
            	{
            		activityIndicator.setVisibility(VISIBLE);
            	}
            }
        });
    }

    /**
     * Draws a focus ring around the ad when it has been scrolled over. This is
     * the last step in rendering the ad.
     * 
     * @param c
     *            is the canvas the ring is drawn onto.
     * @param r
     *            is the rectangle that specifies the path of the ring.
     * @param color
     *            is the focus ring color around the ad.
     */
    private static void drawFocusRing(Canvas c, Rect r)
    {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(FOCUS_COLOR);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(FOCUS_WIDTH);
        paint.setPathEffect(new CornerPathEffect(FOCUS_CORNER_ROUNDING));

        Path path = new Path();
        path.addRoundRect(new RectF(r), FOCUS_CORNER_ROUNDING,
                FOCUS_CORNER_ROUNDING, Path.Direction.CW);

        c.drawPath(path, paint);
    }

    /**
     * Called when the user presses any key while the focus is on this ad.
     * 
     * @see android.view.View#onKeyDown(int, android.view.KeyEvent)
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (Log.isLoggable(AdManager.LOG, Log.VERBOSE))
        {
            Log.v(AdManager.LOG, "onKeyDown:" + " keyCode=" + keyCode);
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
        {
            setPressed(true);
        }

        // Continue processing the event.
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Called when the user releases any key while the focus is on this ad.
     * 
     * @see android.view.View#onKeyUp(int, android.view.KeyEvent)
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        if (Log.isLoggable(AdManager.LOG, Log.VERBOSE))
        {
            Log.v(AdManager.LOG, "onKeyUp:" + " keyCode=" + keyCode);
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
        {
            click();
        }

        setPressed(false);

        // Continue processing the event.
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Called when the user presses the touch screen for this ad.
     * 
     * @see android.view.View#dispatchTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent e)
    {
        // Note onTouchEvent() does not work like the expected so we need to
        // hook into the dispatch.

        int action = e.getAction();

        if (Log.isLoggable(AdManager.LOG, Log.VERBOSE))
        {
            Log.v(AdManager.LOG, "dispatchTouchEvent:" + " action=" + action
                    + " x=" + e.getX() + " y=" + e.getY());
        }

        if (action == MotionEvent.ACTION_DOWN)
        {
            setPressed(true);
        } else if (action == MotionEvent.ACTION_MOVE)
        {
            // Determine if this event is within the view or has left the view.
            float x = e.getX();
            float y = e.getY();

            int left = getLeft();
            int top = getTop();
            int right = getRight();
            int bottom = getBottom();

            if (x < left || x > right || y < top || y > bottom)
            {
                // No longer holding the view down.
                setPressed(false);
            } else
            {
                setPressed(true);
            }
        } else if (action == MotionEvent.ACTION_UP)
        {
            if (isPressed())
            {
                click();
            }

            setPressed(false);
        }
        else if (action == MotionEvent.ACTION_CANCEL)
        {
        	// this action happens if the touch turns into a scroll.
        	setPressed(false);
        }

        // Continue processing the event.
        return super.dispatchTouchEvent(e);
    }

    /**
     * Called when the user presses the trackball while the focus is on this ad.
     * 
     * @see android.view.View#dispatchTrackballEvent(android.view.MotionEvent)
     */
    @Override
    public boolean dispatchTrackballEvent(MotionEvent event)
    {
        if (Log.isLoggable(AdManager.LOG, Log.VERBOSE))
        {
            Log.v(AdManager.LOG, "dispatchTrackballEvent:" + " action="
                    + event.getAction());
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN)
        {
            setPressed(true);
        } else if (event.getAction() == MotionEvent.ACTION_UP)
        {
            if (hasFocus())
            {
                click();
            }

            setPressed(false);
        }

        // Continue processing the event.
        return super.onTrackballEvent(event);
    }

    /* (non-Javadoc)
     * @see android.view.View#onFocusChanged(boolean, int, android.graphics.Rect)
     */
    @Override
    protected void onFocusChanged(boolean gainFocus, int direction,
            Rect previouslyFocusedRect)
    {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        // change the background.
        if(gainFocus)
        {
            setBackgroundDrawable(focusedBackground);
        }
        else
        {
            setBackgroundDrawable(defaultBackground);
        }
    }

    /**
     * Call whenever this ad is getting pressed, but before it has been clicked.
     * For example when a key goes down, but before it goes up.
     * 
     * @param pressed
     *            when <code>true</code> renders this ad that a click will take
     *            place and when <code>false</code> renders the ad normally.
     */
    @Override
    public void setPressed(boolean pressed)
    {
        // check with design and they prefer to
        // have the ad eat clicks while click is in progress.
        if (!(pressed && clickInProgress) 
                && isPressed() != pressed)
        {
            // we need to update the background.
            Drawable newBackground = defaultBackground;
            int color = textColor;
            if(pressed)
            {
                lastBackground = getBackground();
                newBackground = pressedBackground;
                color = HIGHLIGHT_TEXT_COLOR;
            }
            else
            {
                newBackground = lastBackground;
            }

            setBackgroundDrawable(newBackground);
            if(adTextView != null)
            {
                adTextView.setTextColor(color);
            }
            if(adMobBrandingTextView != null)
            {
                adMobBrandingTextView.setTextColor(color);
            }

            super.setPressed(pressed);
            invalidate();
        }
    }

    /**
     * Call when the user clicks on the ad. This will bring up the ad's landing
     * page in the browser and minimize this application.
     */
    private void click()
    {
        if (ad != null)
        {
            if (isPressed())
            {
                // this will need to change later.
                setPressed(false);

                // ignore clicks when one is in progress.
                if(!clickInProgress)
                {
                    clickInProgress = true;
                    if(iconView != null)
                    {
                        // need to package a few animations.
                        AnimationSet animSet = new AnimationSet(true);
                        // first animation: grow view 1.0 -> 1.4
                        long firstDuration = (long) (PULSE_ANIMATION_DURATION * PULSE_GROW_KEY_TIME * NUM_MILLIS_IN_SECS);
    
                        float pivotX = iconView.getWidth() / 2.0f;
                        float pivotY = iconView.getHeight() / 2.0f;
                        ScaleAnimation growAnim = new ScaleAnimation(PULSE_INITIAL_SCALE, PULSE_GROWN_SCALE,
                                PULSE_INITIAL_SCALE, PULSE_GROWN_SCALE, 
                                pivotX, pivotY);
                        growAnim.setDuration(firstDuration);
                        animSet.addAnimation(growAnim);
                        // second animation: shrink view 1.4 -> 0.001
                        ScaleAnimation shrinkAnim = new ScaleAnimation(PULSE_GROWN_SCALE, PULSE_SHRUNKEN_SCALE,
                                PULSE_GROWN_SCALE, PULSE_SHRUNKEN_SCALE, 
                                pivotX, pivotY);
                        shrinkAnim.setDuration((long) (PULSE_ANIMATION_DURATION * (1.0 - PULSE_GROW_KEY_TIME)*NUM_MILLIS_IN_SECS));
                        shrinkAnim.setStartOffset(firstDuration);
                        shrinkAnim.setAnimationListener(this);
                        animSet.addAnimation(shrinkAnim);
    
                        // figure out a duration and call the clicked after that duration.  This is because
                        // the animation won't run if this ad is moved off screen (for instance in a ListView).
                        // for this reason we don't let the click action depend on the animation finishing.
                        postDelayed(new Thread(){
                          public void run()
                          {
                            ad.clicked();
                          }
                        }, (long) (PULSE_ANIMATION_DURATION * NUM_MILLIS_IN_SECS));
                        iconView.startAnimation(animSet);
                    }
                    else
                    {
                        // what do we do if we don't have an icon?  just click?
                        ad.clicked();
                    }
                }
            }
        }
    }

    /**
     * The click animation never repeats so this is never called.
     *
     * @see android.view.animation.Animation$AnimationListener#onAnimationRepeat(android.view.animation.Animation)
     */
    public void onAnimationRepeat(Animation animation)
    {
        // do nothing here.
    }

    /**
     * The click animation started.
     *
     * @see android.view.animation.Animation$AnimationListener#onAnimationStart(android.view.animation.Animation)
     */
    public void onAnimationStart(Animation animation)
    {
        // do nothing here.
    }

    /**
     * The click animation ended.
     * 
     * @see android.view.animation.Animation.AnimationListener#onAnimationEnd(android.view.animation.Animation)
     */
    public void onAnimationEnd(Animation animation)
    {
    }
}
