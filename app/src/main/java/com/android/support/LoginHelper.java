package com.android.support;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Iterator;

public class LoginHelper {

    public interface Callback {
        void onLoginSuccess();
    }

    private static final int COLOR_BG_TOP    = Color.parseColor("#111A36");
    private static final int COLOR_BG_BOTTOM = Color.parseColor("#080C19");
    private static final int COLOR_ACCENT    = Color.parseColor("#00E5FF");
    private static final int COLOR_SUCCESS   = Color.parseColor("#00F5A0");
    private static final int COLOR_DANGER    = Color.parseColor("#FF4D6D");
    private static final int COLOR_TEXT      = Color.parseColor("#F2F6FF");
    private static final int COLOR_MUTED     = Color.parseColor("#8194BE");
    private static final int COLOR_CARD      = Color.parseColor("#131B35");
    private static final int COLOR_BORDER    = Color.parseColor("#26335F");

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
    //  Compact login view — fits inside menu without scrolling
    // ================================================================
    public View buildView() {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setClickable(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(dp(2), dp(2), dp(2), dp(2));

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(0, dp(4), 0, dp(4));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Card
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_CARD);
        cardBg.setCornerRadius(dp(12));
        cardBg.setStroke(dp(1), COLOR_BORDER);
        card.setBackground(cardBg);
        root.addView(card);

        // Username
        TextView l1 = new TextView(ctx);
        l1.setText("USERNAME");
        l1.setTextColor(COLOR_MUTED);
        l1.setTextSize(8.5f);
        l1.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(l1);

        editUser = makeInput(false);
        editUser.setHint("Enter username");
        LinearLayout.LayoutParams e1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        e1.setMargins(0, dp(3), 0, dp(8));
        editUser.setLayoutParams(e1);
        card.addView(editUser);

        // Password
        TextView l2 = new TextView(ctx);
        l2.setText("PASSWORD");
        l2.setTextColor(COLOR_MUTED);
        l2.setTextSize(8.5f);
        l2.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(l2);

        editPass = makeInput(true);
        editPass.setHint("Enter password");
        LinearLayout.LayoutParams e2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        e2.setMargins(0, dp(3), 0, dp(4));
        editPass.setLayoutParams(e2);
        card.addView(editPass);

        attachKeyboardFix(editUser);
        attachKeyboardFix(editPass);

        // Checkboxes
        LinearLayout cbRow = new LinearLayout(ctx);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbRow.setGravity(Gravity.CENTER_VERTICAL);

        rememberCb = new CheckBox(ctx);
        rememberCb.setText("Remember");
        rememberCb.setTextColor(COLOR_TEXT);
        rememberCb.setTextSize(9.5f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            rememberCb.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        rememberCb.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        showCb = new CheckBox(ctx);
        showCb.setText("Show");
        showCb.setTextColor(COLOR_TEXT);
        showCb.setTextSize(9.5f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            showCb.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));

        cbRow.addView(rememberCb);
        cbRow.addView(showCb);
        card.addView(cbRow);

        // Login button
        loginBtn = new Button(ctx);
        loginBtn.setText("LOGIN");
        loginBtn.setAllCaps(false);
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setTextSize(12);
        loginBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable lb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0093C4, 0xFF6A47F5});
        lb.setCornerRadius(dp(10));
        loginBtn.setBackground(lb);
        loginBtn.setMinHeight(0);
        loginBtn.setMinimumHeight(0);
        loginBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        bLp.setMargins(0, dp(8), 0, 0);
        loginBtn.setLayoutParams(bLp);
        card.addView(loginBtn);

        // Status
        statusTxt = new TextView(ctx);
        statusTxt.setText("");
        statusTxt.setTextColor(COLOR_MUTED);
        statusTxt.setTextSize(9.5f);
        statusTxt.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.setMargins(0, dp(6), 0, 0);
        statusTxt.setLayoutParams(sLp);
        card.addView(statusTxt);

        // Listeners
        showCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean b) {
                if (b) editPass.setTransformationMethod(
                        android.text.method.HideReturnsTransformationMethod.getInstance());
                else editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
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
                hideKeyboard(editUser);
                hideKeyboard(editPass);
                performLogin();
            }
        });

        // Restore saved credentials
        String u = save.getString("edittext1", "");
        String p = save.getString("edittext2", "");
        if (!u.isEmpty() && !p.isEmpty()) {
            editUser.setText(u);
            editPass.setText(p);
            rememberCb.setChecked(true);
        }

        // Update check — background thread (REST call)
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() { checkUpdateAsync(); }
        }, 300);

        return scroll;
    }

    private EditText makeInput(boolean isPassword) {
        EditText e = new EditText(ctx);
        e.setHintTextColor(COLOR_MUTED);
        e.setTextColor(COLOR_TEXT);
        e.setTextSize(12);
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
        e.setPadding(dp(9), dp(8), dp(9), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0B1122);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), COLOR_BORDER);
        e.setBackground(bg);
        return e;
    }

    private void attachKeyboardFix(final EditText et) {
        et.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.requestFocus();
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                InputMethodManager imm = (InputMethodManager)
                                        ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
                            } catch (Exception ignored) { }
                        }
                    }, 80);
                }
                return false;
            }
        });
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                InputMethodManager imm = (InputMethodManager)
                                        ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
                            } catch (Exception ignored) { }
                        }
                    }, 80);
                }
            }
        });
    }

    private void hideKeyboard(EditText et) {
        try {
            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && et != null) imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    private void setStatus(final String msg, final int color) {
        if (statusTxt == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                statusTxt.setText(msg);
                statusTxt.setTextColor(color);
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
    //  Update check — REST API in background thread
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
        box.setPadding(dp(18), dp(16), dp(18), dp(14));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), COLOR_ACCENT);
        box.setBackground(bg);

        TextView t = new TextView(ctx);
        t.setText("🚀 New Update Available");
        t.setTextColor(COLOR_ACCENT);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);

        TextView body = new TextView(ctx);
        String txt = "Current : " + getVersionName() + "\nLatest  : " + version;
        if (msg != null && !msg.isEmpty()) txt += "\n\n" + msg;
        body.setText(txt);
        body.setTextColor(COLOR_TEXT);
        body.setTextSize(12);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.setMargins(0, dp(10), 0, 0);
        box.addView(body, bLp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
        updateBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable ubg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0093C4, 0xFF6A47F5});
        ubg.setCornerRadius(dp(10));
        updateBtn.setBackground(ubg);
        updateBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
    //  Login — REST API in background thread
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
        loginBtn.setText("VERIFYING...");
        setStatus("⏳ Checking credentials...", COLOR_ACCENT);

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
                            loginBtn.setText("LOGIN");
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
                            loginBtn.setText("LOGIN");
                            setStatus("❌ Invalid username or password", COLOR_DANGER);
                        }
                    });
                    return;
                }

                // Check status / expiry
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
                            loginBtn.setText("LOGIN");
                            setStatus("⚠ Key expired or blocked", COLOR_DANGER);
                            showKeyExpiredDialog();
                        }
                    });
                    return;
                }

                // Save session
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
                        loginBtn.setText("LOGIN");
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
        box.setPadding(dp(18), dp(16), dp(18), dp(14));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), COLOR_DANGER);
        box.setBackground(bg);

        TextView t = new TextView(ctx);
        t.setText("🔒 Key Expired");
        t.setTextColor(COLOR_DANGER);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);

        TextView body = new TextView(ctx);
        body.setText("Your key has expired or account has been blocked.\n\n" +
                "Please contact seller to renew your subscription.");
        body.setTextColor(COLOR_TEXT);
        body.setTextSize(12);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.setMargins(0, dp(10), 0, 0);
        box.addView(body, bLp);

        Button contact = new Button(ctx);
        contact.setText("CONTACT");
        contact.setAllCaps(false);
        contact.setTextColor(Color.WHITE);
        contact.setTextSize(13);
        contact.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable cbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF00B489, 0xFF00F5A0});
        cbg.setCornerRadius(dp(20));
        contact.setBackground(cbg);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
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