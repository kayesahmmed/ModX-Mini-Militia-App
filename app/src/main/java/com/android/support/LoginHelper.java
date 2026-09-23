package com.android.support;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import java.util.Iterator;

public class LoginHelper {

    public interface Callback {
        void onLoginSuccess();
    }

    private static final String TAG = "LoginHelper";

    // Premium Modern Dark Theme Colors
    private static final int COLOR_BG_TOP       = Color.parseColor("#0F172A"); // Slate 900
    private static final int COLOR_BG_BOTTOM    = Color.parseColor("#020617"); // Slate 950
    private static final int COLOR_CARD         = Color.parseColor("#1E293B"); // Slate 800
    private static final int COLOR_ACCENT       = Color.parseColor("#3B82F6"); // Blue 500
    private static final int COLOR_ACCENT_LIGHT = Color.parseColor("#60A5FA"); // Blue 400
    private static final int COLOR_SUCCESS      = Color.parseColor("#10B981"); // Emerald 500
    private static final int COLOR_DANGER       = Color.parseColor("#EF4444"); // Red 500
    private static final int COLOR_TEXT         = Color.parseColor("#F8FAFC"); // Slate 50
    private static final int COLOR_MUTED        = Color.parseColor("#94A3B8"); // Slate 400
    private static final int COLOR_BORDER       = Color.parseColor("#334155"); // Slate 700
    private static final int COLOR_INPUT_BG     = Color.parseColor("#0B1120"); // Deep Input Dark

    private final Context ctx;
    private final Callback callback;
    private final SharedPreferences save;
    private final SharedPreferences KEY;

    private EditText editUser, editPass;
    private CheckBox rememberCb, showCb;
    private Button loginBtn;
    private TextView statusTxt;
    private boolean loginInProgress = false;
    private boolean keyExpiredDialogShowing = false;

