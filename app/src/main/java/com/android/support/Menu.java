//Please don't replace listeners with lambda!

package com.android.support;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;

public class Menu {

    public static final String TAG = "Mod_Menu";

    // ================================================================
    //  PREMIUM DESIGN TOKENS
    // ================================================================
    // Brand accents
    int COLOR_ACCENT      = Color.parseColor("#00E5FF");   // electric cyan
    int COLOR_ACCENT_2    = Color.parseColor("#7C4DFF");   // royal violet
    int COLOR_ACCENT_3    = Color.parseColor("#3D7BFF");   // cobalt (bridge colour)
    int COLOR_SUCCESS     = Color.parseColor("#00F5A0");   // mint
    int COLOR_DANGER      = Color.parseColor("#FF4D6D");   // coral red
    // Surfaces
    int COLOR_BG_TOP      = Color.parseColor("#111A36");
    int COLOR_BG_BOTTOM   = Color.parseColor("#080C19");
    int COLOR_CARD        = Color.parseColor("#131B35");
    int COLOR_CARD_HI     = Color.parseColor("#1A2547");
    int COLOR_CARD_BORDER = Color.parseColor("#26335F");
    int COLOR_TRACK       = Color.parseColor("#232E52");
    int COLOR_TEXT_MUTED  = Color.parseColor("#8194BE");
    // Gradient buttons
    int BTN_GRAD_1        = Color.parseColor("#0093C4");
    int BTN_GRAD_2        = Color.parseColor("#6A47F5");

    // Legacy names (kept so old references keep working)
    int TEXT_COLOR            = Color.parseColor("#00E5FF");
    int TEXT_COLOR_2          = Color.parseColor("#F2F6FF");
    int BTN_COLOR             = Color.parseColor("#00E5FF");
    int MENU_BG_COLOR         = Color.parseColor("#0B1122");
    int MENU_FEATURE_BG_COLOR = Color.parseColor("#131B35");
    int BORDER_COLOR          = Color.parseColor("#00E5FF");

    // Default (design) menu size - includes the outer glow padding
    int MENU_WIDTH  = 272;
    int MENU_HEIGHT = 352;

    int POS_X = 5;
    int POS_Y = 100;
    float MENU_CORNER  = 18f;      // dp
    int   ICON_SIZE    = 50;
    float ICON_ALPHA   = 1f;
    int ToggleON  = Color.parseColor("#00F5A0");
    int ToggleOFF = Color.parseColor("#FF4D6D");
    int BtnON     = Color.parseColor("#00F5A0");
    int BtnOFF    = Color.parseColor("#FF4D6D");
    int CategoryBG    = Color.parseColor("#0F1830");
    int SeekBarColor  = Color.parseColor("#00E5FF");
    int SeekBarProgressColor = Color.parseColor("#00F5A0");
    int CheckBoxColor = Color.parseColor("#00E5FF");
    int RadioColor    = Color.parseColor("#00E5FF");
    int CollapseColor = Color.parseColor("#111B3A");
    String NumberTxtColor = "#00F5A0";

    // ---- Glow / frame ----
    private static final int   GLOW_DP   = 8;
    private static final int   BORDER_DP = 1;
    private static final float ICON_CORNER_FRACTION = 0.26f;

    // ---- Responsive / resize settings ----
    private static final boolean SHOW_TAB_ICONS = true;      // false = text-only tabs
    private static final int   MIN_MENU_WIDTH_DP  = 172;
    private static final int   MIN_MENU_HEIGHT_DP = 196;
    private static final int   COMPACT_TABS_BELOW_DP = 205;  // below this width tabs become icon-only
    private static final float MIN_SCALE_FACTOR = 0.70f;
    private static final float MAX_SCALE_FACTOR = 1.50f;

    private View resizeHandle;
    private float currentScale = 1.0f;
    private boolean compactMode = false;
    private int lastSidebarW = -1;

    RelativeLayout mCollapsed, mRootContainer;
    LinearLayout   mExpanded, mCollapse;
    WindowManager             mWindowManager;
    WindowManager.LayoutParams vmParams;
    ImageView  startimage;
    FrameLayout rootFrame;
    FrameLayout menuFrame;

    private LinearLayout sidebarLayout;
    private LinearLayout contentLayout;
    private ScrollView sidebarScroll;
    private ScrollView contentScrollView;
    private HashMap<String, LinearLayout> categoryViewsMap;
    private ArrayList<String> categoryNames;
    private LinearLayout mainContainer;

    // Typography (loaded from assets/fonts if present, otherwise clean system sans)
    private Typeface fontRegular;
    private Typeface fontMedium;
    private Typeface fontBold;

    // Glow animation
    private GlowFrameDrawable glowDrawable;
    private ValueAnimator glowAnimator;
    private final ArgbEvaluator argb = new ArgbEvaluator();

    // base (unscaled) text size + padding for every scaled TextView
    private final WeakHashMap<TextView, float[]> textBase = new WeakHashMap<TextView, float[]>();
    private final ArrayList<TabHolder> tabHolders = new ArrayList<TabHolder>();
    private final ArrayList<ArrayAdapter<String>> spinnerAdapters = new ArrayList<ArrayAdapter<String>>();

    boolean stopChecking, overlayRequired;
    Context getContext;

    private int savedWindowFlags = 0;
private boolean windowIsFocusable = false;
private boolean isLoggedIn = false;
private View sidebarDivider = null;
private int effectivePosY = POS_Y;
    ESPView espview;
    WindowManager espWindowManager;
    WindowManager.LayoutParams espParams;

    native void Init(Context context, TextView title, TextView subTitle);
    native String Icon();
    native String IconWebViewData();
    native String[] GetFeatureList();
    native String[] SettingsList();
    native boolean IsGameLibLoaded();

    public static native void Draw(ESPView espView, Canvas canvas);

    public Menu(Context context) {
        getContext = context;
        Preferences.context = context;

        // Drop Poppins / Inter files into assets/fonts to upgrade the typography.
        fontRegular = loadFont(new String[]{"fonts/poppins_regular.ttf", "fonts/inter_regular.ttf"}, "sans-serif", Typeface.NORMAL);
        fontMedium  = loadFont(new String[]{"fonts/poppins_medium.ttf", "fonts/inter_medium.ttf"}, "sans-serif-medium", Typeface.NORMAL);
        fontBold    = loadFont(new String[]{"fonts/poppins_semibold.ttf", "fonts/poppins_bold.ttf", "fonts/inter_bold.ttf"}, "sans-serif", Typeface.BOLD);

        rootFrame = new FrameLayout(context);
        mRootContainer = new RelativeLayout(context);
        mCollapsed = new RelativeLayout(context);
        mCollapsed.setVisibility(View.VISIBLE);
        mCollapsed.setAlpha(ICON_ALPHA);

        // Initial size never exceeds the screen
        // ================================================================
// 🔥 Orientation-aware initial sizing (fixes landscape height issue)
// ================================================================
// ================================================================
// 🔥 Orientation-aware initial sizing
// ================================================================
int orientation = context.getResources().getConfiguration().orientation;
// ❌ int effectivePosY = POS_Y;   ← এই লাইনটি DELETE করুন

// ✅ Field এ assign করুন
effectivePosY = POS_Y;
if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
    effectivePosY = 10;
}

int initW = Math.min(dp(MENU_WIDTH), Math.max(dp(MIN_MENU_WIDTH_DP), screenW() - dp(10)));
int initH = Math.min(dp(MENU_HEIGHT), Math.max(dp(MIN_MENU_HEIGHT_DP), screenH() - effectivePosY - dp(8)));

int loginMinH = dp(340);
if (initH < loginMinH && screenH() > loginMinH + effectivePosY) {
    initH = loginMinH;
}
        // ---------------- Frame: animated glow + gradient border ----------------
        menuFrame = new FrameLayout(context);
        menuFrame.setVisibility(View.GONE);
        menuFrame.setLayoutParams(new RelativeLayout.LayoutParams(initW, initH));
        menuFrame.setClipChildren(false);
        menuFrame.setClipToPadding(false);
        final int inset = dp(GLOW_DP) + dp(BORDER_DP);
        menuFrame.setPadding(inset, inset, inset, inset);
        glowDrawable = new GlowFrameDrawable(dpf(MENU_CORNER), dp(GLOW_DP), dp(BORDER_DP), COLOR_ACCENT,
                                             new int[]{COLOR_ACCENT, COLOR_ACCENT_3, COLOR_ACCENT_2, COLOR_ACCENT});
        menuFrame.setBackground(glowDrawable);

