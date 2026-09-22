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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class LoginHelper {

    public interface Callback {
        void onLoginSuccess();
    }

    // Colors
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
    private final DatabaseReference updateRef;
    private final DatabaseReference userRef;
    private final FirebaseAuth auth;

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
        this.updateRef = FirebaseDatabase.getInstance().getReference("update");
        this.userRef = FirebaseDatabase.getInstance().getReference("User");
        this.auth = FirebaseAuth.getInstance();
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    // ================================================================
    //  Build the login view (to be embedded in floating menu)
    // ================================================================
    public View buildView() {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setBackground(buildBackground());
        scroll.setClickable(true);
        scroll.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(0, dp(10), 0, dp(10));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(ctx);
        title.setText("MODX LAB");
        title.setTextColor(COLOR_ACCENT);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(ctx);
        sub.setText("Sign In to Continue");
        sub.setTextColor(COLOR_MUTED);
        sub.setTextSize(10);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(3), 0, dp(16));
        sub.setLayoutParams(subLp);
        root.addView(sub);

        // Card
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_CARD);
        cardBg.setCornerRadius(dp(14));
        cardBg.setStroke(dp(1), COLOR_BORDER);
        card.setBackground(cardBg);

        // Username
        TextView l1 = new TextView(ctx);
        l1.setText("USERNAME");
        l1.setTextColor(COLOR_MUTED);
        l1.setTextSize(9);
        l1.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(l1);

        editUser = new EditText(ctx);
        editUser.setHint("Enter username");
        editUser.setHintTextColor(COLOR_MUTED);
        editUser.setTextColor(COLOR_TEXT);
        editUser.setTextSize(13);
        editUser.setSingleLine(true);
        editUser.setInputType(InputType.TYPE_CLASS_TEXT);
        editUser.setPadding(dp(10), dp(9), dp(10), dp(9));
        GradientDrawable in1 = new GradientDrawable();
        in1.setColor(0xFF0B1122);
        in1.setCornerRadius(dp(8));
        in1.setStroke(dp(1), COLOR_BORDER);
        editUser.setBackground(in1);
        LinearLayout.LayoutParams e1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        e1.setMargins(0, dp(4), 0, dp(10));
        editUser.setLayoutParams(e1);
        card.addView(editUser);

        // Password
        TextView l2 = new TextView(ctx);
        l2.setText("PASSWORD");
        l2.setTextColor(COLOR_MUTED);
        l2.setTextSize(9);
        l2.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(l2);

        editPass = new EditText(ctx);
        editPass.setHint("Enter password");
        editPass.setHintTextColor(COLOR_MUTED);
        editPass.setTextColor(COLOR_TEXT);
        editPass.setTextSize(13);
        editPass.setSingleLine(true);
        editPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
        editPass.setPadding(dp(10), dp(9), dp(10), dp(9));
        GradientDrawable in2 = new GradientDrawable();
        in2.setColor(0xFF0B1122);
        in2.setCornerRadius(dp(8));
        in2.setStroke(dp(1), COLOR_BORDER);
        editPass.setBackground(in2);
        LinearLayout.LayoutParams e2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        e2.setMargins(0, dp(4), 0, dp(6));
        editPass.setLayoutParams(e2);
        card.addView(editPass);

        // Checkboxes
        LinearLayout cbRow = new LinearLayout(ctx);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbRow.setGravity(Gravity.CENTER_VERTICAL);

        rememberCb = new CheckBox(ctx);
        rememberCb.setText("Remember");
        rememberCb.setTextColor(COLOR_TEXT);
        rememberCb.setTextSize(10);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            rememberCb.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }
        rememberCb.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        showCb = new CheckBox(ctx);
        showCb.setText("Show");
        showCb.setTextColor(COLOR_TEXT);
        showCb.setTextSize(10);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            showCb.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }

        cbRow.addView(rememberCb);
        cbRow.addView(showCb);
        card.addView(cbRow);

        // Login button
        loginBtn = new Button(ctx);
        loginBtn.setText("LOGIN");
        loginBtn.setAllCaps(false);
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setTextSize(13);
        loginBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable lb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0093C4, 0xFF6A47F5});
        lb.setCornerRadius(dp(10));
        loginBtn.setBackground(lb);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        bLp.setMargins(0, dp(10), 0, 0);
        loginBtn.setLayoutParams(bLp);
        card.addView(loginBtn);

        root.addView(card);

        statusTxt = new TextView(ctx);
        statusTxt.setText("");
        statusTxt.setTextColor(COLOR_MUTED);
        statusTxt.setTextSize(10);
        statusTxt.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.setMargins(0, dp(10), 0, 0);
        statusTxt.setLayoutParams(sLp);
        root.addView(statusTxt);

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
            public void onClick(View v) { performLogin(); }
        });

        // Load saved credentials
        String u = save.getString("edittext1", "");
        String p = save.getString("edittext2", "");
        if (!u.isEmpty() && !p.isEmpty()) {
            editUser.setText(u);
            editPass.setText(p);
            rememberCb.setChecked(true);
        }

        // Check for updates on Firebase after UI ready
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() { checkUpdate(); }
        }, 150);

        return scroll;
    }

    private GradientDrawable buildBackground() {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        bg.setCornerRadius(dp(12));
        return bg;
    }

    private void setStatus(String msg, int color) {
        if (statusTxt == null) return;
        statusTxt.setText(msg);
        statusTxt.setTextColor(color);
    }

    private String getVersionName() {
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) { return ""; }
    }

    // ================================================================
    //  Update check
    // ================================================================
    private void checkUpdate() {
        updateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    if (snapshot == null || !snapshot.exists()) return;
                    DataSnapshot up = snapshot.child("up");
                    if (!up.exists()) return;
                    Object v = up.child("version").getValue();
                    if (v == null) return;
                    String remoteVer = v.toString();
                    Object msgObj = up.child("message").getValue();
                    String msg = (msgObj != null) ? msgObj.toString() : "";

                    if (!getVersionName().equals(remoteVer)) {
                        showUpdateDialog(remoteVer, msg);
                    }
                } catch (Exception e) {
                    android.util.Log.e("LoginHelper", "Update check failed: " + e);
                }
            }
            @Override public void onCancelled(DatabaseError error) { }
        });
    }

    private void showUpdateDialog(String version, String msg) {
        if (!(ctx instanceof android.app.Activity)) return;

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(14));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
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
    //  Login logic
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

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                loginInProgress = false;
                loginBtn.setEnabled(true);
                loginBtn.setText("LOGIN");

                ArrayList<HashMap<String, Object>> userMap = new ArrayList<>();
                try {
                    GenericTypeIndicator<HashMap<String, Object>> ind =
                            new GenericTypeIndicator<HashMap<String, Object>>() {};
                    for (DataSnapshot d : dataSnapshot.getChildren()) {
                        HashMap<String, Object> m = d.getValue(ind);
                        if (m != null) userMap.add(m);
                    }
                } catch (Exception e) {
                    setStatus("⚠ Database error", COLOR_DANGER);
                    return;
                }

                HashMap<String, Object> matched = null;
                for (int i = 0; i < userMap.size(); i++) {
                    Object u = userMap.get(i).get("user");
                    Object p = userMap.get(i).get("pass");
                    if (u == null || p == null) continue;
                    if (inputUser.equals(u.toString()) && inputPass.equals(p.toString())) {
                        matched = userMap.get(i);
                        break;
                    }
                }

                if (matched == null) {
                    setStatus("❌ Invalid username or password", COLOR_DANGER);
                    return;
                }

                Object statusObj = matched.get("status");
                Object timeObj = matched.get("time");
                if (statusObj == null || timeObj == null) {
                    setStatus("⚠ Account data missing", COLOR_DANGER);
                    return;
                }

                boolean expired = false;
                try {
                    long expireTime = (long) Double.parseDouble(timeObj.toString());
                    long currentTime = System.currentTimeMillis();
                    if (currentTime > expireTime) expired = true;
                } catch (Exception ignored) { }

                if (!statusObj.toString().equals("true") || expired) {
                    setStatus("⚠ Key expired or blocked", COLOR_DANGER);
                    showKeyExpiredDialog();
                    return;
                }

                try {
                    KEY.edit().putString("User", matched.get("user").toString()).apply();
                    KEY.edit().putString("Status", matched.get("status").toString()).apply();
                    KEY.edit().putString("Register", matched.get("rgtime").toString()).apply();
                    KEY.edit().putString("time", matched.get("time").toString()).apply();
                    KEY.edit().putString("Valid", matched.get("Validity").toString()).apply();
                    KEY.edit().putString("key", matched.get("key").toString()).apply();
                } catch (Exception e) {
                    setStatus("⚠ Session save failed", COLOR_DANGER);
                    return;
                }

                setStatus("✅ Login successful", COLOR_SUCCESS);
                Toast.makeText(ctx, "Login Success", Toast.LENGTH_SHORT).show();

                auth.signInAnonymously();

                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onLoginSuccess();
                    }
                }, 700);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                loginInProgress = false;
                loginBtn.setEnabled(true);
                loginBtn.setText("LOGIN");
                setStatus("⚠ Connection error", COLOR_DANGER);
            }
        });
    }

    // ================================================================
    //  Key expired dialog
    // ================================================================
    private void showKeyExpiredDialog() {
        if (keyExpiredDialogShowing) return;
        keyExpiredDialogShowing = true;

        if (!(ctx instanceof android.app.Activity)) return;

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(14));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
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