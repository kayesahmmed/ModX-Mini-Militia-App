package com.android.support;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.view.inputmethod.EditorInfo;
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

    // ================================================================
    //  PREMIUM COLOR PALETTE
    // ================================================================
    private static final int COLOR_BG_TOP      = Color.parseColor("#0E1730");
    private static final int COLOR_BG_BOTTOM   = Color.parseColor("#060A18");
    private static final int COLOR_ACCENT      = Color.parseColor("#00E5FF");
    private static final int COLOR_ACCENT_2    = Color.parseColor("#7C4DFF");
    private static final int COLOR_SUCCESS     = Color.parseColor("#00F5A0");
    private static final int COLOR_DANGER      = Color.parseColor("#FF4D6D");
    private static final int COLOR_WARN        = Color.parseColor("#FFB300");
    private static final int COLOR_TEXT        = Color.parseColor("#EAF1FF");
    private static final int COLOR_MUTED       = Color.parseColor("#7E8CB3");
    private static final int COLOR_FIELD_BG    = Color.parseColor("#0A1120");
    private static final int COLOR_FIELD_BORDER= Color.parseColor("#1E2A48");
    private static final int COLOR_FIELD_FOCUS = Color.parseColor("#00E5FF");

    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;

    private final Context ctx;
    private final Callback callback;
    private final SharedPreferences save;
    private final SharedPreferences KEY;

    private EditText editUser, editPass;
    private LinearLayout userBox, passBox;
    private TextView userLabel, passLabel;
    private GradientDrawable userBg, passBg;
    private CheckBox rememberCb, showCb;
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

    // ================================================================
    //  BUILD PREMIUM LOGIN VIEW
    // ================================================================
    public View buildView() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(6), dp(6), dp(6), dp(6));

        // ---- Greeting ----
        TextView greeting = new TextView(ctx);
        greeting.setText("Welcome back");
        greeting.setTextColor(COLOR_TEXT);
        greeting.setTextSize(15f);
        greeting.setTypeface(tfBold);
        greeting.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            greeting.setLetterSpacing(0.02f);
        }
        root.addView(greeting);

        TextView hint = new TextView(ctx);
        hint.setText("Sign in to unlock ModX Lab");
        hint.setTextColor(COLOR_MUTED);
        hint.setTextSize(10.5f);
        hint.setTypeface(tfRegular);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        hintLp.setMargins(0, dp(2), 0, dp(10));
        hint.setLayoutParams(hintLp);
        root.addView(hint);

        // ---- Card containing form ----
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable cardBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        cardBg.setCornerRadius(dp(14));
        cardBg.setStroke(dp(1), COLOR_FIELD_BORDER);
        card.setBackground(cardBg);
        card.setElevation(dp(4));
        root.addView(card);

        // ---- Username ----
        userLabel = makeFieldLabel("USERNAME");
        card.addView(userLabel);

        userBox = makeFieldContainer();
        editUser = makeInput(false);
        editUser.setHint("your username");
        editUser.setImeOptions(EditorInfo.IME_ACTION_NEXT);   // 🔥 Next button
        editUser.setInputType(InputType.TYPE_CLASS_TEXT);
        userBox.addView(editUser, new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(38));
        uLp.setMargins(0, dp(4), 0, dp(10));
        userBox.setLayoutParams(uLp);
        card.addView(userBox);

        // ---- Password ----
        passLabel = makeFieldLabel("PASSWORD");
        card.addView(passLabel);

        passBox = makeFieldContainer();
        editPass = makeInput(true);
        editPass.setHint("your password");
        editPass.setImeOptions(EditorInfo.IME_ACTION_DONE);   // 🔥 Done button
        editPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passBox.addView(editPass, new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(38));
        pLp.setMargins(0, dp(4), 0, dp(8));
        passBox.setLayoutParams(pLp);
        card.addView(passBox);

        // ---- Focus animations ----
        attachFieldFocus(userBox, editUser, true);
        attachFieldFocus(passBox, editPass, false);

        // ---- IME Actions (Next → Password, Done → Login) ----
        editUser.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT
                 || actionId == EditorInfo.IME_ACTION_DONE
                 || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    editPass.requestFocus();
                    editPass.setSelection(editPass.getText().length());
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

        // ---- Checkbox row ----
        LinearLayout cbRow = new LinearLayout(ctx);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbRow.setGravity(Gravity.CENTER_VERTICAL);
        cbRow.setPadding(0, dp(2), 0, dp(2));

        rememberCb = new CheckBox(ctx);
        rememberCb.setText("Remember me");
        rememberCb.setTextColor(COLOR_TEXT);
        rememberCb.setTextSize(10.5f);
        rememberCb.setTypeface(tfRegular);
        rememberCb.setPadding(0, 0, 0, 0);
        rememberCb.setMinHeight(0);
        rememberCb.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            rememberCb.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        rememberCb.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        showCb = new CheckBox(ctx);
        showCb.setText("Show");
        showCb.setTextColor(COLOR_TEXT);
        showCb.setTextSize(10.5f);
        showCb.setTypeface(tfRegular);
        showCb.setPadding(0, 0, 0, 0);
        showCb.setMinHeight(0);
        showCb.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            showCb.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));

        cbRow.addView(rememberCb);
        cbRow.addView(showCb);
        card.addView(cbRow);

        // ---- Login button ----
        loginBtn = new Button(ctx);
        loginBtn.setText("SIGN IN");
        loginBtn.setAllCaps(false);
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setTextSize(12.5f);
        loginBtn.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            loginBtn.setLetterSpacing(0.08f);
        }
        GradientDrawable lb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0088CC, 0xFF6A47F5});
        lb.setCornerRadius(dp(10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(0x44FFFFFF), lb, null);
            loginBtn.setBackground(ripple);
        } else {
            loginBtn.setBackground(lb);
        }
        loginBtn.setMinHeight(0);
        loginBtn.setMinimumHeight(0);
        loginBtn.setMinWidth(0);
        loginBtn.setMinimumWidth(0);
        loginBtn.setPadding(dp(8), 0, dp(8), 0);
        loginBtn.setElevation(dp(2));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(38));
        bLp.setMargins(0, dp(8), 0, 0);
        loginBtn.setLayoutParams(bLp);
        card.addView(loginBtn);

        // ---- Status text (with fade animation) ----
        statusTxt = new TextView(ctx);
        statusTxt.setText("");
        statusTxt.setTextColor(COLOR_MUTED);
        statusTxt.setTextSize(10.5f);
        statusTxt.setTypeface(tfMedium);
        statusTxt.setGravity(Gravity.CENTER);
        statusTxt.setSingleLine(true);
        statusTxt.setAlpha(0f);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        sLp.setMargins(0, dp(6), 0, 0);
        statusTxt.setLayoutParams(sLp);
        card.addView(statusTxt);

        // ================================================================
        //  Listeners
        // ================================================================
        showCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean b) {
                int sel = editPass.getSelectionStart();
                if (b) editPass.setTransformationMethod(
                        android.text.method.HideReturnsTransformationMethod.getInstance());
                else editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
                if (sel >= 0 && sel <= editPass.getText().length()) {
                    editPass.setSelection(sel);
                }
            }
        });

        rememberCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean b) {
                if (b) {
                    save.edit().putString("edittext1", editUser.getText().toString()).apply();
                    save.edit().putString("edittext2", editPass.getText().toString()).apply();
                } else {
                    save.edit().remove("edittext1").apply();
                    save.edit().remove("edittext2").apply();
                }
            }
        });

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                loginBtn.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
                            }
                        }).start();
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

        // Silent update check
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() { checkUpdateAsync(); }
        }, 400);

        return root;
    }

    // ================================================================
    private TextView makeFieldLabel(String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(COLOR_MUTED);
        tv.setTextSize(8.5f);
        tv.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tv.setLetterSpacing(0.15f);
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
        bg.setCornerRadius(dp(9));
        bg.setStroke(dp(1.2f), COLOR_FIELD_BORDER);
        box.setBackground(bg);
        box.setClipToPadding(false);
        return box;
    }

    private EditText makeInput(boolean isPassword) {
        EditText e = new EditText(ctx);
        e.setHintTextColor(COLOR_MUTED);
        e.setTextColor(COLOR_TEXT);
        e.setTextSize(12.5f);
        e.setTypeface(tfMedium);
        e.setSingleLine(true);
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        e.setClickable(true);
        e.setCursorVisible(true);
        e.setBackground(null);
        e.setIncludeFontPadding(false);

        // 🔥 FIX: cursor position behind text
        e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        e.setPadding(dp(12), 0, dp(12), 0);

        // IME — necessary for smooth Next/Done behaviour
        e.setImeOptions(isPassword
                ? EditorInfo.IME_ACTION_DONE
                : EditorInfo.IME_ACTION_NEXT);
        e.setInputType(isPassword
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT);

        attachKeyboardFix(e);
        return e;
    }

    // ================================================================
    //  Field focus animation — border glow
    // ================================================================
    private void attachFieldFocus(final LinearLayout box, final EditText et, final boolean isUser) {
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                final GradientDrawable bg = (GradientDrawable) box.getBackground();
                int fromColor = hasFocus ? COLOR_FIELD_BORDER : COLOR_FIELD_FOCUS;
                int toColor   = hasFocus ? COLOR_FIELD_FOCUS  : COLOR_FIELD_BORDER;

                ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(),
                        fromColor, toColor);
                va.setDuration(220);
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        bg.setStroke(dp(1.2f), (Integer) a.getAnimatedValue());
                    }
                });
                va.start();

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
    }

    // ================================================================
    private void attachKeyboardFix(final EditText et) {
        // Handled above in attachFieldFocus
    }

    // ================================================================
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

    // ================================================================
    //  Status update with smooth fade animation
    // ================================================================
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

    // ================================================================
    //  UPDATE CHECK (REST)
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
                        final String fV = version;
                        final String fM = message;
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() { showUpdateDialog(fV, fM); }
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
        box.setPadding(dp(18), dp(16), dp(18), dp(14));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), COLOR_ACCENT);
        box.setBackground(bg);

        TextView t = new TextView(ctx);
        t.setText("🚀 Update Available");
        t.setTextColor(COLOR_ACCENT);
        t.setTextSize(15);
        t.setTypeface(tfBold);
        box.addView(t);

        TextView body = new TextView(ctx);
        String txt = "Current : " + getVersionName() + "\nLatest  : " + version;
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
        rLp.setMargins(0, dp(14), 0, 0);
        box.addView(row, rLp);

        Button exitBtn = new Button(ctx);
        exitBtn.setText("Exit");
        exitBtn.setAllCaps(false);
        exitBtn.setTextColor(COLOR_MUTED);
        exitBtn.setTextSize(12);
        GradientDrawable ebg = new GradientDrawable();
        ebg.setColor(0x14FFFFFF);
        ebg.setCornerRadius(dp(10));
        exitBtn.setBackground(ebg);
        exitBtn.setPadding(dp(16), dp(8), dp(16), dp(8));

        Button updateBtn = new Button(ctx);
        updateBtn.setText("Update");
        updateBtn.setAllCaps(false);
        updateBtn.setTextColor(Color.WHITE);
        updateBtn.setTextSize(12);
        updateBtn.setTypeface(tfBold);
        GradientDrawable ubg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0088CC, 0xFF6A47F5});
        ubg.setCornerRadius(dp(10));
        updateBtn.setBackground(ubg);
        updateBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        uLp.setMargins(dp(8), 0, 0, 0);
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
        }
        ref[0] = d;
        d.show();
    }

    // ================================================================
    //  LOGIN (REST)
    // ================================================================
    private void performLogin() {
        if (loginInProgress) return;

        final String inputUser = editUser.getText().toString().trim();
        final String inputPass = editPass.getText().toString().trim();

        if (TextUtils.isEmpty(inputUser) || TextUtils.isEmpty(inputPass)) {
            setStatus("⚠  Please fill in all fields", COLOR_WARN);
            return;
        }

        loginInProgress = true;
        loginBtn.setEnabled(false);
        loginBtn.setText("SIGNING IN...");
        setStatus("⏳  Verifying credentials…", COLOR_ACCENT);

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
                            setStatus("⚠  Connection failed", COLOR_DANGER);
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
                            setStatus("✕  Invalid username or password", COLOR_DANGER);
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
                            setStatus("⚠  Key expired or blocked", COLOR_DANGER);
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
                        setStatus("✓  Welcome back!", COLOR_SUCCESS);
                        Toast.makeText(ctx, "Login Success", Toast.LENGTH_SHORT).show();

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) callback.onLoginSuccess();
                            }
                        }, 750);
                    }
                });
            }
        }).start();
    }

    // ================================================================
    //  KEY EXPIRED DIALOG
    // ================================================================
    private void showKeyExpiredDialog() {
        if (keyExpiredDialogShowing) return;
        keyExpiredDialogShowing = true;
        if (!(ctx instanceof android.app.Activity)) return;

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(14));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), COLOR_DANGER);
        box.setBackground(bg);

        TextView t = new TextView(ctx);
        t.setText("🔒 Access Expired");
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
}