        // Re-apply responsive scale whenever the width changes
        menuFrame.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    int w = right - left;
                    if (w > 0 && w != (oldRight - oldLeft)) {
                        menuFrame.post(new Runnable() {
                                @Override
                                public void run() {
                                    applyResponsiveScale(menuFrame.getWidth());
                                }
                            });
                    }
                }
            });

        // ---------------- Menu surface ----------------
        mExpanded = new LinearLayout(context);
        mExpanded.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable menuBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                                                       new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        final float innerRadius = dpf(MENU_CORNER) - dp(BORDER_DP);
        menuBg.setCornerRadius(innerRadius);
        mExpanded.setBackground(menuBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mExpanded.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), innerRadius);
                    }
                });
            mExpanded.setClipToOutline(true);
        }
        menuFrame.addView(mExpanded, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        // ---------------- Collapsed icon (with animated neon ring) ----------------
        startimage = new ImageView(context);
        int iconPx = (int) TypedValue.applyDimension(1, ICON_SIZE, context.getResources().getDisplayMetrics());
        startimage.setLayoutParams(new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
        startimage.setScaleType(ImageView.ScaleType.FIT_XY);
        byte[] decode = Base64.decode(Icon(), 0);
        startimage.setImageBitmap(BitmapFactory.decodeByteArray(decode, 0, decode.length));
        startimage.setOnTouchListener(onTouchListener());

        WebView wView = new WebView(context);
        wView.setLayoutParams(new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
        wView.loadData("<html><head></head><body style=\"margin:0;padding:0\">" + "<img src=\"" + IconWebViewData() + "\" width=\"" + ICON_SIZE + "\" height=\"" + ICON_SIZE + "\"></body></html>", "text/html", "utf-8");
        wView.setBackgroundColor(0x00000000);
        wView.setAlpha(ICON_ALPHA);
        wView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        wView.setOnTouchListener(onTouchListener());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final float iconRadius = iconPx * ICON_CORNER_FRACTION;
            ViewOutlineProvider iconOutline = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), iconRadius);
                }
            };
            startimage.setOutlineProvider(iconOutline);
            startimage.setClipToOutline(true);
            wView.setOutlineProvider(iconOutline);
            wView.setClipToOutline(true);
        }

        FrameLayout iconHolder = new FrameLayout(context);
        int holderPx = iconPx + dp(22);
        iconHolder.setLayoutParams(new RelativeLayout.LayoutParams(holderPx, holderPx));
        iconHolder.addView(new GlowRingView(context, COLOR_ACCENT, COLOR_ACCENT_2),
                           new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        iconHolder.setOnTouchListener(onTouchListener());
        mCollapsed.addView(iconHolder);

        // ---------------- Header ----------------
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(9), dp(8), dp(7));
        header.setOnTouchListener(onTouchListener());

        LinearLayout brand = new LinearLayout(context);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        final TitanicTextView title = new TitanicTextView(context);
        title.setTextSize(19);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setTextColor(COLOR_ACCENT);
        title.setTypeface(fontBold);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) title.setLetterSpacing(0.03f);
        title.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView subTitle = new TextView(context);
        subTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        subTitle.setMarqueeRepeatLimit(-1);
        subTitle.setSingleLine(true);
        subTitle.setSelected(true);
        subTitle.setTextColor(COLOR_TEXT_MUTED);
        subTitle.setTypeface(fontRegular);
        subTitle.setTextSize(9.5f);
        subTitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        subTitle.setPadding(0, dp(1), 0, 0);
        subTitle.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        brand.addView(title);
        brand.addView(subTitle);

        final TitanicTextView proTitle = new TitanicTextView(context);
        proTitle.setText("PRO");
        proTitle.setTextSize(10);
        proTitle.setGravity(Gravity.CENTER);
        proTitle.setTextColor(COLOR_SUCCESS);
        proTitle.setTypeface(fontBold);
        proTitle.setPadding(dp(8), dp(2), dp(8), dp(2));
        proTitle.setBackground(cardBg(withAlpha(COLOR_SUCCESS, 0x1A), withAlpha(COLOR_SUCCESS, 0x99), 8));
        LinearLayout.LayoutParams proLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        proLp.setMargins(dp(6), 0, dp(6), 0);
        proTitle.setLayoutParams(proLp);

        final ImageView settings = new ImageView(context);
        TabIcon gearIcon = new TabIcon(TabIcon.GEAR);
        gearIcon.setColor(COLOR_ACCENT);
        settings.setImageDrawable(gearIcon);
        settings.setScaleType(ImageView.ScaleType.FIT_CENTER);
        settings.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable gearBg = new GradientDrawable();
        gearBg.setShape(GradientDrawable.OVAL);
        gearBg.setColor(withAlpha(COLOR_ACCENT, 0x14));
        gearBg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0x55));
        settings.setBackground(gearBg);
        settings.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
        settings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.animate().rotationBy(120f).setDuration(320).setInterpolator(new OvershootInterpolator(1.4f)).start();
                    selectTabByName("Settings");
                }
            });

        header.addView(brand);
        header.addView(proTitle);
        header.addView(settings);

        LinearLayout.LayoutParams shimmerLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(2));
        shimmerLp.setMargins(dp(12), 0, dp(12), 0);
        ShimmerLine shimmer = new ShimmerLine(context, COLOR_ACCENT, COLOR_ACCENT_2);
        shimmer.setLayoutParams(shimmerLp);

        // ---------------- Body: sidebar + content (both natively drag-scrollable) ----------------
        mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.HORIZONTAL);
        mainContainer.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f));

        sidebarScroll = new SlimScrollView(context);
        sidebarScroll.setLayoutParams(new LinearLayout.LayoutParams(dp(96), MATCH_PARENT));
        sidebarScroll.setBackgroundColor(0x33000000);

        sidebarLayout = new LinearLayout(context);
        sidebarLayout.setOrientation(LinearLayout.VERTICAL);
        sidebarLayout.setPadding(dp(6), dp(6), dp(8), dp(6));
        sidebarScroll.addView(sidebarLayout);

        contentScrollView = new SlimScrollView(context);
        contentScrollView.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
        contentScrollView.setFillViewport(true);

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(0), dp(4), dp(6), dp(6));
        contentScrollView.addView(contentLayout);

        mainContainer.addView(sidebarScroll);
        sidebarDivider = makeDivider(true);
        mainContainer.addView(sidebarDivider);
        mainContainer.addView(contentScrollView);

        // ---------------- Bottom bar (HIDE/KILL + MINIMIZE) ----------------
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        bottomBar.setPadding(dp(8), dp(6), dp(26), dp(7));   // right space for resize handle

        Button hideBtn = new Button(context);
        LinearLayout.LayoutParams hideLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        hideLp.setMargins(0, 0, dp(6), 0);
        hideBtn.setLayoutParams(hideLp);
        hideBtn.setBackground(cardBg(withAlpha(COLOR_DANGER, 0x1C), withAlpha(COLOR_DANGER, 0x77), 10));
        hideBtn.setText("HIDE/KILL (Hold)");
        hideBtn.setAllCaps(false);
        hideBtn.setTextColor(Color.parseColor("#FF7B93"));
        hideBtn.setTextSize(11f);
        hideBtn.setTypeface(fontMedium);
        hideBtn.setSingleLine(true);
        hideBtn.setEllipsize(TextUtils.TruncateAt.END);
        hideBtn.setGravity(Gravity.CENTER);
        hideBtn.setMinHeight(0);
        hideBtn.setMinimumHeight(0);
        hideBtn.setMinWidth(0);
        hideBtn.setMinimumWidth(0);
        hideBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        flatten(hideBtn);
        addPressAnim(hideBtn);
        hideBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    collapseMenu(0f);
                    Toast.makeText(view.getContext(), "Icon hidden", Toast.LENGTH_SHORT).show();
                }
            });
        hideBtn.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View view) {
                    Toast.makeText(view.getContext(), "Menu killed", Toast.LENGTH_LONG).show();
                    stopGlowAnimator();
                    rootFrame.removeView(mRootContainer);
                    mWindowManager.removeView(rootFrame);
                    return false;
                }
            });

        Button closeBtn = new Button(context);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        closeBtn.setBackground(cardBg(withAlpha(COLOR_ACCENT, 0x1A), withAlpha(COLOR_ACCENT, 0x77), 10));
        closeBtn.setText("MINIMIZE");
        closeBtn.setAllCaps(false);
        closeBtn.setTextColor(COLOR_ACCENT);
        closeBtn.setTextSize(11f);
        closeBtn.setTypeface(fontMedium);
        closeBtn.setSingleLine(true);
        closeBtn.setMinHeight(0);
        closeBtn.setMinimumHeight(0);
        closeBtn.setMinWidth(0);
        closeBtn.setMinimumWidth(0);
        closeBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        flatten(closeBtn);
        addPressAnim(closeBtn);
        closeBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    collapseMenu(ICON_ALPHA);
                }
            });

        bottomBar.addView(hideBtn);
        bottomBar.addView(closeBtn);

        mExpanded.addView(header);
        mExpanded.addView(shimmer);
        mExpanded.addView(mainContainer);
        mExpanded.addView(makeDivider(false));
        mExpanded.addView(bottomBar);

        mRootContainer.addView(mCollapsed);
        mRootContainer.addView(menuFrame);

        if (IconWebViewData() != null) {
            iconHolder.addView(wView);
        } else {
            iconHolder.addView(startimage);
        }

        categoryViewsMap = new HashMap<String, LinearLayout>();
        categoryNames = new ArrayList<String>();

        // ---------------- Resize handle (diagonal grip) ----------------
        resizeHandle = new View(context);
        resizeHandle.setBackground(makeGripDrawable(withAlpha(COLOR_ACCENT, 0xE0), dpf(1.7f)));
        int hSize = dp(22);
        FrameLayout.LayoutParams rhLp = new FrameLayout.LayoutParams(hSize, hSize);
        rhLp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        rhLp.setMargins(0, 0, inset + dp(3), inset + dp(3));
        resizeHandle.setLayoutParams(rhLp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            resizeHandle.setElevation(dp(30));
        }
        resizeHandle.setOnTouchListener(resizeTouchListener);
        menuFrame.addView(resizeHandle);

        // ==================== ESP OVERLAY SETUP ====================
        try {
            espview = new ESPView(context);
            espWindowManager = (WindowManager) context.getSystemService(context.WINDOW_SERVICE);

            int espType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

            espParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                espType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                PixelFormat.TRANSPARENT);
            espParams.gravity = Gravity.TOP | Gravity.START;
            espParams.x = 0;
            espParams.y = 0;

            espWindowManager.addView(espview, espParams);
        } catch (Exception e) {
            Log.e(TAG, "ESPView add failed: " + e);
        }
        // ==================================================================

        Init(context, title, subTitle);
        new Titanic().start(title);
        new Titanic().start(proTitle);
    }

    // ================================================================
    // Small helpers
    // ================================================================
    private Typeface loadFont(String[] assetNames, String fallbackFamily, int fallbackStyle) {
        for (int i = 0; i < assetNames.length; i++) {
            try {
                return Typeface.createFromAsset(getContext.getAssets(), assetNames[i]);
            } catch (Exception ignored) {}
        }
        try {
            return Typeface.create(fallbackFamily, fallbackStyle);
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static boolean isLight(int color) {
        double lum = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color);
        return lum > 165;
    }

    private static String hex(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    private LinearLayout.LayoutParams rowLp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private GradientDrawable cardBg(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private GradientDrawable gradBg(int c1, int c2, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void flatten(Button b) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            b.setStateListAnimator(null);
        }
    }

    // Soft "press in / bounce out" feedback. Returns false so click listeners still fire.
    private void addPressAnim(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            view.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.88f).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2.2f)).start();
                            break;
                    }
                    return false;
                }
            });
    }

    private void animateStroke(final GradientDrawable g, int from, int to) {
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        va.setDuration(240);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    g.setStroke(dp(1), (Integer) a.getAnimatedValue());
                }
            });
        va.start();
    }

    private View makeDivider(boolean vertical) {
        View v = new View(getContext);
        if (vertical) {
            v.setLayoutParams(new LinearLayout.LayoutParams(dp(1), MATCH_PARENT));
            v.setBackgroundColor(withAlpha(COLOR_ACCENT, 0x24));
        } else {
            v.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, dp(1)));
            GradientDrawable line = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                                         new int[]{withAlpha(COLOR_ACCENT, 0x00), withAlpha(COLOR_ACCENT, 0x70), withAlpha(COLOR_ACCENT_2, 0x70), withAlpha(COLOR_ACCENT_2, 0x00)});
            v.setBackground(line);
        }
        return v;
    }

    private DisplayMetrics displayMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        try {
            WindowManager wm = (WindowManager) getContext.getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getMetrics(dm);
            if (dm.widthPixels > 0 && dm.heightPixels > 0) return dm;
        } catch (Exception ignored) {}
        return getContext.getResources().getDisplayMetrics();
    }

    private int screenW() { return displayMetrics().widthPixels; }
    private int screenH() { return displayMetrics().heightPixels; }

    // ================================================================
    // Expand / collapse with animation
    // ================================================================
    private void expandMenu() {
    menuFrame.animate().cancel();
    mCollapsed.setVisibility(View.GONE);
    menuFrame.setVisibility(View.VISIBLE);
    menuFrame.setPivotX(0f);
    menuFrame.setPivotY(0f);
    menuFrame.setAlpha(0f);
    menuFrame.setScaleX(0.86f);
    menuFrame.setScaleY(0.86f);
    menuFrame.animate()
        .alpha(1f).scaleX(1f).scaleY(1f)
        .setDuration(280)
        .setInterpolator(new DecelerateInterpolator(1.8f))
        .start();
    startGlowAnimator();
    menuFrame.post(new Runnable() {
        @Override
        public void run() {
            keepInsideScreen();
            applyResponsiveScale(menuFrame.getWidth());

            if (!isLoggedIn) {
                setWindowFocusable(true);
            }
        }
    });
}

    private void collapseMenu(float iconAlpha) {
        setWindowFocusable(false);
        stopGlowAnimator();
        mCollapsed.setVisibility(View.VISIBLE);
        mCollapsed.setAlpha(iconAlpha);
        mCollapsed.setScaleX(0.6f);
        mCollapsed.setScaleY(0.6f);
        mCollapsed.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(2.4f)).start();

        menuFrame.animate().cancel();
        menuFrame.animate()
            .alpha(0f).scaleX(0.9f).scaleY(0.9f)
            .setDuration(170)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    menuFrame.setVisibility(View.GONE);
                    menuFrame.setAlpha(1f);
                    menuFrame.setScaleX(1f);
                    menuFrame.setScaleY(1f);
                }
            })
            .start();
    }

    // Border light rotates slowly and the glow "breathes" between cyan and violet
    private void startGlowAnimator() {
        if (glowDrawable == null) return;
        if (glowAnimator != null && glowAnimator.isRunning()) return;
        glowAnimator = ValueAnimator.ofFloat(0f, 1f);
        glowAnimator.setDuration(9000);
        glowAnimator.setInterpolator(new LinearInterpolator());
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    float p = (Float) a.getAnimatedValue();
                    double s = Math.sin(p * Math.PI * 2.0 * 4.0);
                    float intensity = (float) (0.78 + 0.22 * s);
                    int color = (Integer) argb.evaluate((float) ((s + 1.0) / 2.0), COLOR_ACCENT, COLOR_ACCENT_2);
                    glowDrawable.update(p, intensity, color);
                }
            });
        glowAnimator.start();
    }

    private void stopGlowAnimator() {
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
    }

    // Move the overlay window but keep it inside the screen
    private void moveWindow(int x, int y) {
        if (vmParams == null || mWindowManager == null || rootFrame == null) return;
        int vw = rootFrame.getWidth();
        int vh = rootFrame.getHeight();
        int maxX = Math.max(0, screenW() - vw);
        int maxY = Math.max(0, screenH() - vh);
        vmParams.x = Math.max(0, Math.min(x, maxX));
        vmParams.y = Math.max(0, Math.min(y, maxY));
        try { mWindowManager.updateViewLayout(rootFrame, vmParams); } catch (Exception ignored) {}
    }

    // Shrink / reposition the menu if it is bigger than the current screen (e.g. after rotation)
    private void keepInsideScreen() {
        if (vmParams == null || mWindowManager == null || rootFrame == null) return;
        try {
            int sw = screenW();
            int sh = screenH();
            ViewGroup.LayoutParams lp = menuFrame.getLayoutParams();
            int minW = dp(MIN_MENU_WIDTH_DP);
            int minH = dp(MIN_MENU_HEIGHT_DP);
            int maxW = Math.max(minW, sw - dp(4));
            int maxH = Math.max(minH, sh - dp(4));
            boolean changed = false;
            if (lp.width > maxW) { lp.width = maxW; changed = true; }
            if (lp.height > maxH) { lp.height = maxH; changed = true; }
            if (changed) menuFrame.setLayoutParams(lp);

            int vw = menuFrame.getVisibility() == View.VISIBLE ? lp.width : rootFrame.getWidth();
            int vh = menuFrame.getVisibility() == View.VISIBLE ? lp.height : rootFrame.getHeight();
            int nx = Math.max(0, Math.min(vmParams.x, Math.max(0, sw - vw)));
            int ny = Math.max(0, Math.min(vmParams.y, Math.max(0, sh - vh)));
            if (changed || nx != vmParams.x || ny != vmParams.y) {
                vmParams.x = nx;
                vmParams.y = ny;
                mWindowManager.updateViewLayout(rootFrame, vmParams);
            }
        } catch (Exception ignored) {}
    }

    // ================================================================
    // Resize grip (three diagonal lines)
    // ================================================================
    private Drawable makeGripDrawable(final int color, final float strokePx) {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                p.setColor(color);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokePx);
                p.setStrokeCap(Paint.Cap.ROUND);
            }

            @Override
            public void draw(Canvas canvas) {
                int W = getBounds().width();
                int H = getBounds().height();
                if (W <= 0 || H <= 0) return;
                float m = strokePx + 1f;
                float[] d = {0.34f, 0.62f, 0.90f};
                for (int i = 0; i < d.length; i++) {
                    float len = Math.min(W, H) * d[i];
                    canvas.drawLine(W - m - len, H - m, W - m, H - m - len, p);
                }
            }

            @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
            @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    // ================================================================
    // Animated glow + gradient border frame (drawn behind the menu surface)
    // ================================================================
    private static class GlowFrameDrawable extends Drawable {
        private static final int STEPS = 10;

        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF base = new RectF();
        private final RectF tmp = new RectF();
        private final Matrix matrix = new Matrix();
        private final float radius, glowSize, borderWidth;
        private final int[] sweepColors;
        private SweepGradient sweep;
        private float phase = 0f;
        private float intensity = 1f;
        private int glowColor;

        GlowFrameDrawable(float radius, float glowSize, float borderWidth, int glowColor, int[] sweepColors) {
            this.radius = radius;
            this.glowSize = glowSize;
            this.borderWidth = borderWidth;
            this.glowColor = glowColor;
            this.sweepColors = sweepColors;
            glowPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(borderWidth);
        }

        void update(float phase, float intensity, int glowColor) {
            this.phase = phase;
            this.intensity = intensity;
            this.glowColor = glowColor;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect b) {
            super.onBoundsChange(b);
            if (b.width() > 0 && b.height() > 0) {
                sweep = new SweepGradient(b.exactCenterX(), b.exactCenterY(), sweepColors, null);
                borderPaint.setShader(sweep);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0 || sweep == null) return;

            base.set(b.left + glowSize, b.top + glowSize, b.right - glowSize, b.bottom - glowSize);
            float step = glowSize / STEPS;
            glowPaint.setStrokeWidth(step + 1f);

            for (int i = STEPS; i >= 1; i--) {
                float d = (i - 0.5f) / STEPS;
                float a = 0.36f * (1f - d) * (1f - d) * intensity;
                glowPaint.setColor(glowColor);
                glowPaint.setAlpha(Math.max(0, Math.min(255, (int) (255f * a))));
                float inflate = (i - 0.5f) * step;
                tmp.set(base.left - inflate, base.top - inflate, base.right + inflate, base.bottom + inflate);
                canvas.drawRoundRect(tmp, radius + inflate, radius + inflate, glowPaint);
            }

            matrix.setRotate(phase * 360f, b.exactCenterX(), b.exactCenterY());
            sweep.setLocalMatrix(matrix);
            float half = borderWidth / 2f;
            tmp.set(base.left + half, base.top + half, base.right - half, base.bottom - half);
            canvas.drawRoundRect(tmp, radius - half, radius - half, borderPaint);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================
    // Looping animated views (auto start / stop with visibility)
    // ================================================================
    private static abstract class LoopView extends View {
        protected float progress;
        private final long duration;
        private ValueAnimator animator;
        private boolean attached;

        LoopView(Context context, long duration) {
            super(context);
            this.duration = duration;
        }

        private void sync() {
            boolean run = attached && isShown() && getWindowVisibility() == View.VISIBLE;
            if (run) {
                if (animator == null) {
                    animator = ValueAnimator.ofFloat(0f, 1f);
                    animator.setDuration(duration);
                    animator.setInterpolator(new LinearInterpolator());
                    animator.setRepeatCount(ValueAnimator.INFINITE);
                    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator a) {
                                progress = (Float) a.getAnimatedValue();
                                invalidate();
                            }
                        });
                }
                if (!animator.isStarted()) animator.start();
            } else if (animator != null && animator.isStarted()) {
                animator.cancel();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            sync();
        }

        @Override
        protected void onDetachedFromWindow() {
            attached = false;
            sync();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onVisibilityChanged(View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            sync();
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            sync();
        }
    }

    // Rotating neon ring + pulsing glow around the collapsed icon
    private static class GlowRingView extends LoopView {
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ringRect = new RectF();
        private final RectF tmp = new RectF();
        private final Matrix matrix = new Matrix();
        private final float density;
        private final int c1, c2;
        private SweepGradient sweep;

        GlowRingView(Context context, int c1, int c2) {
            super(context, 3200);
            this.density = context.getResources().getDisplayMetrics().density;
            this.c1 = c1;
            this.c2 = c2;
            glowPaint.setStyle(Paint.Style.STROKE);
            basePaint.setStyle(Paint.Style.STROKE);
            basePaint.setStrokeWidth(1.2f * density);
            basePaint.setColor(withAlpha(c1, 0x50));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(2.2f * density);
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w > 0 && h > 0) {
                sweep = new SweepGradient(w / 2f, h / 2f,
                                          new int[]{withAlpha(c1, 0), c1, c2, withAlpha(c2, 0)},
                                          new float[]{0f, 0.45f, 0.8f, 1f});
                ringPaint.setShader(sweep);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0 || sweep == null) return;

            float pad = 11f * density;
            float gap = 3f * density;
            float radius = (w - 2f * pad) * ICON_CORNER_FRACTION + gap;
            ringRect.set(pad - gap, pad - gap, w - pad + gap, h - pad + gap);

            float glowSize = pad - gap - density;
            int steps = 7;
            float step = glowSize / steps;
            float pulse = 0.65f + 0.35f * (float) Math.sin(progress * 2.0 * Math.PI);
            glowPaint.setStrokeWidth(step + 1f);
            for (int i = steps; i >= 1; i--) {
                float d = (i - 0.5f) / steps;
                float a = 0.45f * (1f - d) * (1f - d) * pulse;
                glowPaint.setColor(c1);
                glowPaint.setAlpha(Math.max(0, Math.min(255, (int) (255f * a))));
                float inflate = (i - 0.5f) * step;
                tmp.set(ringRect.left - inflate, ringRect.top - inflate, ringRect.right + inflate, ringRect.bottom + inflate);
                canvas.drawRoundRect(tmp, radius + inflate, radius + inflate, glowPaint);
            }

            canvas.drawRoundRect(ringRect, radius, radius, basePaint);
            matrix.setRotate(progress * 360f, w / 2f, h / 2f);
            sweep.setLocalMatrix(matrix);
            canvas.drawRoundRect(ringRect, radius, radius, ringPaint);
        }
    }

    // Thin gradient line with a light sweeping across (header accent)
    private static class ShimmerLine extends LoopView {
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final int c1, c2;
        private LinearGradient hiShader;

        ShimmerLine(Context context, int c1, int c2) {
            super(context, 2600);
            this.c1 = c1;
            this.c2 = c2;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0) return;
            basePaint.setShader(new LinearGradient(0, 0, w, 0,
                                                   new int[]{withAlpha(c1, 0), c1, c2, withAlpha(c2, 0)},
                                                   new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            hiShader = new LinearGradient(0, 0, w * 0.28f, 0,
                                          new int[]{0x00FFFFFF, 0xDDFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            hiPaint.setShader(hiShader);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || hiShader == null) return;
            canvas.drawRect(0, 0, w, h, basePaint);
            matrix.setTranslate(-w * 0.28f + progress * (w * 1.28f), 0f);
            hiShader.setLocalMatrix(matrix);
            canvas.drawRect(0, 0, w, h, hiPaint);
        }
    }

    // ================================================================
    // Premium toggle switch (animated thumb + glow)
    // ================================================================
    private static class ToggleView extends View {
        interface Listener { void onChanged(boolean checked); }

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final ArgbEvaluator argb = new ArgbEvaluator();
        private final float density;
        private final int onColor, onColor2, offColor;
        private boolean checked;
        private float t = 0f;
        private float scale = 1f;
        private float shaderScale = -1f;
        private LinearGradient onShader;
        private ValueAnimator anim;
        private Listener listener;

        ToggleView(Context context, int onColor, int onColor2, int offColor) {
            super(context);
            this.density = context.getResources().getDisplayMetrics().density;
            this.onColor = onColor;
            this.onColor2 = onColor2;
            this.offColor = offColor;
            glowPaint.setStyle(Paint.Style.FILL);
            thumbPaint.setStyle(Paint.Style.FILL);
            trackPaint.setStyle(Paint.Style.FILL);
        }

        void setListener(Listener l) { listener = l; }

        void setScale(float s) {
            if (Math.abs(s - scale) > 0.005f) {
                scale = s;
                requestLayout();
                invalidate();
            }
        }

        boolean isChecked() { return checked; }

        void setChecked(boolean value, boolean animate) {
            checked = value;
            float target = value ? 1f : 0f;
            if (anim != null) anim.cancel();
            if (!animate) {
                t = target;
                invalidate();
                return;
            }
            anim = ValueAnimator.ofFloat(t, target);
            anim.setDuration(260);
            anim.setInterpolator(new OvershootInterpolator(1.6f));
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        t = (Float) a.getAnimatedValue();
                        invalidate();
                    }
                });
            anim.start();
        }

        void toggle() {
            setChecked(!checked, true);
            if (listener != null) listener.onChanged(checked);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(Math.round(51f * scale * density), Math.round(28f * scale * density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = scale * density;
            float trackW = 38f * s;
            float trackH = 20f * s;
            float left = 3f * s;
            float top = (getHeight() - trackH) / 2f;
            float r = trackH / 2f;
            float tc = Math.max(0f, Math.min(1f, t));

            trackPaint.setShader(null);
            trackPaint.setColor(offColor);
            rect.set(left, top, left + trackW, top + trackH);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            if (tc > 0.01f) {
                if (onShader == null || shaderScale != s) {
                    onShader = new LinearGradient(left, 0, left + trackW, 0, onColor, onColor2, Shader.TileMode.CLAMP);
                    shaderScale = s;
                }
                trackPaint.setShader(onShader);
                trackPaint.setAlpha((int) (255f * tc));
                canvas.drawRoundRect(rect, r, r, trackPaint);
                trackPaint.setShader(null);
            }

            float thumbR = r - 2.6f * s;
            float pos = Math.max(-0.1f, Math.min(1.1f, t));
            float cx = left + r + (trackW - 2f * r) * pos;
            float cy = top + r;

            if (tc > 0.01f) {
                for (int k = 3; k >= 1; k--) {
                    glowPaint.setColor(onColor);
                    glowPaint.setAlpha((int) (255f * 0.15f * tc * (4 - k) / 3f));
                    canvas.drawCircle(cx, cy, thumbR + k * 1.7f * s, glowPaint);
                }
            }
            thumbPaint.setColor((Integer) argb.evaluate(tc, 0xFF9AA9CF, 0xFFFFFFFF));
            canvas.drawCircle(cx, cy, thumbR, thumbPaint);
        }
    }

    // ================================================================
    // Seek bar track: rounded gradient bar with a soft glow
    // ================================================================
    private static class SeekTrackDrawable extends Drawable {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float barH;
        private final int c1;
        private final int c2;

        SeekTrackDrawable(float barH, int trackColor, int c1, int c2) {
            this.barH = barH;
            this.c1 = c1;
            this.c2 = c2;
            trackPaint.setColor(trackColor);
            glowPaint.setColor(c1);
            glowPaint.setAlpha(46);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            super.onBoundsChange(b);
            progPaint.setShader(new LinearGradient(b.left, 0, Math.max(b.right, b.left + 1), 0, c1, c2, Shader.TileMode.CLAMP));
        }

        @Override
        protected boolean onLevelChange(int level) {
            invalidateSelf();
            return true;
        }

        @Override public int getIntrinsicHeight() { return (int) barH; }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0) return;
            float cy = b.exactCenterY();
            float top = cy - barH / 2f;
            float bottom = cy + barH / 2f;
            float r = barH / 2f;

            rect.set(b.left, top, b.right, bottom);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            float frac = getLevel() / 10000f;
            float right = b.left + b.width() * frac;
            if (right - b.left > 0.5f) {
                float gh = barH * 0.4f;
                rect.set(b.left, top - gh, Math.max(right, b.left + barH), bottom + gh);
                canvas.drawRoundRect(rect, r + gh, r + gh, glowPaint);
                rect.set(b.left, top, Math.max(right, b.left + barH), bottom);
                canvas.drawRoundRect(rect, r, r, progPaint);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================
    // Slim, themed scroll view (custom drawn scrollbar, native drag + fling)
    // ================================================================
    private static class SlimScrollView extends ScrollView {
        private static final int ACTIVE_ALPHA = 255;
        private static final int IDLE_ALPHA = 0;

        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float density;
        private int barAlpha = IDLE_ALPHA;

        private final Runnable fadeRunnable = new Runnable() {
            @Override
            public void run() {
                barAlpha = Math.max(IDLE_ALPHA, barAlpha - 25);
                invalidate();
                if (barAlpha > IDLE_ALPHA) postDelayed(this, 30);
            }
        };

        SlimScrollView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            setVerticalScrollBarEnabled(false);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setWillNotDraw(false);
        }

        private void flashBar() {
            barAlpha = ACTIVE_ALPHA;
            removeCallbacks(fadeRunnable);
            postDelayed(fadeRunnable, 700);
            invalidate();
        }

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            flashBar();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(fadeRunnable);
            super.onDetachedFromWindow();
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (getChildCount() == 0) return;

            final int viewH = getHeight();
            final int contentH = getChildAt(0).getHeight();
            final int range = contentH - viewH;
            if (viewH <= 0 || range <= 0) return;

            final float inset = 4f * density;
            final float trackH = viewH - inset * 2f;
            float thumbH = trackH * viewH / (float) contentH;
            thumbH = Math.max(Math.min(24f * density, trackH), thumbH);

            final float progress = Math.max(0f, Math.min(1f, getScrollY() / (float) range));
            final float w = 3f * density;
            final float right = getScrollX() + getWidth() - 1.5f * density;
            final float left = right - w;
            final float trackTop = getScrollY() + inset;
            final float thumbTop = trackTop + (trackH - thumbH) * progress;
            final float r = w / 2f;

            trackPaint.setColor(0xFF00E5FF);
            trackPaint.setAlpha(barAlpha * 26 / 255);
            rect.set(left, trackTop, right, trackTop + trackH);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            thumbPaint.setColor(0xFF00E5FF);
            thumbPaint.setAlpha(barAlpha);
            rect.set(left, thumbTop, right, thumbTop + thumbH);
            canvas.drawRoundRect(rect, r, r, thumbPaint);
        }
    }

    // ================================================================
    // Tab icons (drawn in code, no drawable resources needed)
    // ================================================================
    private static class TabIcon extends Drawable {
        static final int HOME = 0, USER = 1, RUN = 2, BOLT = 3, EYE = 4,
        GEAR = 5, PULSE = 6, TARGET = 7, GRID = 8, CHEVRON = 9;

        private final int type;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        TabIcon(int type) {
            this.type = type;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(2f);
            fill.setStyle(Paint.Style.FILL);
            setColor(Color.WHITE);
        }

        void setColor(int color) {
            stroke.setColor(color);
            fill.setColor(color);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;

            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);
            path.reset();

            switch (type) {
                case HOME:
                    path.moveTo(3f, 11f); path.lineTo(12f, 3f); path.lineTo(21f, 11f);
                    canvas.drawPath(path, stroke);
                    path.reset();
                    path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 20f); path.lineTo(18.5f, 20f); path.lineTo(18.5f, 9.5f);
                    canvas.drawPath(path, stroke);
                    path.reset();
                    path.moveTo(10f, 20f); path.lineTo(10f, 14f); path.lineTo(14f, 14f); path.lineTo(14f, 20f);
                    canvas.drawPath(path, stroke);
                    break;

                case USER:
                    canvas.drawCircle(12f, 7.5f, 3.8f, stroke);
                    path.moveTo(4.5f, 21f);
                    path.cubicTo(4.5f, 15.5f, 8f, 13.5f, 12f, 13.5f);
                    path.cubicTo(16f, 13.5f, 19.5f, 15.5f, 19.5f, 21f);
                    canvas.drawPath(path, stroke);
                    break;

                case RUN:
                    path.moveTo(5f, 5f); path.lineTo(12f, 12f); path.lineTo(5f, 19f);
                    canvas.drawPath(path, stroke);
                    path.reset();
                    path.moveTo(12f, 5f); path.lineTo(19f, 12f); path.lineTo(12f, 19f);
                    canvas.drawPath(path, stroke);
                    break;

                case BOLT:
                    path.moveTo(13f, 2f); path.lineTo(4.5f, 13.5f); path.lineTo(11f, 13.5f);
                    path.lineTo(10f, 22f); path.lineTo(19.5f, 9.5f); path.lineTo(13f, 9.5f);
                    path.close();
                    canvas.drawPath(path, stroke);
                    break;

                case EYE:
                    path.moveTo(1.5f, 12f);
                    path.quadTo(12f, 0.5f, 22.5f, 12f);
                    path.quadTo(12f, 23.5f, 1.5f, 12f);
                    path.close();
                    canvas.drawPath(path, stroke);
                    canvas.drawCircle(12f, 12f, 3.2f, stroke);
                    break;

                case GEAR: {
                        final int teeth = 8;
                        final float rIn = 7.2f, rOut = 10f;
                        final double[] offs = {-17, -9, 9, 17};
                        final float[] radii = {rIn, rOut, rOut, rIn};
                        for (int i = 0; i < teeth; i++) {
                            double c = Math.toRadians(i * 360.0 / teeth);
                            for (int k = 0; k < 4; k++) {
                                double a = c + Math.toRadians(offs[k]);
                                float x = 12f + (float) (radii[k] * Math.cos(a));
                                float y = 12f + (float) (radii[k] * Math.sin(a));
                                if (i == 0 && k == 0) path.moveTo(x, y);
                                else path.lineTo(x, y);
                            }
                        }
                        path.close();
                        canvas.drawPath(path, stroke);
                        canvas.drawCircle(12f, 12f, 3f, stroke);
                        break;
                    }

                case PULSE:
                    path.moveTo(1.5f, 12f); path.lineTo(7f, 12f); path.lineTo(10f, 4.5f);
                    path.lineTo(14f, 19.5f); path.lineTo(17f, 12f); path.lineTo(22.5f, 12f);
                    canvas.drawPath(path, stroke);
                    break;

                case TARGET:
                    canvas.drawCircle(12f, 12f, 7f, stroke);
                    canvas.drawCircle(12f, 12f, 1.8f, fill);
                    canvas.drawLine(12f, 2f, 12f, 5f, stroke);
                    canvas.drawLine(12f, 19f, 12f, 22f, stroke);
                    canvas.drawLine(2f, 12f, 5f, 12f, stroke);
                    canvas.drawLine(19f, 12f, 22f, 12f, stroke);
                    break;

                case CHEVRON:
                    path.moveTo(5.5f, 9f); path.lineTo(12f, 15.5f); path.lineTo(18.5f, 9f);
                    canvas.drawPath(path, stroke);
                    break;

                default:
                    rect.set(4f, 4f, 10.5f, 10.5f);   canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                    rect.set(13.5f, 4f, 20f, 10.5f);  canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                    rect.set(4f, 13.5f, 10.5f, 20f);  canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                    rect.set(13.5f, 13.5f, 20f, 20f); canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                    break;
            }
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { stroke.setAlpha(alpha); fill.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter cf) { stroke.setColorFilter(cf); fill.setColorFilter(cf); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static int iconTypeFor(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.contains("setting")) return TabIcon.GEAR;
        if (n.contains("diag")) return TabIcon.PULSE;
        if (n.contains("main") || n.contains("home")) return TabIcon.HOME;
        if (n.contains("esp") || n.contains("visual") || n.contains("wall")) return TabIcon.EYE;
        if (n.contains("aim") || n.contains("weapon") || n.contains("gun") || n.contains("target") || n.contains("combat")) return TabIcon.TARGET;
        if (n.contains("move") || n.contains("speed") || n.contains("fly")) return TabIcon.RUN;
        if (n.contains("power")) return TabIcon.BOLT;
        if (n.contains("surviv") || n.contains("player") || n.contains("people")) return TabIcon.USER;
        return TabIcon.GRID;
    }

    private static class TabHolder {
        String name;
        LinearLayout root;
        ImageView icon;
        TabIcon iconDrawable;
        TextView label;
    }

    // ================================================================
    // Resize handle touch listener
    // ================================================================
    private View.OnTouchListener resizeTouchListener = new View.OnTouchListener() {
        private int startW, startH;
        private float startX, startY;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                        startW = menuFrame.getWidth();
                        startH = menuFrame.getHeight();
                        if (startW <= 0) startW = Menu.this.dp(MENU_WIDTH);
                        if (startH <= 0) startH = Menu.this.dp(MENU_HEIGHT);
                        startX = event.getRawX();
                        startY = event.getRawY();
                        v.setAlpha(0.6f);
                        return true;
                    }
                case MotionEvent.ACTION_MOVE: {
                        int dx = (int)(event.getRawX() - startX);
                        int dy = (int)(event.getRawY() - startY);
                        int newW = startW + dx;
                        int newH = startH + dy;

                        int minW = Menu.this.dp(MIN_MENU_WIDTH_DP);
                        int minH = Menu.this.dp(MIN_MENU_HEIGHT_DP);
                        int maxW = Math.max(minW, Menu.this.screenW() - (vmParams != null ? vmParams.x : 0));
                        int maxH = Math.max(minH, Menu.this.screenH() - (vmParams != null ? vmParams.y : 0));
                        newW = Math.max(minW, Math.min(newW, maxW));
                        newH = Math.max(minH, Math.min(newH, maxH));

                        ViewGroup.LayoutParams mlp = menuFrame.getLayoutParams();
                        if (mlp.width != newW || mlp.height != newH) {
                            mlp.width  = newW;
                            mlp.height = newH;
                            menuFrame.setLayoutParams(mlp);
                        }

                        if (mWindowManager != null && rootFrame != null && vmParams != null) {
                            try { mWindowManager.updateViewLayout(rootFrame, vmParams); } catch (Exception ignored) {}
                        }
                        return true;
                    }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                        v.setAlpha(1f);
                        if (mWindowManager != null && rootFrame != null && vmParams != null) {
                            try { mWindowManager.updateViewLayout(rootFrame, vmParams); } catch (Exception ignored) {}
                        }
                        return true;
                    }
            }
            return false;
        }
    };

    // ================================================================
    // Responsive layout: text / padding scale + sidebar width + compact tabs
    // ================================================================
    private void applyResponsiveScale(int menuWidthPx) {
        if (menuWidthPx <= 0) return;
        int designW = dp(MENU_WIDTH);
        if (designW <= 0) return;

        float scale = (float) menuWidthPx / (float) designW;
        if (scale < MIN_SCALE_FACTOR) scale = MIN_SCALE_FACTOR;
        if (scale > MAX_SCALE_FACTOR) scale = MAX_SCALE_FACTOR;

        int frameInset = menuFrame.getPaddingLeft() + menuFrame.getPaddingRight();
        int innerW = Math.max(1, menuWidthPx - frameInset);

        boolean compact = SHOW_TAB_ICONS && menuWidthPx < dp(COMPACT_TABS_BELOW_DP);
        int sidebarW = compact
            ? dp(48)
            : Math.max(dp(84), Math.min(dp(140), Math.round(innerW * 0.37f)));

        boolean changed = Math.abs(scale - currentScale) >= 0.01f
            || compact != compactMode
            || sidebarW != lastSidebarW;
        if (!changed) return;

        currentScale = scale;
        compactMode = compact;

        if (sidebarW != lastSidebarW && sidebarScroll != null) {
            lastSidebarW = sidebarW;
            ViewGroup.LayoutParams slp = sidebarScroll.getLayoutParams();
            slp.width = sidebarW;
            sidebarScroll.setLayoutParams(slp);
        }

        rescaleAll();
        applyTabMetrics();
    }

    private void rescaleAll() {
        scaleTextViewsIn(mExpanded);
        // category pages that are not currently attached
        for (LinearLayout cat : categoryViewsMap.values()) {
            if (cat != null && cat.getParent() == null) scaleTextViewsIn(cat);
        }
        // refresh spinner rows so they follow the new scale
        for (ArrayAdapter<String> ad : spinnerAdapters) {
            try { ad.notifyDataSetChanged(); } catch (Exception ignored) {}
        }
    }

    private void scaleTextViewsIn(View v) {
        if (v == null) return;
        if (v instanceof WebView || v instanceof AdapterView) return;

        if (v instanceof ToggleView) {
            ((ToggleView) v).setScale(currentScale);
        } else if (v instanceof TextView) {
            TextView tv = (TextView) v;
            float[] base = textBase.get(tv);
            if (base == null) {
                base = new float[] {
                    tv.getTextSize(),        // px
                    tv.getPaddingLeft(),
                    tv.getPaddingTop(),
                    tv.getPaddingRight(),
                    tv.getPaddingBottom()
                };
                textBase.put(tv, base);
            }
            float s = currentScale;
            try {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, base[0] * s);
                tv.setPadding((int) (base[1] * s), (int) (base[2] * s), (int) (base[3] * s), (int) (base[4] * s));
            } catch (Exception ignored) {}
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                scaleTextViewsIn(vg.getChildAt(i));
            }
        }
    }

    private void applyTabMetrics() {
        float s = currentScale;
        int iconSize = Math.max(dp(12), (int) (dp(18) * s));
        for (TabHolder h : tabHolders) {
            boolean iconOnly = compactMode && h.icon != null;
            if (h.icon != null) {
                ViewGroup.LayoutParams ilp = h.icon.getLayoutParams();
                if (ilp.width != iconSize || ilp.height != iconSize) {
                    ilp.width = iconSize;
                    ilp.height = iconSize;
                    h.icon.setLayoutParams(ilp);
                }
            }
            h.label.setVisibility(iconOnly ? View.GONE : View.VISIBLE);
            h.root.setGravity(iconOnly ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
            h.root.setPadding((int) (dp(iconOnly ? 2 : 9) * s), (int) (dp(10) * s),
                              (int) (dp(iconOnly ? 2 : 9) * s), (int) (dp(10) * s));
        }
    }

    // ================================================================
    // Window drag (title bar + collapsed icon)
    // ================================================================
    private View.OnTouchListener onTouchListener() {
        return new View.OnTouchListener() {
            private float initialTouchX, initialTouchY;
            private int   initialX, initialY;
            private boolean moved;
            private final int slop = ViewConfiguration.get(getContext).getScaledTouchSlop();

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (vmParams == null) return true;
                switch (motionEvent.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = vmParams.x;
                        initialY = vmParams.y;
                        initialTouchX = motionEvent.getRawX();
                        initialTouchY = motionEvent.getRawY();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE: {
                            float dx = motionEvent.getRawX() - initialTouchX;
                            float dy = motionEvent.getRawY() - initialTouchY;
                            if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) moved = true;
                            if (moved) {
                                menuFrame.setAlpha(0.7f);
                                mCollapsed.setAlpha(0.6f);
                                moveWindow(initialX + (int) dx, initialY + (int) dy);
                            }
                            return true;
                        }

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        menuFrame.setAlpha(1f);
                        mCollapsed.setAlpha(1f);
                        if (!moved && motionEvent.getActionMasked() == MotionEvent.ACTION_UP && isViewCollapsed()) {
                            try {
                                expandMenu();
                            } catch (NullPointerException e) {}
                        }
                        return true;

                    default:
                        return false;
                }
            }
        };
    }

    // ================================================================
    // Build features / categories
    // ================================================================
    private void buildFeaturesAndCategories(String[] features) {
        categoryViewsMap.clear();
        categoryNames.clear();
        tabHolders.clear();
        spinnerAdapters.clear();
        sidebarLayout.removeAllViews();
        contentLayout.removeAllViews();

        String currentCategory = "Main";
        LinearLayout defaultLayout = newCategoryLayout();
        categoryViewsMap.put(currentCategory, defaultLayout);
        categoryNames.add(currentCategory);

        int subFeat = 0;
        for (int i = 0; i < features.length; i++) {
            String feature = features[i];
            boolean switchedOn = false;
            if (feature.contains("_True")) switchedOn = true;
            feature = feature.replaceFirst("_True", "");

            boolean isCollapseAdd = feature.contains("CollapseAdd_");
            if (isCollapseAdd) feature = feature.replaceFirst("CollapseAdd_", "");

            String[] str = feature.split("_");
            int featNum;
            if (TextUtils.isDigitsOnly(str[0]) || str[0].matches("-[0-9]*")) {
                featNum = Integer.parseInt(str[0]);
                feature = feature.replaceFirst(str[0] + "_", "");
                subFeat++;
            } else {
                featNum = i - subFeat;
            }
            String[] strSplit = feature.split("_");
            String type = strSplit[0];
            String featName = strSplit.length > 1 ? strSplit[1] : "";

            if (type.equals("Category")) {
                currentCategory = strSplit[1];
                if (!categoryNames.contains(currentCategory)) {
                    categoryNames.add(currentCategory);
                    categoryViewsMap.put(currentCategory, newCategoryLayout());
                }
            } else {
                LinearLayout targetLayout = (isCollapseAdd) ? mCollapse : categoryViewsMap.get(currentCategory);
                if (targetLayout == null) targetLayout = categoryViewsMap.get("Main");

                switch (type) {
                    case "Toggle": Switch(targetLayout, featNum, featName, switchedOn); break;
                    case "SeekBar": SeekBar(targetLayout, featNum, featName, Integer.parseInt(strSplit[2]), Integer.parseInt(strSplit[3])); break;
                    case "Button": Button(targetLayout, featNum, featName); break;
                    case "ButtonOnOff": ButtonOnOff(targetLayout, featNum, featName, switchedOn); break;
                    case "Spinner": Spinner(targetLayout, featNum, featName, strSplit[2]); break;
                    case "InputText": InputText(targetLayout, featNum, featName); break;
                    case "InputValue": InputNum(targetLayout, featNum, strSplit.length == 3 ? strSplit[2] : strSplit[1], strSplit.length == 3 ? Integer.parseInt(strSplit[1]) : 0); break;
                    case "InputLValue": InputLNum(targetLayout, featNum, strSplit.length == 3 ? strSplit[2] : strSplit[1], strSplit.length == 3 ? Long.parseLong(strSplit[1]) : 0); break;
                    case "CheckBox": CheckBox(targetLayout, featNum, featName, switchedOn); break;
                    case "RadioButton": RadioButton(targetLayout, featNum, featName, strSplit[2]); break;
                    case "Collapse": Collapse(targetLayout, featName, switchedOn); break;
                    case "ButtonLink": ButtonLink(targetLayout, featName, strSplit[2]); break;
                    case "RichTextView": TextView(targetLayout, featName); break;
                    case "RichWebView": WebTextView(targetLayout, featName); break;
                    case "ColorPicker": ColorPicker(targetLayout, featNum, featName, strSplit.length > 2 ? strSplit[2] : "#00FF88"); break;
                }
            }
        }

        if (!categoryNames.contains("Power")) {
            int idx = categoryNames.indexOf("Settings");
            if (idx == -1) idx = categoryNames.size();
            categoryNames.add(idx, "Power");
            categoryViewsMap.put("Power", newCategoryLayout());
        }

        if (categoryViewsMap.containsKey("Settings")) {
            LinearLayout settingsLay = categoryViewsMap.get("Settings");
            settingsLay.removeAllViews();
            Switch(settingsLay, -1, "Save features preference", Preferences.loadPref);
            Switch(settingsLay, -3, "Auto size", Preferences.isExpanded);
            Button(settingsLay, -6, "Close Menu");
        }

        setupSidebarTabs();
        rescaleAll();
        applyTabMetrics();
    }

    private LinearLayout newCategoryLayout() {
        LinearLayout l = new LinearLayout(getContext);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    // ================================================================
    // Sidebar tabs
    // ================================================================
    private void setupSidebarTabs() {
        tabHolders.clear();
        sidebarLayout.removeAllViews();

        for (final String catName : categoryNames) {
            final TabHolder h = new TabHolder();
            h.name = catName;

            LinearLayout tab = new LinearLayout(getContext);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            lp.setMargins(0, dp(3), 0, dp(3));
            tab.setLayoutParams(lp);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(dp(9), dp(10), dp(9), dp(10));
            tab.setTag(h);
            h.root = tab;

            if (SHOW_TAB_ICONS) {
                h.iconDrawable = new TabIcon(iconTypeFor(catName));
                ImageView iv = new ImageView(getContext);
                iv.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageDrawable(h.iconDrawable);
                h.icon = iv;
                tab.addView(iv);
            }

            TextView tabText = new TextView(getContext);
            tabText.setText(catName);
            tabText.setTextColor(COLOR_TEXT_MUTED);
            tabText.setTextSize(12);
            tabText.setTypeface(fontMedium);
            tabText.setSingleLine(true);
            tabText.setEllipsize(TextUtils.TruncateAt.END);
            tabText.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
            lpText.setMargins(SHOW_TAB_ICONS ? dp(7) : 0, 0, 0, 0);
            tabText.setLayoutParams(lpText);
            h.label = tabText;
            tab.addView(tabText);

            tab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectTab(h);
                    }
                });
            addPressAnim(tab);

            tabHolders.add(h);
            sidebarLayout.addView(tab);
            styleTab(h, false);
        }

        if (tabHolders.size() > 0) selectTab(tabHolders.get(0));
    }

    private void styleTab(TabHolder h, boolean selected) {
        GradientDrawable bg;
        if (selected) {
            bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                      new int[]{withAlpha(COLOR_ACCENT, 0x38), withAlpha(COLOR_ACCENT_2, 0x1C)});
            bg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0xAA));
        } else {
            bg = new GradientDrawable();
            bg.setColor(0x0DFFFFFF);
        }
        bg.setCornerRadius(dp(10));
        h.root.setBackground(bg);
        h.label.setTextColor(selected ? Color.WHITE : COLOR_TEXT_MUTED);
        h.label.setTypeface(selected ? fontBold : fontMedium);
        if (h.iconDrawable != null) {
            h.iconDrawable.setColor(selected ? COLOR_ACCENT : COLOR_TEXT_MUTED);
        }
    }

    private void selectTab(TabHolder selected) {
        for (TabHolder t : tabHolders) styleTab(t, t == selected);

        contentLayout.removeAllViews();
        LinearLayout selectedCatView = categoryViewsMap.get(selected.name);
        if (selectedCatView != null) {
            if (selectedCatView.getParent() != null) {
                ((ViewGroup) selectedCatView.getParent()).removeView(selectedCatView);
            }
            contentLayout.addView(selectedCatView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        contentScrollView.scrollTo(0, 0);

        // content glides in, selected tab pops
        contentLayout.animate().cancel();
        contentLayout.setAlpha(0f);
        contentLayout.setTranslationY(dp(10));
        contentLayout.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(new DecelerateInterpolator(1.6f)).start();

        selected.root.setScaleX(0.93f);
        selected.root.setScaleY(0.93f);
        selected.root.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new OvershootInterpolator(2.2f)).start();
    }

    private boolean selectTabByName(String name) {
        for (TabHolder h : tabHolders) {
            if (h.name.equals(name)) {
                selectTab(h);
                return true;
            }
        }
        return false;
    }

    public void ShowMenu() {
    rootFrame.addView(mRootContainer);
    final Handler handler = new Handler();
    handler.postDelayed(new Runnable() {
        boolean viewLoaded = false;
        @Override
        public void run() {
            if (Preferences.loadPref && !IsGameLibLoaded() && !stopChecking) {
                if (!viewLoaded) {
                    contentLayout.removeAllViews();
                    TextView waitTxt = new TextView(getContext);
                    waitTxt.setText("Waiting for game lib...");
                    waitTxt.setTextColor(COLOR_ACCENT);
                    waitTxt.setTypeface(fontMedium);
                    waitTxt.setTextSize(12f);
                    waitTxt.setGravity(Gravity.CENTER);
                    waitTxt.setPadding(0, dp(22), 0, dp(14));
                    AlphaAnimation blink = new AlphaAnimation(1f, 0.3f);
                    blink.setDuration(850);
                    blink.setRepeatMode(Animation.REVERSE);
                    blink.setRepeatCount(Animation.INFINITE);
                    waitTxt.startAnimation(blink);
                    contentLayout.addView(waitTxt);

                    Button forceBtn = new Button(getContext);
                    forceBtn.setBackground(gradBg(BTN_GRAD_1, BTN_GRAD_2, 12));
                    forceBtn.setTextColor(Color.WHITE);
                    forceBtn.setTypeface(fontBold);
                    forceBtn.setTextSize(12f);
                    forceBtn.setAllCaps(false);
                    forceBtn.setMinHeight(0);
                    forceBtn.setMinimumHeight(0);
                    forceBtn.setPadding(dp(10), dp(11), dp(10), dp(11));
                    forceBtn.setText("Force load menu");
                    forceBtn.setLayoutParams(rowLp(8, 4, 8, 4));
                    flatten(forceBtn);
                    addPressAnim(forceBtn);
                    forceBtn.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                stopChecking = true;
                                showLoginScreen();
                            }
                        });
                    contentLayout.addView(forceBtn);
                    scaleTextViewsIn(contentLayout);
                    viewLoaded = true;
                }
                handler.postDelayed(this, 600);
            } else {
                // Game lib ready → login বা main menu
                if (!isLoggedIn) {
                    showLoginScreen();
                } else {
                    buildFeaturesAndCategories(GetFeatureList());
                }
            }
        }
    }, 500);
}

