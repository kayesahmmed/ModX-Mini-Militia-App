package com.android.support;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

    private static final int COLOR_BG_1       = Color.parseColor("#0B1224");
    private static final int COLOR_BG_2       = Color.parseColor("#050810");
    private static final int COLOR_ACCENT     = Color.parseColor("#00E5FF");
    private static final int COLOR_ACCENT_2   = Color.parseColor("#7C4DFF");
    private static final int COLOR_SUCCESS    = Color.parseColor("#00F5A0");
    private static final int COLOR_DANGER     = Color.parseColor("#FF4D6D");
    private static final int COLOR_WARN       = Color.parseColor("#FFB300");
    private static final int COLOR_TEXT       = Color.parseColor("#ECF2FF");
    private static final int COLOR_MUTED      = Color.parseColor("#6F7FA8");
    private static final int COLOR_FIELD_BG   = Color.parseColor("#0D1526");
    private static final int COLOR_FIELD_BORD = Color.parseColor("#1C2742");
    private static final int COLOR_INPUT_HINT = Color.parseColor("#3E4A6B");
    private static final int COLOR_CHECKED_BG = Color.parseColor("#0A1A2E");

    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;

    private final Context ctx;
    private final Callback callback;
    private final SharedPreferences save;
    private final SharedPreferences KEY;

    private EditText editUser, editPass;
    private LinearLayout userBox, passBox;
    private CustomCheck rememberCb, showCb;
    private Button loginBtn;
    private TextView statusTxt;
    private boolean loginInProgress = false;
    private boolean keyExpiredDialogShowing = false;

    private Typeface tfRegular, tfMedium, tfBold;

    public LoginHelper(Context context, Callback cb) {
        this.ctx = context;
        this.callback = cb;
        this.save = context.getSharedPreferences("save", Context.MODE_PRIVATE);
        this.KEY = context.getSharedPreferences("KEY", Context.MODE_PRIVATE);
        this.tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
        this.tfMedium  = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        this.tfBold    = Typeface.create("sans-serif", Typeface.BOLD);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    public View buildView() {
        FrameLayout wrapper = new FrameLayout(ctx);
        wrapper.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(4), dp(6), dp(4), dp(4));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable cardBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_1, COLOR_BG_2});
        cardBg.setCornerRadius(dp(14));
        cardBg.setStroke(dp(1), 0x33FFFFFF);
        card.setBackground(cardBg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(6));
        }
        root.addView(card);

        TextView userLabel = makeFieldLabel("USERNAME");
        card.addView(userLabel);

        userBox = makeFieldContainer();
        ImageView userIcon = new ImageView(ctx);
        userIcon.setImageDrawable(new FieldIcon(FieldIcon.USER, COLOR_MUTED));
        userIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams uiLp = new LinearLayout.LayoutParams(dp(16), dp(16));
        uiLp.setMargins(dp(10), 0, 0, 0);
        userIcon.setLayoutParams(uiLp);
        userBox.addView(userIcon);

        editUser = makeInput();
        editUser.setHint("Enter username");
        editUser.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        editUser.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams ueLp = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f);
        editUser.setLayoutParams(ueLp);
        userBox.addView(editUser);

        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(34));
        uLp.setMargins(0, dp(3), 0, dp(10));
        userBox.setLayoutParams(uLp);
        card.addView(userBox);

        TextView passLabel = makeFieldLabel("PASSWORD");
        card.addView(passLabel);

        passBox = makeFieldContainer();
        ImageView passIcon = new ImageView(ctx);
        passIcon.setImageDrawable(new FieldIcon(FieldIcon.LOCK, COLOR_MUTED));
        passIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams piLp = new LinearLayout.LayoutParams(dp(16), dp(16));
        piLp.setMargins(dp(10), 0, 0, 0);
        passIcon.setLayoutParams(piLp);
        passBox.addView(passIcon);

        editPass = makeInput();
        editPass.setHint("Enter password");
        editPass.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
        LinearLayout.LayoutParams peLp = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f);
        editPass.setLayoutParams(peLp);
        passBox.addView(editPass);

        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(34));
        pLp.setMargins(0, dp(3), 0, dp(8));
        passBox.setLayoutParams(pLp);
        card.addView(passBox);

        attachFieldFocus(userBox, editUser, (ImageView) userBox.getChildAt(0));
        attachFieldFocus(passBox, editPass, (ImageView) passBox.getChildAt(0));

        editUser.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT
                 || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    editPass.requestFocus();
                    editPass.setSelection(editPass.getText().length());
                    forceShowKeyboard(editPass);
                    return true;
                }
                return false;
            }
        });

        editPass.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE
                 || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    hideKeyboard(editPass);
                    performLogin();
                    return true;
                }
                return false;
            }
        });

        LinearLayout cbRow = new LinearLayout(ctx);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbRow.setGravity(Gravity.CENTER_VERTICAL);
        cbRow.setPadding(0, dp(2), 0, dp(2));

        rememberCb = new CustomCheck("Remember", false);
        rememberCb.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        showCb = new CustomCheck("Show", false);
        showCb.setListener(new CustomCheck.Listener() {
            @Override
            public void onChanged(boolean checked) {
                int sel = editPass.getSelectionStart();
                if (checked) editPass.setTransformationMethod(
                        android.text.method.HideReturnsTransformationMethod.getInstance());
                else editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
                if (sel >= 0 && sel <= editPass.getText().length()) {
                    editPass.setSelection(sel);
                }
            }
        });

        rememberCb.setListener(new CustomCheck.Listener() {
            @Override
            public void onChanged(boolean checked) {
                if (checked) {
                    save.edit().putString("edittext1", editUser.getText().toString()).apply();
                    save.edit().putString("edittext2", editPass.getText().toString()).apply();
                } else {
                    save.edit().remove("edittext1").apply();
                    save.edit().remove("edittext2").apply();
                }
            }
        });

        cbRow.addView(rememberCb);
        cbRow.addView(showCb);
        card.addView(cbRow);

        loginBtn = new Button(ctx);
        loginBtn.setText("SIGN IN");
        loginBtn.setAllCaps(false);
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setTextSize(12.5f);
        loginBtn.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            loginBtn.setLetterSpacing(0.12f);
        }

        GradientDrawable lb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0093C4, 0xFF6A47F5});
        lb.setCornerRadius(dp(10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(0x55FFFFFF), lb, null);
            loginBtn.setBackground(ripple);
            loginBtn.setElevation(dp(3));
        } else {
            loginBtn.setBackground(lb);
        }
        loginBtn.setMinHeight(0);
        loginBtn.setMinimumHeight(0);
        loginBtn.setMinWidth(0);
        loginBtn.setMinimumWidth(0);
        loginBtn.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(38));
        bLp.setMargins(0, dp(10), 0, 0);
        loginBtn.setLayoutParams(bLp);
        card.addView(loginBtn);

        statusTxt = new TextView(ctx);
        statusTxt.setText("");
        statusTxt.setTextColor(COLOR_MUTED);
        statusTxt.setTextSize(10f);
        statusTxt.setTypeface(tfMedium);
        statusTxt.setGravity(Gravity.CENTER);
        statusTxt.setSingleLine(true);
        statusTxt.setAlpha(0f);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        sLp.setMargins(0, dp(6), 0, 0);
        statusTxt.setLayoutParams(sLp);
        card.addView(statusTxt);

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                loginBtn.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(220)
                                        .setInterpolator(new OvershootInterpolator(2.4f))
                                        .start();
                            }
                        }).start();
                hideKeyboard(editUser);
                hideKeyboard(editPass);
                performLogin();
            }
        });

        String u = save.getString("edittext1", "");
        String p = save.getString("edittext2", "");
        if (!u.isEmpty() && !p.isEmpty()) {
            editUser.setText(u);
            editPass.setText(p);
            rememberCb.setChecked(true);
        }

        wrapper.addView(root, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return wrapper;
    }

    private TextView makeFieldLabel(String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(COLOR_MUTED);
        tv.setTextSize(8f);
        tv.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tv.setLetterSpacing(0.18f);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        lp.setMargins(dp(2), 0, 0, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout makeFieldContainer() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_FIELD_BG);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), COLOR_FIELD_BORD);
        box.setBackground(bg);
        box.setClipToPadding(false);
        return box;
    }

    private EditText makeInput() {
        EditText e = new EditText(ctx);
        e.setHintTextColor(COLOR_INPUT_HINT);
        e.setTextColor(COLOR_TEXT);
        e.setTextSize(12f);
        e.setTypeface(tfMedium);
        e.setSingleLine(true);
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        e.setClickable(true);
        e.setCursorVisible(true);
        e.setBackground(null);
        e.setIncludeFontPadding(false);
        e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        e.setPadding(dp(8), 0, dp(10), 0);
        return e;
    }

    private void attachFieldFocus(final LinearLayout box, final EditText et, final ImageView icon) {
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                final GradientDrawable bg = (GradientDrawable) box.getBackground();
                int fromColor = hasFocus ? COLOR_FIELD_BORD : COLOR_ACCENT;
                int toColor   = hasFocus ? COLOR_ACCENT      : COLOR_FIELD_BORD;

                ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
                va.setDuration(240);
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        bg.setStroke(dp(hasFocus ? 1.4f : 1f), (Integer) a.getAnimatedValue());
                    }
                });
                va.start();

                FieldIcon fi = (FieldIcon) icon.getDrawable();
                if (fi != null) {
                    fi.animateColor(hasFocus ? COLOR_ACCENT : COLOR_MUTED, 240);
                }

                if (hasFocus) {
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() { forceShowKeyboard(et); }
                    }, 80);
                }
            }
        });

        et.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.requestFocus();
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() { forceShowKeyboard(et); }
                    }, 80);
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
    }

    private void forceShowKeyboard(final EditText et) {
        if (et == null) return;
        try {
            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return;
            et.requestFocus();
            if (et.getText().length() > 0) {
                et.setSelection(et.getText().length());
            }
            boolean shown = imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
            if (!shown) imm.showSoftInput(et, InputMethodManager.SHOW_FORCED);
        } catch (Exception e) {
            Log.e(TAG, "forceShowKeyboard: " + e);
        }
    }

    private void hideKeyboard(EditText et) {
        try {
            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && et != null) {
                imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
            }
        } catch (Exception ignored) { }
    }

    private void setStatus(final String msg, final int color) {
        if (statusTxt == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                statusTxt.animate().cancel();
                statusTxt.animate().alpha(0f).setDuration(120)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                statusTxt.setText(msg);
                                statusTxt.setTextColor(color);
                                statusTxt.animate().alpha(1f).setDuration(220).start();
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

    private void performLogin() {
        if (loginInProgress) return;

        final String inputUser = editUser.getText().toString().trim();
        final String inputPass = editPass.getText().toString().trim();

        if (TextUtils.isEmpty(inputUser) || TextUtils.isEmpty(inputPass)) {
            setStatus("Please fill in all fields", COLOR_WARN);
            return;
        }

        loginInProgress = true;
        loginBtn.setEnabled(false);
        loginBtn.setText("SIGNING IN...");
        setStatus("Verifying credentials...", COLOR_ACCENT);

        save.edit().putString("edittext1", inputUser).apply();
        save.edit().putString("edittext2", inputPass).apply();

        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject users = ModFirebase.fetchJson("User");

                if (users == null) {
                    loginInProgress = false;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() {
                            loginBtn.setEnabled(true);
                            loginBtn.setText("SIGN IN");
                            setStatus("Connection failed", COLOR_DANGER);
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
                        @Override public void run() {
                            loginBtn.setEnabled(true);
                            loginBtn.setText("SIGN IN");
                            setStatus("Invalid username or password", COLOR_DANGER);
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
                        @Override public void run() {
                            loginBtn.setEnabled(true);
                            loginBtn.setText("SIGN IN");
                            setStatus("Key expired or blocked", COLOR_DANGER);
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
                    @Override public void run() {
                        loginBtn.setEnabled(true);
                        loginBtn.setText("SIGN IN");
                        setStatus("Welcome back!", COLOR_SUCCESS);
                        Toast.makeText(ctx, "Login Success", Toast.LENGTH_SHORT).show();
                        checkUpdateAfterLogin();
                    }
                });
            }
        }).start();
    }

    private void checkUpdateAfterLogin() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject updateJson = ModFirebase.fetchJson("update");
                if (updateJson == null) {
                    proceedToMenu();
                    return;
                }
                try {
                    JSONObject up = updateJson.optJSONObject("up");
                    if (up == null) {
                        proceedToMenu();
                        return;
                    }
                    String latestVersion = up.optString("version", "");
                    String message = up.optString("message", "");
                    String currentVersion = getVersionName();

                    if (!TextUtils.isEmpty(latestVersion) && !currentVersion.equals(latestVersion)) {
                        final String fV = latestVersion;
                        final String fM = message;
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override public void run() {
                                showUpdateDialog(fV, fM);
                            }
                        });
                    } else {
                        proceedToMenu();
                    }
                } catch (Exception e) {
                    proceedToMenu();
                }
            }
        }).start();
    }

    private void proceedToMenu() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                if (callback != null) callback.onLoginSuccess();
            }
        });
    }

    private void showUpdateDialog(String version, String msg) {
        if (!(ctx instanceof android.app.Activity)) {
            proceedToMenu();
            return;
        }
        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_1, COLOR_BG_2});
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1.2f), COLOR_ACCENT);
        box.setBackground(bg);

        TextView t = new TextView(ctx);
        t.setText("New Update Available");
        t.setTextColor(COLOR_ACCENT);
        t.setTextSize(15);
        t.setTypeface(tfBold);
        box.addView(t);

        TextView body = new TextView(ctx);
        String txt = "Version " + version + " is now available.\n" +
                "You're currently on version " + getVersionName() + ".";
        if (msg != null && !msg.isEmpty()) txt += "\n\n" + msg;
        body.setText(txt);
        body.setTextColor(COLOR_TEXT);
        body.setTextSize(12);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        bLp.setMargins(0, dp(10), 0, 0);
        box.addView(body, bLp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        rLp.setMargins(0, dp(16), 0, 0);
        box.addView(row, rLp);

        Button continueBtn = new Button(ctx);
        continueBtn.setText("Continue");
        continueBtn.setAllCaps(false);
        continueBtn.setTextColor(COLOR_MUTED);
        continueBtn.setTextSize(12);
        continueBtn.setTypeface(tfMedium);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0x14FFFFFF);
        cbg.setCornerRadius(dp(10));
        continueBtn.setBackground(cbg);
        continueBtn.setPadding(dp(18), dp(10), dp(18), dp(10));

        Button updateBtn = new Button(ctx);
        updateBtn.setText("Update");
        updateBtn.setAllCaps(false);
        updateBtn.setTextColor(Color.WHITE);
        updateBtn.setTextSize(12);
        updateBtn.setTypeface(tfBold);
        GradientDrawable ubg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0093C4, 0xFF6A47F5});
        ubg.setCornerRadius(dp(10));
        updateBtn.setBackground(ubg);
        updateBtn.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        uLp.setMargins(dp(8), 0, 0, 0);
        updateBtn.setLayoutParams(uLp);

        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
                proceedToMenu();
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

        row.addView(continueBtn);
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
        }
        ref[0] = d;
        d.show();
    }

    private void showKeyExpiredDialog() {
        if (keyExpiredDialogShowing) return;
        keyExpiredDialogShowing = true;
        if (!(ctx instanceof android.app.Activity)) return;

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_1, COLOR_BG_2});
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1.2f), COLOR_DANGER);
        box.setBackground(bg);

        TextView t = new TextView(ctx);
        t.setText("Access Expired");
        t.setTextColor(COLOR_DANGER);
        t.setTextSize(15);
        t.setTypeface(tfBold);
        box.addView(t);

        TextView body = new TextView(ctx);
        body.setText("Your subscription has ended or the account is blocked.\n\n" +
                "Contact the seller to renew access.");
        body.setTextColor(COLOR_TEXT);
        body.setTextSize(12);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        bLp.setMargins(0, dp(10), 0, 0);
        box.addView(body, bLp);

        Button contact = new Button(ctx);
        contact.setText("CONTACT SELLER");
        contact.setAllCaps(false);
        contact.setTextColor(Color.WHITE);
        contact.setTextSize(13);
        contact.setTypeface(tfBold);
        GradientDrawable cbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF00B489, 0xFF00F5A0});
        cbg.setCornerRadius(dp(20));
        contact.setBackground(cbg);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(42));
        cLp.setMargins(0, dp(14), 0, 0);
        contact.setLayoutParams(cLp);
        box.addView(contact);

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
        }
        ref[0] = d;
        d.show();
    }

    private class CustomCheck extends LinearLayout {
        interface Listener { void onChanged(boolean checked); }

        private final GradientDrawable boxBg;
        private final View checkmark;
        private boolean checked;
        private Listener listener;

        CustomCheck(String label, boolean initial) {
            super(ctx);
            this.checked = initial;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0, 0, 0, 0);

            FrameLayout box = new FrameLayout(ctx);
            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(dp(16), dp(16));
            boxLp.setMargins(0, 0, dp(6), 0);
            box.setLayoutParams(boxLp);

            boxBg = new GradientDrawable();
            boxBg.setCornerRadius(dp(4));
            boxBg.setStroke(dp(1.4f), initial ? COLOR_ACCENT : COLOR_FIELD_BORD);
            boxBg.setColor(initial ? COLOR_CHECKED_BG : Color.TRANSPARENT);
            box.setBackground(boxBg);

            checkmark = new View(ctx);
            FrameLayout.LayoutParams cmLp = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER);
            checkmark.setLayoutParams(cmLp);
            GradientDrawable cmBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{COLOR_ACCENT, COLOR_ACCENT_2});
            cmBg.setCornerRadius(dp(2));
            checkmark.setBackground(cmBg);
            checkmark.setVisibility(initial ? View.VISIBLE : View.GONE);
            box.addView(checkmark);

            TextView labelView = new TextView(ctx);
            labelView.setText(label);
            labelView.setTextColor(COLOR_MUTED);
            labelView.setTextSize(10f);
            labelView.setTypeface(tfRegular);

            addView(box);
            addView(labelView);

            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle();
                }
            });
        }

        void setListener(Listener l) { this.listener = l; }

        void setChecked(boolean value) {
            if (this.checked != value) toggle();
        }

        boolean isChecked() { return checked; }

        private void toggle() {
            checked = !checked;
            checkmark.setVisibility(checked ? View.VISIBLE : View.GONE);
            boxBg.setStroke(dp(1.4f), checked ? COLOR_ACCENT : COLOR_FIELD_BORD);
            boxBg.setColor(checked ? COLOR_CHECKED_BG : Color.TRANSPARENT);
            if (listener != null) listener.onChanged(checked);
        }
    }

    private static class FieldIcon extends Drawable {
        static final int USER = 0;
        static final int LOCK = 1;

        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private int currentColor;

        FieldIcon(int type, int color) {
            this.type = type;
            this.currentColor = color;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void animateColor(int toColor, int durationMs) {
            ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), currentColor, toColor);
            va.setDuration(durationMs);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    currentColor = (Integer) a.getAnimatedValue();
                    paint.setColor(currentColor);
                    invalidateSelf();
                }
            });
            va.start();
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;
            paint.setStrokeWidth(1.8f * s);

            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f,
                             b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);
            path.reset();

            if (type == USER) {
                canvas.drawCircle(12f, 8f, 3.8f, paint);
                path.moveTo(4.5f, 21f);
                path.cubicTo(4.5f, 15.5f, 8f, 13.8f, 12f, 13.8f);
                path.cubicTo(16f, 13.8f, 19.5f, 15.5f, 19.5f, 21f);
                canvas.drawPath(path, paint);
            } else {
                rect.set(5f, 10.5f, 19f, 21f);
                canvas.drawRoundRect(rect, 2f, 2f, paint);
                path.moveTo(8.5f, 10.5f);
                path.lineTo(8.5f, 7.5f);
                path.cubicTo(8.5f, 5.0f, 10.2f, 3f, 12f, 3f);
                path.cubicTo(13.8f, 3f, 15.5f, 5.0f, 15.5f, 7.5f);
                path.lineTo(15.5f, 10.5f);
                canvas.drawPath(path, paint);
                canvas.drawCircle(12f, 15.5f, 1.2f, paint);
            }
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}