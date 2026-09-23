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

    public interface CheckListener {
        void onChanged(boolean checked);
    }

    private static final String TAG = "LoginHelper";

    // ---- "Obsidian & Gilt" palette (matches Menu.java) : graphite-navy + champagne-gold ----
    private static final int COLOR_BG_1       = Color.parseColor("#0C0E14");
    private static final int COLOR_CARD       = Color.parseColor("#181B24");
    private static final int COLOR_BORDER     = Color.parseColor("#2E313C");
    private static final int COLOR_FIELD_BG   = Color.parseColor("#14161F");
    private static final int COLOR_FIELD_BORD = Color.parseColor("#2A2D38");
    private static final int COLOR_ACCENT     = Color.parseColor("#D8B36C");   // champagne gold
    private static final int COLOR_ACCENT_HI  = Color.parseColor("#F1DFAE");   // pale gold sheen
    private static final int COLOR_SUCCESS    = Color.parseColor("#4FBA82");
    private static final int COLOR_DANGER     = Color.parseColor("#C25C56");
    private static final int COLOR_WARN       = Color.parseColor("#E0A94D");
    private static final int COLOR_TEXT       = Color.parseColor("#ECE8DF");
    private static final int COLOR_TEXT_MUTED = Color.parseColor("#8D8F99");
    private static final int COLOR_HINT       = Color.parseColor("#5C5F6A");
    // Dark navy/charcoal used for text & icons placed on top of the light-gold accent surfaces
    private static final int COLOR_ON_ACCENT  = Color.parseColor("#14161F");

    // Dialogs now share the same dark "Obsidian & Gilt" surface instead of a plain white card
    private static final int COLOR_DIALOG_BG     = Color.parseColor("#1D2029");
    private static final int COLOR_DIALOG_TITLE  = Color.parseColor("#ECE8DF");
    private static final int COLOR_DIALOG_SUB    = Color.parseColor("#A7A9B2");
    private static final int COLOR_DIALOG_SUB2   = Color.parseColor("#7D7F89");
    private static final int COLOR_DIALOG_BODY   = Color.parseColor("#C6C8D0");
    private static final int COLOR_CTA           = Color.parseColor("#D8B36C");   // gold call-to-action
    private static final int COLOR_OUTLINE       = Color.parseColor("#3A3D49");

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
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_CARD);
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), COLOR_BORDER);
        card.setBackground(cardBg);
        root.addView(card);

        TextView userLabel = makeFieldLabel("USERNAME");
        card.addView(userLabel);

        userBox = makeFieldContainer();
        ImageView userIcon = new ImageView(ctx);
        userIcon.setImageDrawable(new FieldIcon(FieldIcon.USER, COLOR_TEXT_MUTED));
        userIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams uiLp = new LinearLayout.LayoutParams(dp(16), dp(16));
        uiLp.setMargins(dp(11), 0, 0, 0);
        userIcon.setLayoutParams(uiLp);
        userBox.addView(userIcon);

        editUser = makeInput();
        editUser.setHint("Enter username");
        editUser.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        editUser.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams ueLp = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f);
        editUser.setLayoutParams(ueLp);
        userBox.addView(editUser);

        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(36));
        uLp.setMargins(0, dp(4), 0, dp(12));
        userBox.setLayoutParams(uLp);
        card.addView(userBox);

        TextView passLabel = makeFieldLabel("PASSWORD");
        card.addView(passLabel);

        passBox = makeFieldContainer();
        ImageView passIcon = new ImageView(ctx);
        passIcon.setImageDrawable(new FieldIcon(FieldIcon.LOCK, COLOR_TEXT_MUTED));
        passIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams piLp = new LinearLayout.LayoutParams(dp(16), dp(16));
        piLp.setMargins(dp(11), 0, 0, 0);
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

        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(36));
        pLp.setMargins(0, dp(4), 0, dp(10));
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
        showCb.setListener(new CheckListener() {
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

        rememberCb.setListener(new CheckListener() {
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
        loginBtn.setTextColor(COLOR_ON_ACCENT);
        loginBtn.setTextSize(12f);
        loginBtn.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            loginBtn.setLetterSpacing(0.14f);
        }

        GradientDrawable lb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                                    new int[]{COLOR_ACCENT, Color.parseColor("#C79C56")});
        lb.setCornerRadius(dp(12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(0x26000000), lb, null);
            loginBtn.setBackground(ripple);
        } else {
            loginBtn.setBackground(lb);
        }
        loginBtn.setMinHeight(0);
        loginBtn.setMinimumHeight(0);
        loginBtn.setMinWidth(0);
        loginBtn.setMinimumWidth(0);
        loginBtn.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(40));
        bLp.setMargins(0, dp(12), 0, 0);
        loginBtn.setLayoutParams(bLp);
        card.addView(loginBtn);

        statusTxt = new TextView(ctx);
        statusTxt.setText("");
        statusTxt.setTextColor(COLOR_TEXT_MUTED);
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
                v.animate().scaleX(0.975f).scaleY(0.975f).setDuration(100)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                loginBtn.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(260)
                                        .setInterpolator(new OvershootInterpolator(1.3f))
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
        tv.setTextColor(COLOR_TEXT_MUTED);
        tv.setTextSize(9f);
        tv.setTypeface(tfMedium);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tv.setLetterSpacing(0.12f);
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
        e.setHintTextColor(COLOR_HINT);
        e.setTextColor(COLOR_TEXT);
        e.setTextSize(12.5f);
        e.setTypeface(tfRegular);
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
                va.setDuration(200);
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        bg.setStroke(dp(hasFocus ? 1.5f : 1f), (Integer) a.getAnimatedValue());
                    }
                });
                va.start();

                FieldIcon fi = (FieldIcon) icon.getDrawable();
                if (fi != null) {
                    fi.animateColor(hasFocus ? COLOR_ACCENT_HI : COLOR_TEXT_MUTED, 200);
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
        setStatus("Verifying credentials...", COLOR_ACCENT_HI);

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
        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];

        FrameLayout dialogRoot = new FrameLayout(ctx);
        dialogRoot.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(24), dp(22), dp(20));
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_DIALOG_BG);
        cardBg.setCornerRadius(dp(20));
        cardBg.setStroke(dp(1), COLOR_BORDER);
        card.setBackground(cardBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(12));
        }

        FrameLayout iconHolder = new FrameLayout(ctx);
        int iconSize = dp(64);
        LinearLayout.LayoutParams ihLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ihLp.setMargins(0, 0, 0, dp(14));
        iconHolder.setLayoutParams(ihLp);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(COLOR_CTA);
        iconHolder.setBackground(circle);

        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(new UpdateIcon(COLOR_ON_ACCENT));
        FrameLayout.LayoutParams icLp = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
        icon.setLayoutParams(icLp);
        iconHolder.addView(icon);
        card.addView(iconHolder);

        TextView title = new TextView(ctx);
        title.setText("NEW UPDATE");
        title.setTextColor(COLOR_DIALOG_TITLE);
        title.setTextSize(16f);
        title.setTypeface(tfBold);
        title.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            title.setLetterSpacing(0.08f);
        }
        card.addView(title);

        TextView versionView = new TextView(ctx);
        versionView.setText("Version " + version + " is available now");
        versionView.setTextColor(COLOR_DIALOG_SUB);
        versionView.setTextSize(12f);
        versionView.setTypeface(tfRegular);
        versionView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        vLp.setMargins(0, dp(6), 0, 0);
        versionView.setLayoutParams(vLp);
        card.addView(versionView);

        TextView currentView = new TextView(ctx);
        currentView.setText("Your version: " + getVersionName());
        currentView.setTextColor(COLOR_DIALOG_SUB2);
        currentView.setTextSize(10.5f);
        currentView.setTypeface(tfRegular);
        currentView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams curLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        curLp.setMargins(0, dp(3), 0, 0);
        currentView.setLayoutParams(curLp);
        card.addView(currentView);

        if (msg != null && !msg.trim().isEmpty()) {
            TextView msgView = new TextView(ctx);
            msgView.setText(msg);
            msgView.setTextColor(COLOR_DIALOG_BODY);
            msgView.setTextSize(11f);
            msgView.setTypeface(tfRegular);
            msgView.setGravity(Gravity.CENTER);
            msgView.setMaxLines(3);
            msgView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            mLp.setMargins(0, dp(12), 0, 0);
            msgView.setLayoutParams(mLp);
            card.addView(msgView);
        }

        Button updateBtn = new Button(ctx);
        updateBtn.setText("UPDATE NOW");
        updateBtn.setAllCaps(false);
        updateBtn.setTextColor(COLOR_ON_ACCENT);
        updateBtn.setTextSize(12.5f);
        updateBtn.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            updateBtn.setLetterSpacing(0.1f);
        }
        GradientDrawable uBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                                     new int[]{COLOR_CTA, Color.parseColor("#C79C56")});
        uBg.setCornerRadius(dp(25));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable uRipple = new RippleDrawable(
                    ColorStateList.valueOf(0x26000000), uBg, null);
            updateBtn.setBackground(uRipple);
            updateBtn.setElevation(dp(3));
        } else {
            updateBtn.setBackground(uBg);
        }
        updateBtn.setMinHeight(0);
        updateBtn.setMinimumHeight(0);
        updateBtn.setMinWidth(0);
        updateBtn.setMinimumWidth(0);
        updateBtn.setPadding(dp(20), 0, dp(20), 0);
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(48));
        uLp.setMargins(0, dp(20), 0, 0);
        updateBtn.setLayoutParams(uLp);
        card.addView(updateBtn);

        Button continueBtn = new Button(ctx);
        continueBtn.setText("Continue to game");
        continueBtn.setAllCaps(false);
        continueBtn.setTextColor(COLOR_DIALOG_SUB);
        continueBtn.setTextSize(12f);
        continueBtn.setTypeface(tfMedium);
        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(Color.TRANSPARENT);
        cBg.setStroke(dp(1.5f), COLOR_OUTLINE);
        cBg.setCornerRadius(dp(25));
        continueBtn.setBackground(cBg);
        continueBtn.setMinHeight(0);
        continueBtn.setMinimumHeight(0);
        continueBtn.setMinWidth(0);
        continueBtn.setMinimumWidth(0);
        continueBtn.setPadding(dp(20), 0, dp(20), 0);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(44));
        cLp.setMargins(0, dp(8), 0, 0);
        continueBtn.setLayoutParams(cLp);
        card.addView(continueBtn);

        dialogRoot.addView(card, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

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

        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
                proceedToMenu();
            }
        });

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(ctx);
        b.setView(dialogRoot);
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

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];

        FrameLayout dialogRoot = new FrameLayout(ctx);
        dialogRoot.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(24), dp(22), dp(20));
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_DIALOG_BG);
        cardBg.setCornerRadius(dp(20));
        cardBg.setStroke(dp(1), COLOR_BORDER);
        card.setBackground(cardBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(12));
        }

        FrameLayout iconHolder = new FrameLayout(ctx);
        int iconSize = dp(64);
        LinearLayout.LayoutParams ihLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ihLp.setMargins(0, 0, 0, dp(14));
        iconHolder.setLayoutParams(ihLp);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(COLOR_DANGER);
        iconHolder.setBackground(circle);

        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(new LockIcon(Color.WHITE));
        FrameLayout.LayoutParams icLp = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
        icon.setLayoutParams(icLp);
        iconHolder.addView(icon);
        card.addView(iconHolder);

        TextView title = new TextView(ctx);
        title.setText("ACCESS EXPIRED");
        title.setTextColor(COLOR_DIALOG_TITLE);
        title.setTextSize(16f);
        title.setTypeface(tfBold);
        title.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            title.setLetterSpacing(0.08f);
        }
        card.addView(title);

        TextView body = new TextView(ctx);
        body.setText("Your subscription has ended or the account is blocked.\n\nContact the seller to renew access.");
        body.setTextColor(COLOR_DIALOG_BODY);
        body.setTextSize(12f);
        body.setTypeface(tfRegular);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        bLp.setMargins(0, dp(12), 0, 0);
        body.setLayoutParams(bLp);
        card.addView(body);

        Button contact = new Button(ctx);
        contact.setText("CONTACT SELLER");
        contact.setAllCaps(false);
        contact.setTextColor(COLOR_ON_ACCENT);
        contact.setTextSize(12.5f);
        contact.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            contact.setLetterSpacing(0.1f);
        }
        GradientDrawable cBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                                     new int[]{COLOR_CTA, Color.parseColor("#C79C56")});
        cBg.setCornerRadius(dp(25));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable cRipple = new RippleDrawable(
                    ColorStateList.valueOf(0x26000000), cBg, null);
            contact.setBackground(cRipple);
            contact.setElevation(dp(3));
        } else {
            contact.setBackground(cBg);
        }
        contact.setMinHeight(0);
        contact.setMinimumHeight(0);
        contact.setMinWidth(0);
        contact.setMinimumWidth(0);
        contact.setPadding(dp(20), 0, dp(20), 0);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(48));
        cLp.setMargins(0, dp(20), 0, 0);
        contact.setLayoutParams(cLp);
        card.addView(contact);

        dialogRoot.addView(card, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

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
        b.setView(dialogRoot);
        android.app.AlertDialog d = b.create();
        d.setCanceledOnTouchOutside(false);
        d.setCancelable(true);
        d.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(android.content.DialogInterface di) {
                keyExpiredDialogShowing = false;
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
        private final GradientDrawable boxBg;
        private final View checkmark;
        private boolean checked;
        private CheckListener listener;

        CustomCheck(String label, boolean initial) {
            super(ctx);
            this.checked = initial;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0, 0, 0, 0);

            FrameLayout box = new FrameLayout(ctx);
            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(dp(16), dp(16));
            boxLp.setMargins(0, 0, dp(8), 0);
            box.setLayoutParams(boxLp);

            boxBg = new GradientDrawable();
            boxBg.setCornerRadius(dp(4));
            boxBg.setStroke(dp(1.5f), initial ? COLOR_ACCENT : COLOR_FIELD_BORD);
            boxBg.setColor(initial ? withAlpha(COLOR_ACCENT, 0x22) : Color.TRANSPARENT);
            box.setBackground(boxBg);

            checkmark = new View(ctx);
            FrameLayout.LayoutParams cmLp = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER);
            checkmark.setLayoutParams(cmLp);
            GradientDrawable cmBg = new GradientDrawable();
            cmBg.setColor(COLOR_ACCENT);
            cmBg.setCornerRadius(dp(2));
            checkmark.setBackground(cmBg);
            checkmark.setVisibility(initial ? View.VISIBLE : View.GONE);
            box.addView(checkmark);

            TextView labelView = new TextView(ctx);
            labelView.setText(label);
            labelView.setTextColor(COLOR_TEXT_MUTED);
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

        void setListener(CheckListener l) { this.listener = l; }

        void setChecked(boolean value) {
            if (this.checked != value) toggle();
        }

        boolean isChecked() { return checked; }

        private void toggle() {
            checked = !checked;
            checkmark.setVisibility(checked ? View.VISIBLE : View.GONE);
            boxBg.setStroke(dp(1.5f), checked ? COLOR_ACCENT : COLOR_FIELD_BORD);
            boxBg.setColor(checked ? withAlpha(COLOR_ACCENT, 0x22) : Color.TRANSPARENT);
            if (listener != null) listener.onChanged(checked);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
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
            paint.setStrokeWidth(1.9f * s);

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

    private static class UpdateIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        UpdateIcon(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;
            paint.setStrokeWidth(2.2f * s);

            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f,
                             b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);
            path.reset();

            path.moveTo(12f, 3f);
            path.lineTo(12f, 15f);
            canvas.drawPath(path, paint);

            path.reset();
            path.moveTo(6f, 10f);
            path.lineTo(12f, 16f);
            path.lineTo(18f, 10f);
            canvas.drawPath(path, paint);

            path.reset();
            path.moveTo(4f, 20.5f);
            path.lineTo(20f, 20.5f);
            canvas.drawPath(path, paint);

            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class LockIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        LockIcon(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;
            paint.setStrokeWidth(2.2f * s);

            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f,
                             b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);
            path.reset();

            rect.set(5f, 10.5f, 19f, 21f);
            canvas.drawRoundRect(rect, 2f, 2f, paint);
            path.moveTo(8.5f, 10.5f);
            path.lineTo(8.5f, 7.5f);
            path.cubicTo(8.5f, 5.0f, 10.2f, 3f, 12f, 3f);
            path.cubicTo(13.8f, 3f, 15.5f, 5.0f, 15.5f, 7.5f);
            path.lineTo(15.5f, 10.5f);
            canvas.drawPath(path, paint);
            canvas.drawCircle(12f, 15.5f, 1.2f, paint);

            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}