// ================================================================
// 🔥 NEW: Login Overlay — floating menu এর উপরে login UI দেখায়
// ================================================================
// ================================================================
// 🔥 Login screen — sidebar লুকিয়ে content area তে login দেখায়
// ================================================================
private void showLoginScreen() {
    if (isLoggedIn) return;

    // Sidebar hide
    if (sidebarScroll != null) sidebarScroll.setVisibility(View.GONE);
    if (sidebarDivider != null) sidebarDivider.setVisibility(View.GONE);

    contentLayout.removeAllViews();

    // Window focusable (keyboard support)
    setWindowFocusable(true);

    LoginHelper loginHelper = new LoginHelper(getContext, new LoginHelper.Callback() {
        @Override
        public void onLoginSuccess() {
            setWindowFocusable(false);
            isLoggedIn = true;

            if (sidebarScroll != null) sidebarScroll.setVisibility(View.VISIBLE);
            if (sidebarDivider != null) sidebarDivider.setVisibility(View.VISIBLE);

            contentLayout.removeAllViews();
            buildFeaturesAndCategories(GetFeatureList());
        }
    });

    View loginView = loginHelper.buildView();
    contentLayout.addView(loginView,
            new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

    if (isViewCollapsed()) {
        menuFrame.post(new Runnable() {
            @Override
            public void run() {
                try { expandMenu(); } catch (Exception ignored) { }
            }
        });
    }

// 🔥 Landscape এ menu height force adjust (login form যাতে fit হয়)
menuFrame.postDelayed(new Runnable() {
    @Override
    public void run() {
        try {
            ViewGroup.LayoutParams lp = menuFrame.getLayoutParams();
            int orientation = getContext.getResources().getConfiguration().orientation;
            int targetH;

            if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                targetH = screenH() - dp(15);   // Landscape: পুরো height
            } else {
                targetH = dp(380);               // Portrait: comfortable
                if (targetH > screenH() - vmParams.y - dp(10)) {
                    targetH = screenH() - vmParams.y - dp(10);
                }
            }

            if (lp.height != targetH) {
                lp.height = targetH;
                menuFrame.setLayoutParams(lp);
                if (mWindowManager != null) {
                    mWindowManager.updateViewLayout(rootFrame, vmParams);
                }
            }
        } catch (Exception ignored) { }
    }
}, 250);
}
    @SuppressLint("WrongConstant")
    public void SetWindowManagerWindowService() {
        int iparams = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 2038 : 2002;
        vmParams = new WindowManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, iparams, 8 | FLAG_TRANSLUCENT_STATUS, -3);
        vmParams.gravity = 51;
        vmParams.x = POS_X;
        vmParams.y = effectivePosY;
        vmParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    vmParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                           | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
        mWindowManager = (WindowManager) getContext.getSystemService(getContext.WINDOW_SERVICE);
        mWindowManager.addView(rootFrame, vmParams);
        overlayRequired = true;
    }

    @SuppressLint("WrongConstant")
    public void SetWindowManagerActivity() {
        vmParams = new WindowManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, POS_X, POS_Y, WindowManager.LayoutParams.TYPE_APPLICATION, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH, PixelFormat.TRANSPARENT);
        vmParams.gravity = 51;
        vmParams.x = POS_X;
        vmParams.y = effectivePosY;
        mWindowManager = ((Activity) getContext).getWindowManager();
        mWindowManager.addView(rootFrame, vmParams);
    }

    private boolean isViewCollapsed() { return rootFrame == null || mCollapsed.getVisibility() == View.VISIBLE; }
    private int convertDipToPixels(int i) { return (int)((i * getContext.getResources().getDisplayMetrics().density) + 0.5f); }
    private int dp(int i) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) i, getContext.getResources().getDisplayMetrics()); }
    private float dpf(float v) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getContext.getResources().getDisplayMetrics()); }
    public void setVisibility(int view) { if (rootFrame != null) rootFrame.setVisibility(view); }