    public LoginHelper(Context context, Callback cb) {
        this.ctx = context;
        this.callback = cb;
        this.save = context.getSharedPreferences("save", Context.MODE_PRIVATE);
        this.KEY = context.getSharedPreferences("KEY", Context.MODE_PRIVATE);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    // ================================================================
    //  Modern & Compact login view — fits inside 272×352 menu
    // ================================================================
    public View buildView() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- Premium Card Setup ----
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_CARD);
        cardBg.setCornerRadius(dp(16)); // More rounded for modern look
        cardBg.setStroke(dp(1), COLOR_BORDER);
        card.setBackground(cardBg);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(8)); // Adds realistic drop shadow
        }
        root.addView(card);

        // ---- Header Title (Subtle & Professional) ----
        TextView titleTxt = new TextView(ctx);
        titleTxt.setText("Sign In");
        titleTxt.setTextColor(COLOR_TEXT);
        titleTxt.setTextSize(16f);
        titleTxt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTxt.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.setMargins(0, 0, 0, dp(12));
        card.addView(titleTxt, tLp);

        // ---- Username Field ----
        TextView l1 = new TextView(ctx);
        l1.setText("USERNAME");
        l1.setTextColor(COLOR_MUTED);
        l1.setTextSize(9f);
        l1.setLetterSpacing(0.05f);
        l1.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(l1);

        editUser = makeInput(false);
        editUser.setHint("Enter your username");
        LinearLayout.LayoutParams e1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)); // Slightly taller for better UX
        e1.setMargins(0, dp(4), 0, dp(10));
        editUser.setLayoutParams(e1);
        card.addView(editUser);

        // ---- Password Field ----
        TextView l2 = new TextView(ctx);
        l2.setText("PASSWORD");
        l2.setTextColor(COLOR_MUTED);
        l2.setTextSize(9f);
        l2.setLetterSpacing(0.05f);
        l2.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(l2);

        editPass = makeInput(true);
        editPass.setHint("Enter your password");
        LinearLayout.LayoutParams e2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        e2.setMargins(0, dp(4), 0, dp(6));
        editPass.setLayoutParams(e2);
        card.addView(editPass);

        attachKeyboardFix(editUser);
        attachKeyboardFix(editPass);

        // ---- Checkbox row ----
        LinearLayout cbRow = new LinearLayout(ctx);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbRow.setGravity(Gravity.CENTER_VERTICAL);
        cbRow.setPadding(dp(2), dp(4), dp(2), dp(4));

        rememberCb = new CheckBox(ctx);
        rememberCb.setText("Remember me");
        rememberCb.setTextColor(COLOR_MUTED);
        rememberCb.setTextSize(10f);
        rememberCb.setPadding(dp(4), 0, 0, 0);
        rememberCb.setMinHeight(0);
        rememberCb.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            rememberCb.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        rememberCb.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        showCb = new CheckBox(ctx);
        showCb.setText("Show");
        showCb.setTextColor(COLOR_MUTED);
        showCb.setTextSize(10f);
        showCb.setPadding(dp(4), 0, 0, 0);
        showCb.setMinHeight(0);
        showCb.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            showCb.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));

        cbRow.addView(rememberCb);
        cbRow.addView(showCb);
        card.addView(cbRow);

        // ---- Login button ----
        loginBtn = new Button(ctx);
        loginBtn.setText("Login Now");
        loginBtn.setAllCaps(false);
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setTextSize(13f);
        loginBtn.setLetterSpacing(0.02f);
        loginBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        
        // Premium Button Gradient
        GradientDrawable lb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF2563EB, 0xFF3B82F6}); // Deep Blue to Light Blue
        lb.setCornerRadius(dp(10));
        loginBtn.setBackground(lb);
        loginBtn.setMinHeight(0);
        loginBtn.setMinimumHeight(0);
        loginBtn.setMinWidth(0);
        loginBtn.setMinimumWidth(0);
        loginBtn.setPadding(dp(8), 0, dp(8), 0);
        
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        bLp.setMargins(0, dp(12), 0, 0);
        loginBtn.setLayoutParams(bLp);
        card.addView(loginBtn);

        // ---- Button Touch Animation (Bounce Effect) ----
        loginBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                                .setInterpolator(new OvershootInterpolator()).start();
                        break;
                }
                return false;
            }
        });

        // ---- Status ----
        statusTxt = new TextView(ctx);
        statusTxt.setText("");
        statusTxt.setTextColor(COLOR_MUTED);
        statusTxt.setTextSize(10f);
        statusTxt.setGravity(Gravity.CENTER);
        statusTxt.setSingleLine(true);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, WRAP_CONTENT);
        sLp.setMargins(0, dp(8), 0, 0);
        statusTxt.setLayoutParams(sLp);
        card.addView(statusTxt);

        // ---- UI Entry Animation ----
        card.setAlpha(0f);
        card.setTranslationY(dp(15));
        card.animate().alpha(1f).translationY(0).setDuration(400)
                .setInterpolator(new DecelerateInterpolator()).start();

        // ---- Listeners ----
        showCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
                if (isChecked) {
                    editPass.setTransformationMethod(android.text.method.HideReturnsTransformationMethod.getInstance());
                    showCb.setTextColor(COLOR_TEXT);
                } else {
                    editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    showCb.setTextColor(COLOR_MUTED);
                }
                editPass.setSelection(editPass.getText().length());
            }
        });

        rememberCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
                if (isChecked) {
                    rememberCb.setTextColor(COLOR_TEXT);
                    save.edit().putString("edittext1", editUser.getText().toString()).apply();
                    save.edit().putString("edittext2", editPass.getText().toString()).apply();
                } else {
                    rememberCb.setTextColor(COLOR_MUTED);
                    save.edit().remove("edittext1").apply();
                    save.edit().remove("edittext2").apply();
                }
            }
        });

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(editUser);
                hideKeyboard(editPass);
                performLogin();
            }
        });

        // Restore saved
        String u = save.getString("edittext1", "");
        String p = save.getString("edittext2", "");
        if (!u.isEmpty() && !p.isEmpty()) {
            editUser.setText(u);
            editPass.setText(p);
            rememberCb.setChecked(true);
        }

        // Update check (silent)
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() { checkUpdateAsync(); }
        }, 300);

        return root;
    }

    // ================================================================
    private EditText makeInput(boolean isPassword) {
        final EditText e = new EditText(ctx);
        e.setHintTextColor(Color.parseColor("#475569")); // Soft Hint Color
        e.setTextColor(COLOR_TEXT);
        e.setTextSize(12f);
        e.setSingleLine(true);
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        e.setClickable(true);
        e.setCursorVisible(true);
        if (isPassword) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            e.setTransformationMethod(PasswordTransformationMethod.getInstance());
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        e.setPadding(dp(12), 0, dp(12), 0);
        
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_INPUT_BG);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), COLOR_BORDER);
        e.setBackground(bg);

        // Smooth Focus Animation (Border Highlight)
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    bg.setStroke(dp(1), COLOR_ACCENT_LIGHT);
                } else {
                    bg.setStroke(dp(1), COLOR_BORDER);
                }
            }
        });
        
        return e;
    }

    // ================================================================
    //  Keyboard handling
    // ================================================================
    private void attachKeyboardFix(final EditText et) {
        et.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.requestFocus();
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() { forceShowKeyboard(et); }
                    }, 80);
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() { forceShowKeyboard(et); }
                    }, 300);
                }
                return false;
            }
        });

        et.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.postDelayed(new Runnable() {
                    @Override
                    public void run() { forceShowKeyboard(et); }
                }, 80);
            }
        });

        View.OnFocusChangeListener existingListener = et.getOnFocusChangeListener();
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (existingListener != null) {
                    existingListener.onFocusChange(v, hasFocus);
                }
                if (hasFocus) {
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() { forceShowKeyboard(et); }
                    }, 80);
                }
            }
        });
    }

    private void forceShowKeyboard(final EditText et) {
        if (et == null) return;
        try {
            applySoftInputModeForOrientation(et);

            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return;

            et.requestFocus();
            et.setSelection(et.getText().length());

            boolean shown = imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
            if (!shown) {
                imm.showSoftInput(et, InputMethodManager.SHOW_FORCED);
            }
        } catch (Exception e) {
            Log.e(TAG, "forceShowKeyboard: " + e);
        }
    }

    private void applySoftInputModeForOrientation(EditText et) {
        try {
            android.view.WindowManager wm = (android.view.WindowManager)
                    ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;

            int orientation = ctx.getResources().getConfiguration().orientation;
            View rootView = et.getRootView();
            if (rootView == null) return;

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Landscape
            } else {
                // Portrait
            }
        } catch (Exception ignored) { }
    }

    private void hideKeyboard(EditText et) {
        try {
            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && et != null) imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    // Animated Status Text
    private void setStatus(final String msg, final int color) {
        if (statusTxt == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                statusTxt.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        statusTxt.setText(msg);
                        statusTxt.setTextColor(color);
                        statusTxt.animate().alpha(1f).setDuration(150).start();
                    }
                }).start();
            }
        });
    }

    private String getVersionName() {
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) { return ""; }
    }

    // ================================================================
    //  Update check — REST API in background
    // ================================================================
    private void checkUpdateAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject json = ModFirebase.fetchJson("update");
                if (json == null) return;
                try {
                    JSONObject up = json.optJSONObject("up");
                    if (up == null) return;
                    String version = up.optString("version", "");
                    String message = up.optString("message", "");
                    if (TextUtils.isEmpty(version)) return;

                    final String currentVer = getVersionName();
                    if (!currentVer.equals(version)) {
                        final String fVersion = version;
                        final String fMessage = message;
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateDialog(fVersion, fMessage);
                            }
                        });
                    }
                } catch (Exception ignored) { }
            }
        }).start();
    }

    private void showUpdateDialog(String version, String msg) {
        if (!(ctx instanceof android.app.Activity)) return;
        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), COLOR_ACCENT);
        box.setBackground(bg);

        TextView t = new TextView(ctx);
        t.setText("🚀 Update Available");
        t.setTextColor(COLOR_ACCENT);
        t.setTextSize(16);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        box.addView(t);

        TextView body = new TextView(ctx);
        String txt = "Current Version : " + getVersionName() + "\nLatest Version  : " + version;
        if (msg != null && !msg.isEmpty()) txt += "\n\n" + msg;
        body.setText(txt);
        body.setTextColor(COLOR_TEXT);
        body.setLineSpacing(dp(2), 1.1f);
        body.setTextSize(13);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.setMargins(0, dp(12), 0, 0);
        box.addView(body, bLp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.setMargins(0, dp(20), 0, 0);
        box.addView(row, rLp);

        Button exitBtn = new Button(ctx);
        exitBtn.setText("Exit");
        exitBtn.setAllCaps(false);
        exitBtn.setTextColor(COLOR_MUTED);
        exitBtn.setTextSize(13);
        GradientDrawable ebg = new GradientDrawable();
        ebg.setColor(Color.parseColor("#1AFFFFFF"));
        ebg.setCornerRadius(dp(10));
        exitBtn.setBackground(ebg);
        exitBtn.setPadding(dp(16), dp(8), dp(16), dp(8));

        Button updateBtn = new Button(ctx);
        updateBtn.setText("Update Now");
        updateBtn.setAllCaps(false);
        updateBtn.setTextColor(Color.WHITE);
        updateBtn.setTextSize(13);
        updateBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        GradientDrawable ubg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF2563EB, 0xFF3B82F6});
        ubg.setCornerRadius(dp(10));
        updateBtn.setBackground(ubg);
        updateBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uLp.setMargins(dp(12), 0, 0, 0);
        updateBtn.setLayoutParams(uLp);

        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
                ((android.app.Activity) ctx).finishAffinity();
            }
        });
        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/kayesahmmedpro"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception e) { }
            }
        });

        row.addView(exitBtn);
        row.addView(updateBtn);

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(ctx);
        b.setView(box);
        android.app.AlertDialog d = b.create();
        d.setCanceledOnTouchOutside(false);
        d.setCancelable(false);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            if (Build.VERSION.SDK_INT >= 26) d.getWindow().setType(2038);
            else d.getWindow().setType(2002);
            d.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
        }
        ref[0] = d;
        d.show();
    }

    // ================================================================
    //  Login — REST API background thread
    // ================================================================
    private void performLogin() {
        if (loginInProgress) return;

        final String inputUser = editUser.getText().toString().trim();
        final String inputPass = editPass.getText().toString().trim();

        if (TextUtils.isEmpty(inputUser) || TextUtils.isEmpty(inputPass)) {
            setStatus("⚠ Please fill all fields", COLOR_DANGER);
            return;
        }

        loginInProgress = true;
        loginBtn.setEnabled(false);
        loginBtn.setText("Verifying...");
        setStatus("⏳ Checking credentials...", COLOR_ACCENT_LIGHT);

        save.edit().putString("edittext1", inputUser).apply();
        save.edit().putString("edittext2", inputPass).apply();

        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject users = ModFirebase.fetchJson("User");

                if (users == null) {
                    loginInProgress = false;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            loginBtn.setEnabled(true);
                            loginBtn.setText("Login Now");
                            setStatus("⚠ Connection error", COLOR_DANGER);
                        }
                    });
                    return;
                }

                JSONObject matched = null;
                try {
                    Iterator<String> keys = users.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        JSONObject u = users.optJSONObject(k);
                        if (u == null) continue;
                        String user = u.optString("user", "");
                        String pass = u.optString("pass", "");
                        if (inputUser.equals(user) && inputPass.equals(pass)) {
                            matched = u;
                            break;
                        }
                    }
                } catch (Exception ignored) { }

                if (matched == null) {
                    loginInProgress = false;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            loginBtn.setEnabled(true);
                            loginBtn.setText("Login Now");
                            setStatus("❌ Invalid username or password", COLOR_DANGER);
                        }
                    });
                    return;
                }

                String status = matched.optString("status", "false");
                long time = 0;
                try { time = (long) matched.optDouble("time", 0); } catch (Exception ignored) { }
                long now = System.currentTimeMillis();
                boolean expired = (time > 0 && now > time);

                if (!status.equals("true") || expired) {
                    loginInProgress = false;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            loginBtn.setEnabled(true);
                            loginBtn.setText("Login Now");
                            setStatus("⚠ Key expired or blocked", COLOR_DANGER);
                            showKeyExpiredDialog();
                        }
                    });
                    return;
                }

                try {
                    KEY.edit().putString("User",     matched.optString("user", "")).apply();
                    KEY.edit().putString("Status",   matched.optString("status", "")).apply();
                    KEY.edit().putString("Register", matched.optString("rgtime", "")).apply();
                    KEY.edit().putString("time",     matched.optString("time", "")).apply();
                    KEY.edit().putString("Valid",    matched.optString("Validity", "")).apply();
                    KEY.edit().putString("key",      matched.optString("key", "")).apply();
                } catch (Exception ignored) { }

                loginInProgress = false;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("Success!");
                        
                        GradientDrawable successBg = new GradientDrawable();
                        successBg.setColor(COLOR_SUCCESS);
                        successBg.setCornerRadius(dp(10));
                        loginBtn.setBackground(successBg);
                        
                        setStatus("✅ Login successful", COLOR_SUCCESS);
                        Toast.makeText(ctx, "Login Success", Toast.LENGTH_SHORT).show();

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) callback.onLoginSuccess();
                            }
                        }, 700);
                    }
                });
            }
        }).start();
    }

    // ================================================================
    private void showKeyExpiredDialog() {
        if (keyExpiredDialogShowing) return;
        keyExpiredDialogShowing = true;
        if (!(ctx instanceof android.app.Activity)) return;

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), COLOR_DANGER);
        box.setBackground(bg);

        TextView t = new TextView(ctx);
        t.setText("🔒 Key Expired");
        t.setTextColor(COLOR_DANGER);
        t.setTextSize(16);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        box.addView(t);

        TextView body = new TextView(ctx);
        body.setText("Your key has expired or account has been blocked.\n\n" +
                "Please contact the seller to renew your subscription.");
        body.setTextColor(COLOR_TEXT);
        body.setLineSpacing(dp(2), 1.1f);
        body.setTextSize(13);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.setMargins(0, dp(12), 0, 0);
        box.addView(body, bLp);

        Button contact = new Button(ctx);
        contact.setText("Contact Support");
        contact.setAllCaps(false);
        contact.setTextColor(Color.WHITE);
        contact.setTextSize(14);
        contact.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        GradientDrawable cbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF059669, 0xFF10B981}); // Premium Emerald Gradient
        cbg.setCornerRadius(dp(12));
        contact.setBackground(cbg);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        cLp.setMargins(0, dp(20), 0, 0);
        contact.setLayoutParams(cLp);
        box.addView(contact);

        contact.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                                .setInterpolator(new OvershootInterpolator()).start();
                        break;
                }
                return false;
            }
        });

        contact.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                keyExpiredDialogShowing = false;
                if (ref[0] != null) ref[0].dismiss();
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/kayesahmmedpro"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception e) { }
            }
        });

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(ctx);
        b.setView(box);
        android.app.AlertDialog d = b.create();
        d.setCanceledOnTouchOutside(false);
        d.setCancelable(true);
        d.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(android.content.DialogInterface di) {
                keyExpiredDialogShowing = false;
                ((android.app.Activity) ctx).finishAffinity();
            }
        });
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            if (Build.VERSION.SDK_INT >= 26) d.getWindow().setType(2038);
            else d.getWindow().setType(2002);
            d.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
        }
        ref[0] = d;
        d.show();
    }

    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
}