// ================================================================
// 🔥 Login এর সময় window focusable বানাই যাতে keyboard কাজ করে
// ================================================================
// ================================================================
// 🔥 Window focusable + IME (keyboard) support enable
// Game overlays এ keyboard কাজ করার জন্য এই method critical
// ================================================================
private void setWindowFocusable(boolean focusable) {
    if (vmParams == null || mWindowManager == null || rootFrame == null) return;
    try {
        if (focusable) {
            savedWindowFlags = vmParams.flags;

            vmParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            vmParams.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            vmParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

            vmParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                                   | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;

            windowIsFocusable = true;
            mWindowManager.updateViewLayout(rootFrame, vmParams);

            rootFrame.requestFocus();
            rootFrame.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputMethodManager imm = (InputMethodManager)
                                getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.restartInput(rootFrame);
                    } catch (Exception ignored) { }
                }
            }, 100);
        } else {
            if (windowIsFocusable) {
                vmParams.flags = savedWindowFlags;
                vmParams.softInputMode = 0;
                windowIsFocusable = false;
                mWindowManager.updateViewLayout(rootFrame, vmParams);

                try {
                    InputMethodManager imm = (InputMethodManager)
                            getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(rootFrame.getWindowToken(), 0);
                } catch (Exception ignored) { }
            }
        }
    } catch (Exception e) {
        Log.e(TAG, "setWindowFocusable: " + e);
    }
}
    public void onDestroy() {
    stopGlowAnimator();
    if (rootFrame != null && mWindowManager != null) {
        try { mWindowManager.removeView(rootFrame); } catch (Exception e) {}
    }
    if (espview != null && espWindowManager != null) {
        try { espWindowManager.removeView(espview); } catch (Exception e) {}
    }
}

    // ================================================================
    // Styled dialog (premium replacement for the default AlertDialog look)
    // ================================================================
    private Button dialogButton(String text, boolean primary) {
        Button b = new Button(getContext);
        b.setText(text);
        b.setAllCaps(false);
        b.setTypeface(fontBold);
        b.setTextSize(12f);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        if (primary) {
            b.setTextColor(Color.WHITE);
            b.setBackground(gradBg(BTN_GRAD_1, BTN_GRAD_2, 10));
        } else {
            b.setTextColor(COLOR_TEXT_MUTED);
            b.setBackground(cardBg(0x0FFFFFFF, COLOR_CARD_BORDER, 10));
        }
        flatten(b);
        addPressAnim(b);
        return b;
    }

    private AlertDialog showStyledDialog(String title, View body,
                                         String posText, final Runnable onPos,
                                         String negText, final Runnable onNeg) {
        final Context ctx = getContext;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(14));
        GradientDrawable boxBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                                                      new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        boxBg.setCornerRadius(dp(18));
        boxBg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0xAA));
        box.setBackground(boxBg);

        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(COLOR_ACCENT);
        t.setTypeface(fontBold);
        t.setTextSize(15f);
        box.addView(t);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        blp.setMargins(0, dp(12), 0, 0);
        box.addView(body, blp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        rlp.setMargins(0, dp(14), 0, 0);
        box.addView(row, rlp);

        final AlertDialog[] ref = new AlertDialog[1];

        if (negText != null) {
            Button neg = dialogButton(negText, false);
            neg.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (onNeg != null) onNeg.run();
                        if (ref[0] != null) ref[0].dismiss();
                    }
                });
            row.addView(neg);
        }
        if (posText != null) {
            Button pos = dialogButton(posText, true);
            pos.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (onPos != null) onPos.run();
                        if (ref[0] != null) ref[0].dismiss();
                    }
                });
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            plp.setMargins(dp(8), 0, 0, 0);
            row.addView(pos, plp);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setView(box);
        AlertDialog dialog = builder.create();
        ref[0] = dialog;
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (overlayRequired) {
                w.setType(Build.VERSION.SDK_INT >= 26 ? 2038 : 2002);
            }
        }
        dialog.show();
        return dialog;
    }

    private EditText makeEditText() {
        EditText e = new EditText(getContext);
        e.setTextColor(TEXT_COLOR_2);
        e.setHintTextColor(COLOR_TEXT_MUTED);
        e.setTypeface(fontMedium);
        e.setTextSize(14f);
        e.setSingleLine(true);
        e.setBackground(cardBg(COLOR_CARD, withAlpha(COLOR_ACCENT, 0x88), 10));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }

    // Shared keyboard behaviour for all input dialogs
    private void showInputDialog(String title, final EditText editText, final Runnable onOk) {
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (hasFocus) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                    else imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                }
            });
        editText.requestFocus();

        showStyledDialog(title, editText,
            "OK", new Runnable() {
                @Override
                public void run() {
                    onOk.run();
                    editText.setFocusable(false);
                }
            },
            "Cancel", new Runnable() {
                @Override
                public void run() {
                    InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                }
            });
    }

    // ================================================================
    // ColorPicker — visual color chart dialog (feature #108)
    // ================================================================
    private void ColorPicker(LinearLayout linLayout, final int featNum, final String featName, final String defaultHex) {
        final int[] PRESET_COLORS = {
            0xFF00FF88, 0xFFFF00FF, 0xFF00FFFF, 0xFFFF0000,
            0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFFFF8000,
            0xFFFF00AA, 0xFF9C27B0, 0xFFFFFFFF, 0xFF000000,
            0xFFFFD700, 0xFF00BFFF, 0xFF32CD32, 0xFFFF4500,
            0xFF00CED1, 0xFF7FFF00, 0xFFDC143C, 0xFF8A2BE2,
            0xFF20B2AA, 0xFFF0E68C, 0xFFFF69B4, 0xFFADFF2F
        };
        final String[] COLOR_NAMES = {
            "Default Green", "Magenta", "Cyan", "Red", "Green", "Blue",
            "Yellow", "Orange", "Pink", "Purple", "White", "Black",
            "Gold", "Sky Blue", "Lime", "Orange Red",
            "Turquoise", "Chartreuse", "Crimson", "Violet",
            "Sea Green", "Khaki", "Hot Pink", "Green Yellow"
        };

        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setAllCaps(false);
        button.setTypeface(fontBold);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(13), dp(10), dp(13));
        flatten(button);
        addPressAnim(button);

        int savedColor = Preferences.loadPrefInt(featName, featNum);
        if (savedColor == 0) {
            try { savedColor = Color.parseColor(defaultHex); }
            catch (Exception e) { savedColor = 0xFF00FF88; }
        }

        applyColorButton(button, featName, savedColor);
        Preferences.changeFeatureInt(featName, featNum, savedColor);

        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ScrollView scroll = new ScrollView(getContext);
                    LinearLayout grid = new LinearLayout(getContext);
                    grid.setOrientation(LinearLayout.VERTICAL);
                    grid.setPadding(dp(2), dp(2), dp(2), dp(2));

                    final AlertDialog[] dialogRef = new AlertDialog[1];

                    final int cols = 4;
                    for (int i = 0; i < PRESET_COLORS.length; i += cols) {
                        LinearLayout row = new LinearLayout(getContext);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

                        int added = 0;
                        for (int j = 0; j < cols && i + j < PRESET_COLORS.length; j++) {
                            final int color = PRESET_COLORS[i + j];
                            final String cname = COLOR_NAMES[i + j];

                            LinearLayout cell = new LinearLayout(getContext);
                            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
                            cellLp.setMargins(dp(3), dp(5), dp(3), dp(5));
                            cell.setLayoutParams(cellLp);
                            cell.setOrientation(LinearLayout.VERTICAL);
                            cell.setGravity(Gravity.CENTER);

                            View swatch = new View(getContext);
                            swatch.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
                            GradientDrawable sw = new GradientDrawable();
                            sw.setShape(GradientDrawable.OVAL);
                            sw.setColor(color);
                            sw.setStroke(dp(2), withAlpha(0xFFFFFF, 0xCC));
                            swatch.setBackground(sw);
                            addPressAnim(swatch);

                            TextView label = new TextView(getContext);
                            label.setText(cname);
                            label.setTextColor(COLOR_TEXT_MUTED);
                            label.setTypeface(fontRegular);
                            label.setTextSize(9f);
                            label.setGravity(Gravity.CENTER);
                            label.setMaxLines(1);
                            label.setEllipsize(TextUtils.TruncateAt.END);
                            LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                            lblLp.setMargins(0, dp(4), 0, 0);
                            label.setLayoutParams(lblLp);

                            cell.addView(swatch);
                            cell.addView(label);

                            swatch.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v2) {
                                        applyColorButton(button, featName, color);
                                        Preferences.changeFeatureInt(featName, featNum, color);
                                        Toast.makeText(getContext, "Color: " + cname, Toast.LENGTH_SHORT).show();
                                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                                    }
                                });

                            row.addView(cell);
                            added++;
                        }
                        for (int k = added; k < cols; k++) {
                            View empty = new View(getContext);
                            empty.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
                            row.addView(empty);
                        }
                        grid.addView(row);
                    }

                    scroll.addView(grid);
                    dialogRef[0] = showStyledDialog("Pick ESP Color", scroll, null, null, "Cancel", null);
                }
            });

        linLayout.addView(button);
    }

    private void applyColorButton(Button button, String featName, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(color);
        bg.setStroke(dp(2), withAlpha(0xFFFFFF, 0xB0));
        button.setBackground(bg);
        int fg = isLight(color) ? Color.BLACK : Color.WHITE;
        button.setTextColor(fg);
        button.setText(Html.fromHtml("<b>" + featName + "</b>  " + hex(color)));
    }

    private void featureList(String[] listFT, LinearLayout linearLayout) {
        int featNum, subFeat = 0;
        LinearLayout llBak = linearLayout;
        for (int i = 0; i < listFT.length; i++) {
            boolean switchedOn = false;
            String feature = listFT[i];
            if (feature.contains("_True")) { switchedOn = true; feature = feature.replaceFirst("_True", ""); }
            linearLayout = llBak;
            if (feature.contains("CollapseAdd_")) { linearLayout = mCollapse; feature = feature.replaceFirst("CollapseAdd_", ""); }
            String[] str = feature.split("_");
            if (TextUtils.isDigitsOnly(str[0]) || str[0].matches("-[0-9]*")) { featNum = Integer.parseInt(str[0]); feature = feature.replaceFirst(str[0] + "_", ""); subFeat++; } else { featNum = i - subFeat; }
            String[] strSplit = feature.split("_");
            switch (strSplit[0]) {
                case "Toggle": Switch(linearLayout, featNum, strSplit[1], switchedOn); break;
                case "SeekBar": SeekBar(linearLayout, featNum, strSplit[1], Integer.parseInt(strSplit[2]), Integer.parseInt(strSplit[3])); break;
                case "Button": Button(linearLayout, featNum, strSplit[1]); break;
                case "ButtonOnOff": ButtonOnOff(linearLayout, featNum, strSplit[1], switchedOn); break;
                case "Spinner": Spinner(linearLayout, featNum, strSplit[1], strSplit[2]); break;
                case "InputText": InputText(linearLayout, featNum, strSplit[1]); break;
                case "InputValue": if (strSplit.length == 3) InputNum(linearLayout, featNum, strSplit[2], Integer.parseInt(strSplit[1])); if (strSplit.length == 2) InputNum(linearLayout, featNum, strSplit[1], 0); break;
                case "InputLValue": if (strSplit.length == 3) InputLNum(linearLayout, featNum, strSplit[2], Long.parseLong(strSplit[1])); if (strSplit.length == 2) InputLNum(linearLayout, featNum, strSplit[1], 0); break;
                case "CheckBox": CheckBox(linearLayout, featNum, strSplit[1], switchedOn); break;
                case "RadioButton": RadioButton(linearLayout, featNum, strSplit[1], strSplit[2]); break;
                case "Collapse": Collapse(linearLayout, strSplit[1], switchedOn); subFeat++; break;
                case "ButtonLink": subFeat++; ButtonLink(linearLayout, strSplit[1], strSplit[2]); break;
                case "Category": subFeat++; Category(linearLayout, strSplit[1]);  break;
                case "RichTextView": subFeat++; TextView(linearLayout, strSplit[1]);  break;
                case "RichWebView": subFeat++; WebTextView(linearLayout, strSplit[1]); break;
                case "ColorPicker": subFeat++; ColorPicker(linearLayout, featNum, strSplit[1], strSplit.length > 2 ? strSplit[2] : "#00FF88"); break;
            }
        }
    }

    // ================================================================
    // Feature widgets
    // ================================================================
    private void Switch(LinearLayout linLayout, final int featNum, final String featName, boolean swiOn) {
        final LinearLayout row = new LinearLayout(getContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(rowLp(6, 3, 6, 3));
        final GradientDrawable rowBg = cardBg(COLOR_CARD, COLOR_CARD_BORDER, 12);
        row.setBackground(rowBg);

        TextView label = new TextView(getContext);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        label.setText(featName);
        label.setTextColor(TEXT_COLOR_2);
        label.setTypeface(fontMedium);
        label.setTextSize(12f);
        label.setPadding(dp(12), dp(10), dp(6), dp(10));

        final ToggleView toggle = new ToggleView(getContext, ToggleON, COLOR_ACCENT, COLOR_TRACK);
        boolean initial = Preferences.loadPrefBool(featName, featNum, swiOn);
        toggle.setChecked(initial, false);
        rowBg.setStroke(dp(1), initial ? withAlpha(ToggleON, 0x99) : COLOR_CARD_BORDER);

        toggle.setListener(new ToggleView.Listener() {
                @Override
                public void onChanged(boolean bool) {
                    animateStroke(rowBg,
                                  bool ? COLOR_CARD_BORDER : withAlpha(ToggleON, 0x99),
                                  bool ? withAlpha(ToggleON, 0x99) : COLOR_CARD_BORDER);
                    Preferences.changeFeatureBool(featName, featNum, bool);
                    switch (featNum) {
                        case -1:
                            Preferences.with(row.getContext()).writeBoolean(-1, bool);
                            if (!bool) Preferences.with(row.getContext()).clear();
                            break;
                        case -3:
                            Preferences.isExpanded = bool;
                            break;
                    }
                }
            });

        row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle.toggle();
                }
            });
        addPressAnim(row);

        row.addView(label);
        row.addView(toggle);
        linLayout.addView(row);
    }

    private void SeekBar(LinearLayout linLayout, final int featNum, final String featName, final int min, int max) {
        int loadedProg = Preferences.loadPrefInt(featName, featNum);
        int startVal = (loadedProg == 0) ? min : loadedProg;

        LinearLayout card = new LinearLayout(getContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(rowLp(6, 3, 6, 3));
        card.setBackground(cardBg(COLOR_CARD, COLOR_CARD_BORDER, 12));

        LinearLayout head = new LinearLayout(getContext);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(getContext);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        label.setText(featName);
        label.setTextColor(TEXT_COLOR_2);
        label.setTypeface(fontMedium);
        label.setTextSize(12f);
        label.setPadding(dp(12), dp(10), dp(6), dp(2));

        final TextView chip = new TextView(getContext);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        chipLp.setMargins(0, dp(8), dp(10), 0);
        chip.setLayoutParams(chipLp);
        chip.setText(String.valueOf(startVal));
        chip.setTextColor(Color.parseColor(NumberTxtColor));
        chip.setTypeface(fontBold);
        chip.setTextSize(11f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(2), dp(8), dp(2));
        chip.setBackground(cardBg(withAlpha(Color.parseColor(NumberTxtColor), 0x18), withAlpha(Color.parseColor(NumberTxtColor), 0x66), 8));

        head.addView(label);
        head.addView(chip);

        final SeekBar seekBar = new SeekBar(getContext);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        seekBar.setPadding(dp(16), dp(8), dp(16), dp(10));
        seekBar.setMax(max);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) seekBar.setMin(min);
        seekBar.setProgressDrawable(new SeekTrackDrawable(dpf(6f), COLOR_TRACK, COLOR_ACCENT, COLOR_SUCCESS));
        seekBar.setThumb(makeSeekThumb());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) seekBar.setSplitTrack(false);
        seekBar.setProgress(startVal);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onStartTrackingTouch(SeekBar s) { }
                @Override public void onStopTrackingTouch(SeekBar s)  { }
                @Override
                public void onProgressChanged(SeekBar s, int i, boolean z) {
                    int val = i < min ? min : i;
                    if (val != i) seekBar.setProgress(val);
                    Preferences.changeFeatureInt(featName, featNum, val);
                    chip.setText(String.valueOf(val));
                }
            });

        card.addView(head);
        card.addView(seekBar);
        linLayout.addView(card);
    }

    private Drawable makeSeekThumb() {
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setColor(withAlpha(COLOR_ACCENT, 0x44));
        glow.setSize(dp(22), dp(22));

        GradientDrawable core = new GradientDrawable();
        core.setShape(GradientDrawable.OVAL);
        core.setColor(0xFFFFFFFF);
        core.setStroke(dp(3), COLOR_ACCENT);
        core.setSize(dp(14), dp(14));

        LayerDrawable ld = new LayerDrawable(new Drawable[]{glow, core});
        ld.setLayerInset(1, dp(4), dp(4), dp(4), dp(4));
        return ld;
    }

    private void Button(LinearLayout linLayout, final int featNum, final String featName) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setText(Html.fromHtml(featName));
        button.setTypeface(fontBold);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));

        GradientDrawable btnBg = gradBg(BTN_GRAD_1, BTN_GRAD_2, 12);
        btnBg.setStroke(dp(1), 0x55FFFFFF);
        button.setBackground(btnBg);
        flatten(button);
        addPressAnim(button);

        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switch (featNum) {
                        case -6:
                            if (categoryNames.size() > 0 && selectTabByName(categoryNames.get(0))) {
                                return;
                            }
                            collapseMenu(ICON_ALPHA);
                            break;
                        case -100: stopChecking = true; break;
                    }
                    Preferences.changeFeatureInt(featName, featNum, 0);
                }
            });
        linLayout.addView(button);
    }

    private void ButtonLink(LinearLayout linLayout, final String featName, final String url) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setAllCaps(false);
        button.setTextColor(COLOR_ACCENT);
        button.setText(Html.fromHtml(featName + " &#8599;"));
        button.setTypeface(fontBold);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        button.setBackground(cardBg(withAlpha(COLOR_ACCENT, 0x14), withAlpha(COLOR_ACCENT, 0x88), 12));
        flatten(button);
        addPressAnim(button);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setData(Uri.parse(url));
                    getContext.startActivity(intent);
                }
            });
        linLayout.addView(button);
    }

    private void applyOnOffStyle(Button button, String name, boolean on) {
        if (on) {
            button.setBackground(cardBg(withAlpha(ToggleON, 0x1F), withAlpha(ToggleON, 0xCC), 12));
            button.setText(Html.fromHtml(name + "  <font color='" + hex(ToggleON) + "'><b>ON</b></font>"));
        } else {
            button.setBackground(cardBg(withAlpha(ToggleOFF, 0x14), withAlpha(ToggleOFF, 0x88), 12));
            button.setText(Html.fromHtml(name + "  <font color='" + hex(ToggleOFF) + "'><b>OFF</b></font>"));
        }
    }

    private void ButtonOnOff(LinearLayout linLayout, final int featNum, String featName, boolean switchedOn) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTextColor(TEXT_COLOR_2);
        button.setAllCaps(false);
        button.setTypeface(fontMedium);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        flatten(button);
        addPressAnim(button);

        final String finalfeatName = featName.replace("OnOff_", "");
        final boolean[] state = new boolean[]{ Preferences.loadPrefBool(featName, featNum, switchedOn) };
        applyOnOffStyle(button, finalfeatName, state[0]);

        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    state[0] = !state[0];
                    Preferences.changeFeatureBool(finalfeatName, featNum, state[0]);
                    applyOnOffStyle(button, finalfeatName, state[0]);
                }
            });
        linLayout.addView(button);
    }

    private void Spinner(LinearLayout linLayout, final int featNum, final String featName, final String list) {
        final Context ctx = getContext;
        final List<String> lists = new LinkedList<String>(Arrays.asList(list.split(",")));

        FrameLayout holder = new FrameLayout(ctx);
        holder.setLayoutParams(rowLp(6, 3, 6, 3));
        holder.setBackground(cardBg(COLOR_CARD, COLOR_CARD_BORDER, 12));

        final Spinner spinner = new Spinner(ctx, Spinner.MODE_DROPDOWN);
        spinner.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        spinner.setBackground(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            spinner.setPopupBackgroundDrawable(cardBg(COLOR_CARD_HI, withAlpha(COLOR_ACCENT, 0x88), 10));
        }

        ArrayAdapter<String> aa = new ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_dropdown_item, lists) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    tv.setTextColor(TEXT_COLOR_2);
                    tv.setTypeface(fontMedium);
                    tv.setTextSize(12f * currentScale);
                    tv.setMinHeight(0);
                    tv.setMinimumHeight(0);
                    tv.setPadding(dp(12), dp(10), dp(30), dp(10));
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    tv.setTextColor(TEXT_COLOR_2);
                    tv.setTypeface(fontMedium);
                    tv.setTextSize(12f * currentScale);
                    tv.setMinHeight(0);
                    tv.setMinimumHeight(0);
                    tv.setBackgroundColor(COLOR_CARD_HI);
                    tv.setPadding(dp(14), dp(11), dp(14), dp(11));
                }
                return v;
            }
        };
        spinnerAdapters.add(aa);
        spinner.setAdapter(aa);
        spinner.setSelection(Preferences.loadPrefInt(featName, featNum));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View sv, int pos, long id) {
                    Preferences.changeFeatureInt(spinner.getSelectedItem().toString(), featNum, pos);
                }
                @Override public void onNothingSelected(AdapterView<?> p) { }
            });

        // chevron sits above the spinner but lets touches fall through
        ImageView chevron = new ImageView(ctx);
        TabIcon chevIcon = new TabIcon(TabIcon.CHEVRON);
        chevIcon.setColor(COLOR_ACCENT);
        chevron.setImageDrawable(chevIcon);
        FrameLayout.LayoutParams chLp = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        chLp.setMargins(0, 0, dp(10), 0);
        chevron.setLayoutParams(chLp);

        holder.addView(spinner);
        holder.addView(chevron);
        linLayout.addView(holder);
    }

    private void InputNum(LinearLayout linLayout, final int featNum, final String featName, final int maxValue) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        int num = Preferences.loadPrefInt(featName, featNum);
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
        button.setAllCaps(false);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTypeface(fontMedium);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        button.setBackground(cardBg(COLOR_CARD, withAlpha(COLOR_ACCENT, 0x66), 12));
        button.setTextColor(TEXT_COLOR_2);
        flatten(button);
        addPressAnim(button);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final EditText editText = makeEditText();
                    if (maxValue != 0) editText.setHint("Max: " + maxValue);
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setKeyListener(DigitsKeyListener.getInstance("0123456789-"));
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
                    showInputDialog("Input number", editText, new Runnable() {
                            @Override
                            public void run() {
                                int num;
                                try {
                                    String inp = editText.getText().toString();
                                    num = Integer.parseInt(inp.isEmpty() ? "0" : inp);
                                    if (maxValue != 0 && num >= maxValue) num = maxValue;
                                } catch (NumberFormatException ex) { num = maxValue != 0 ? maxValue : Integer.MAX_VALUE; }
                                button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
                                Preferences.changeFeatureInt(featName, featNum, num);
                            }
                        });
                }
            });
        linLayout.addView(button);
    }

    private void InputLNum(LinearLayout linLayout, final int featNum, final String featName, final long maxValue) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        long num = Preferences.loadPrefLong(featName, featNum);
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
        button.setAllCaps(false);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTypeface(fontMedium);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        button.setBackground(cardBg(COLOR_CARD, withAlpha(COLOR_ACCENT, 0x66), 12));
        button.setTextColor(TEXT_COLOR_2);
        flatten(button);
        addPressAnim(button);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final EditText editText = makeEditText();
                    if (maxValue != 0) editText.setHint("Max: " + maxValue);
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setKeyListener(DigitsKeyListener.getInstance("0123456789-"));
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
                    showInputDialog("Input number", editText, new Runnable() {
                            @Override
                            public void run() {
                                long num;
                                try {
                                    String inp = editText.getText().toString();
                                    num = Long.parseLong(inp.isEmpty() ? "0" : inp);
                                    if (maxValue != 0 && num >= maxValue) num = maxValue;
                                } catch (NumberFormatException ex) { num = maxValue != 0 ? maxValue : Long.MAX_VALUE; }
                                button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
                                Preferences.changeFeatureLong(featName, featNum, num);
                            }
                        });
                }
            });
        linLayout.addView(button);
    }

    private void InputText(LinearLayout linLayout, final int featNum, final String featName) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        String string = Preferences.loadPrefString(featName, featNum);
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + string + "</font>"));
        button.setAllCaps(false);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTypeface(fontMedium);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        button.setBackground(cardBg(COLOR_CARD, withAlpha(COLOR_ACCENT, 0x66), 12));
        button.setTextColor(TEXT_COLOR_2);
        flatten(button);
        addPressAnim(button);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final EditText editText = makeEditText();
                    showInputDialog("Input text", editText, new Runnable() {
                            @Override
                            public void run() {
                                String str = editText.getText().toString();
                                button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + str + "</font>"));
                                Preferences.changeFeatureString(featName, featNum, str);
                            }
                        });
                }
            });
        linLayout.addView(button);
    }

    private void CheckBox(LinearLayout linLayout, final int featNum, final String featName, boolean switchedOn) {
        final CheckBox checkBox = new CheckBox(getContext);
        final GradientDrawable cbBg = cardBg(COLOR_CARD, COLOR_CARD_BORDER, 12);
        checkBox.setLayoutParams(rowLp(6, 3, 6, 3));
        checkBox.setBackground(cbBg);
        checkBox.setText(featName);
        checkBox.setTextColor(TEXT_COLOR_2);
        checkBox.setTypeface(fontMedium);
        checkBox.setTextSize(12f);
        checkBox.setPadding(dp(10), dp(9), dp(10), dp(9));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) checkBox.setButtonTintList(ColorStateList.valueOf(CheckBoxColor));
        boolean initial = Preferences.loadPrefBool(featName, featNum, switchedOn);
        checkBox.setChecked(initial);
        if (initial) cbBg.setStroke(dp(1), withAlpha(CheckBoxColor, 0x99));
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton bv, boolean isChecked) {
                    animateStroke(cbBg,
                                  isChecked ? COLOR_CARD_BORDER : withAlpha(CheckBoxColor, 0x99),
                                  isChecked ? withAlpha(CheckBoxColor, 0x99) : COLOR_CARD_BORDER);
                    Preferences.changeFeatureBool(featName, featNum, isChecked);
                }
            });
        linLayout.addView(checkBox);
    }

    private void RadioButton(LinearLayout linLayout, final int featNum, String featName, final String list) {
        final List<String> lists = new LinkedList<String>(Arrays.asList(list.split(",")));
        final TextView textView = new TextView(getContext);
        textView.setText(featName + ":");
        textView.setTextColor(TEXT_COLOR_2);
        textView.setTypeface(fontBold);
        textView.setTextSize(12f);
        textView.setPadding(0, dp(2), 0, dp(4));
        final RadioGroup radioGroup = new RadioGroup(getContext);
        radioGroup.setLayoutParams(rowLp(6, 3, 6, 3));
        radioGroup.setBackground(cardBg(COLOR_CARD, COLOR_CARD_BORDER, 12));
        radioGroup.setPadding(dp(12), dp(8), dp(12), dp(8));
        radioGroup.setOrientation(LinearLayout.VERTICAL);
        radioGroup.addView(textView);
        for (int i = 0; i < lists.size(); i++) {
            final RadioButton Radioo = new RadioButton(getContext);
            final String finalfeatName = featName, radioName = lists.get(i);
            View.OnClickListener rl = new View.OnClickListener() {
                public void onClick(View v) {
                    textView.setText(Html.fromHtml(finalfeatName + ": <font color='" + NumberTxtColor + "'>" + radioName));
                    Preferences.changeFeatureInt(finalfeatName, featNum, radioGroup.indexOfChild(Radioo));
                }
            };
            Radioo.setText(lists.get(i));
            Radioo.setTextColor(Color.parseColor("#C9D4EE"));
            Radioo.setTypeface(fontRegular);
            Radioo.setTextSize(12f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) Radioo.setButtonTintList(ColorStateList.valueOf(RadioColor));
            Radioo.setOnClickListener(rl);
            radioGroup.addView(Radioo);
        }
        int index = Preferences.loadPrefInt(featName, featNum);
        if (index > 0) {
            textView.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + lists.get(index - 1)));
            ((RadioButton) radioGroup.getChildAt(index)).setChecked(true);
        }
        linLayout.addView(radioGroup);
    }

    // Smooth height animation for collapsible sections
    private void animateSection(final View sub, boolean expand) {
        Object old = sub.getTag();
        if (old instanceof ValueAnimator) {
            ((ValueAnimator) old).removeAllListeners();
            ((ValueAnimator) old).cancel();
        }
        final ViewGroup.LayoutParams lp = sub.getLayoutParams();
        View parent = (View) sub.getParent();
        int from = sub.getVisibility() == View.VISIBLE ? sub.getHeight() : 0;
        int to = 0;
        if (expand) {
            int pw = parent != null ? parent.getWidth() : 0;
            if (pw <= 0) {
                sub.setVisibility(View.VISIBLE);
                lp.height = WRAP_CONTENT;
                sub.requestLayout();
                return;
            }
            sub.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            to = sub.getMeasuredHeight();
            lp.height = from;
            sub.setVisibility(View.VISIBLE);
            sub.requestLayout();
        }

        final boolean exp = expand;
        ValueAnimator va = ValueAnimator.ofInt(from, to);
        va.setDuration(230);
        va.setInterpolator(new DecelerateInterpolator(1.5f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    lp.height = (Integer) a.getAnimatedValue();
                    sub.requestLayout();
                }
            });
        va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    lp.height = WRAP_CONTENT;
                    if (!exp) sub.setVisibility(View.GONE);
                    sub.requestLayout();
                    sub.setTag(null);
                }
            });
        sub.setTag(va);
        va.start();
    }

    private void Collapse(LinearLayout linLayout, final String text, final boolean expanded) {
        LinearLayout.LayoutParams llp = rowLp(6, 5, 6, 3);
        LinearLayout collapse = new LinearLayout(getContext);
        collapse.setLayoutParams(llp);
        collapse.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout collapseSub = new LinearLayout(getContext);
        collapseSub.setPadding(0, dp(4), 0, dp(2));
        collapseSub.setOrientation(LinearLayout.VERTICAL);
        collapseSub.setVisibility(View.GONE);
        mCollapse = collapseSub;

        // header row: title + rotating chevron
        LinearLayout headRow = new LinearLayout(getContext);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable colBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                                      new int[]{CollapseColor, withAlpha(COLOR_ACCENT_2, 0x30)});
        colBg.setCornerRadius(dp(12));
        colBg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0x77));
        headRow.setBackground(colBg);

        final TextView textView = new TextView(getContext);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        textView.setText(text);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextColor(COLOR_ACCENT);
        textView.setTypeface(fontBold);
        textView.setTextSize(12f);
        textView.setPadding(dp(12), dp(12), dp(6), dp(12));

        final ImageView chevron = new ImageView(getContext);
        TabIcon chevIcon = new TabIcon(TabIcon.CHEVRON);
        chevIcon.setColor(COLOR_ACCENT);
        chevron.setImageDrawable(chevIcon);
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        chLp.setMargins(0, 0, dp(10), 0);
        chevron.setLayoutParams(chLp);

        if (expanded) {
            collapseSub.setVisibility(View.VISIBLE);
            chevron.setRotation(180f);
        }

        headRow.setOnClickListener(new View.OnClickListener() {
                boolean isChecked = expanded;
                @Override
                public void onClick(View v) {
                    isChecked = !isChecked;
                    chevron.animate().rotation(isChecked ? 180f : 0f).setDuration(230).setInterpolator(new DecelerateInterpolator()).start();
                    animateSection(collapseSub, isChecked);
                }
            });
        addPressAnim(headRow);

        headRow.addView(textView);
        headRow.addView(chevron);
        collapse.addView(headRow);
        collapse.addView(collapseSub);
        linLayout.addView(collapse);
    }

    private void Category(LinearLayout linLayout, String text) {
        TextView textView = new TextView(getContext);
        GradientDrawable catBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                                      new int[]{withAlpha(COLOR_ACCENT, 0x00), withAlpha(COLOR_ACCENT, 0x2A), withAlpha(COLOR_ACCENT_2, 0x00)});
        textView.setBackground(catBg);
        textView.setText(Html.fromHtml(text));
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(COLOR_ACCENT);
        textView.setTypeface(fontBold);
        textView.setTextSize(12f);
        textView.setPadding(0, dp(7), 0, dp(7));
        linLayout.addView(textView);
    }

    private void TextView(LinearLayout linLayout, String text) {
        TextView textView = new TextView(getContext);
        textView.setText(Html.fromHtml(text));
        textView.setTextColor(Color.parseColor("#C9D4EE"));
        textView.setTypeface(fontRegular);
        textView.setTextSize(12f);
        textView.setPadding(dp(12), dp(5), dp(12), dp(5));
        linLayout.addView(textView);
    }

    private void WebTextView(LinearLayout linLayout, String text) {
        WebView wView = new WebView(getContext);
        wView.loadData(text, "text/html", "utf-8");
        wView.setBackgroundColor(0x00000000);
        wView.setPadding(0, dp(4), 0, dp(4));
        wView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        linLayout.addView(wView);
    }
}