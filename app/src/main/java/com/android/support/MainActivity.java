package com.android.support;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends Activity {

    // =================== GAME ===================
    public String GameActivity = "com.appsomniacs.da2.DA2Activity";
    public boolean hasLaunched = false;

    // =================== FIREBASE ===================
    private FirebaseDatabase _firebase;
    private FirebaseAuth Auth;
    private DatabaseReference update;
    private DatabaseReference User;

    // =================== STATE ===================
    private SharedPreferences save;   // "save"  → remember username/pass
    private SharedPreferences KEY;    // "KEY"   → logged-in user info
    private SharedPreferences dataSp; // "data"  → cached version

    private ArrayList<HashMap<String, Object>> UserMap = new ArrayList<>();

    private boolean loginInProgress = false;
    private boolean keyExpiredDialogShowing = false;
    private boolean updateDialogShowing = false;

    private String app_version = "";
    private String message = "";

    // =================== UI ===================
    private LinearLayout rootLayout;
    private EditText edittext1;   // username
    private EditText edittext2;   // password
    private CheckBox checkbox1;   // remember
    private CheckBox checkbox2;   // show pass
    private Button button1;       // login
    private TextView statusText;

    // =================== STYLE ===================
    private static final int COLOR_BG_TOP    = 0xFF111A36;
    private static final int COLOR_BG_BOTTOM = 0xFF080C19;
    private static final int COLOR_ACCENT    = 0xFF00E5FF;
    private static final int COLOR_ACCENT_2  = 0xFF7C4DFF;
    private static final int COLOR_SUCCESS   = 0xFF00F5A0;
    private static final int COLOR_DANGER    = 0xFFFF4D6D;
    private static final int COLOR_TEXT      = 0xFFF2F6FF;
    private static final int COLOR_MUTED     = 0xFF8194BE;
    private static final int COLOR_CARD      = 0xFF131B35;
    private static final int COLOR_BORDER    = 0xFF26335F;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen + status bar colour
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(0xFF000000);
        }

        FirebaseApp.initializeApp(this);
        _firebase = FirebaseDatabase.getInstance();
        update = _firebase.getReference("update");
        User   = _firebase.getReference("User");
        Auth   = FirebaseAuth.getInstance();

        save   = getSharedPreferences("save", MODE_PRIVATE);
        KEY    = getSharedPreferences("KEY", MODE_PRIVATE);
        dataSp = getSharedPreferences("data", MODE_PRIVATE);

        app_version = dataSp.getString("cached_app_version", "");

        buildLoginUI();
        loadSavedCredentials();

        // ১. Update check → ২. Login screen
        checkUpdate();
    }

    // ================================================================
    //                       LOGIN UI (programmatic)
    // ================================================================
    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private void buildLoginUI() {
        // Root gradient background
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER);
        rootLayout.setPadding(dp(24), dp(24), dp(24), dp(24));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        rootLayout.setBackground(bg);

        // Title
        TextView title = new TextView(this);
        title.setText("MODX LAB");
        title.setTextColor(COLOR_ACCENT);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) title.setLetterSpacing(0.08f);
        rootLayout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Premium Mod Menu — Sign In");
        sub.setTextColor(COLOR_MUTED);
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(4), 0, dp(28));
        sub.setLayoutParams(subLp);
        rootLayout.addView(sub);

        // Card container
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(22));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_CARD);
        cardBg.setCornerRadius(dp(18));
        cardBg.setStroke(dp(1), COLOR_BORDER);
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(18));
        card.setLayoutParams(cardLp);

        // Username
        TextView label1 = new TextView(this);
        label1.setText("USERNAME");
        label1.setTextColor(COLOR_MUTED);
        label1.setTextSize(10);
        label1.setTypeface(Typeface.DEFAULT_BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) label1.setLetterSpacing(0.15f);
        card.addView(label1);

        edittext1 = new EditText(this);
        edittext1.setHint("Enter your username");
        edittext1.setHintTextColor(COLOR_MUTED);
        edittext1.setTextColor(COLOR_TEXT);
        edittext1.setTextSize(14);
        edittext1.setSingleLine(true);
        edittext1.setInputType(InputType.TYPE_CLASS_TEXT);
        edittext1.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable input1 = new GradientDrawable();
        input1.setColor(0xFF0B1122);
        input1.setCornerRadius(dp(10));
        input1.setStroke(dp(1), COLOR_BORDER);
        edittext1.setBackground(input1);
        LinearLayout.LayoutParams e1Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        e1Lp.setMargins(0, dp(6), 0, dp(16));
        edittext1.setLayoutParams(e1Lp);
        card.addView(edittext1);

        // Password
        TextView label2 = new TextView(this);
        label2.setText("PASSWORD");
        label2.setTextColor(COLOR_MUTED);
        label2.setTextSize(10);
        label2.setTypeface(Typeface.DEFAULT_BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) label2.setLetterSpacing(0.15f);
        card.addView(label2);

        edittext2 = new EditText(this);
        edittext2.setHint("Enter your password");
        edittext2.setHintTextColor(COLOR_MUTED);
        edittext2.setTextColor(COLOR_TEXT);
        edittext2.setTextSize(14);
        edittext2.setSingleLine(true);
        edittext2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edittext2.setTransformationMethod(PasswordTransformationMethod.getInstance());
        edittext2.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable input2 = new GradientDrawable();
        input2.setColor(0xFF0B1122);
        input2.setCornerRadius(dp(10));
        input2.setStroke(dp(1), COLOR_BORDER);
        edittext2.setBackground(input2);
        LinearLayout.LayoutParams e2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        e2Lp.setMargins(0, dp(6), 0, dp(10));
        edittext2.setLayoutParams(e2Lp);
        card.addView(edittext2);

        // Checkboxes row
        LinearLayout cbRow = new LinearLayout(this);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbRow.setGravity(Gravity.CENTER_VERTICAL);
        cbRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        checkbox1 = new CheckBox(this);
        checkbox1.setText("Remember");
        checkbox1.setTextColor(COLOR_TEXT);
        checkbox1.setTextSize(11);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            checkbox1.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        LinearLayout.LayoutParams c1Lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        checkbox1.setLayoutParams(c1Lp);

        checkbox2 = new CheckBox(this);
        checkbox2.setText("Show");
        checkbox2.setTextColor(COLOR_TEXT);
        checkbox2.setTextSize(11);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            checkbox2.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));

        cbRow.addView(checkbox1);
        cbRow.addView(checkbox2);
        card.addView(cbRow);

        // Login button
        button1 = new Button(this);
        button1.setText("LOGIN");
        button1.setAllCaps(false);
        button1.setTextColor(Color.WHITE);
        button1.setTextSize(14);
        button1.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable btnBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0093C4, 0xFF6A47F5});
        btnBg.setCornerRadius(dp(12));
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x55FFFFFF), btnBg, null);
        button1.setBackground(ripple);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        bLp.setMargins(0, dp(14), 0, 0);
        button1.setLayoutParams(bLp);
        card.addView(button1);

        rootLayout.addView(card);

        // Status text
        statusText = new TextView(this);
        statusText.setText("");
        statusText.setTextColor(COLOR_MUTED);
        statusText.setTextSize(11);
        statusText.setGravity(Gravity.CENTER);
        rootLayout.addView(statusText);

        // Footer
        TextView footer = new TextView(this);
        footer.setText("© ModX Lab — All Rights Reserved");
        footer.setTextColor(COLOR_MUTED);
        footer.setTextSize(9);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fLp.setMargins(0, dp(20), 0, 0);
        footer.setLayoutParams(fLp);
        rootLayout.addView(footer);

        // Scroll wrapper
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(rootLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout root = new FrameLayout(this);
        root.addView(sc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Listeners
        checkbox2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean b) {
                if (b) edittext2.setTransformationMethod(
                        android.text.method.HideReturnsTransformationMethod.getInstance());
                else edittext2.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
        });

        checkbox1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton cb, boolean b) {
                if (b) {
                    save.edit().putString("edittext1", edittext1.getText().toString()).apply();
                    save.edit().putString("edittext2", edittext2.getText().toString()).apply();
                } else {
                    save.edit().remove("edittext1").apply();
                    save.edit().remove("edittext2").apply();
                }
            }
        });

        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { _login(); }
        });
    }

    private void loadSavedCredentials() {
        String u = save.getString("edittext1", "");
        String p = save.getString("edittext2", "");
        if (!u.isEmpty() && !p.isEmpty()) {
            edittext1.setText(u);
            edittext2.setText(p);
            checkbox1.setChecked(true);
        }
    }

    private void setStatus(String msg, int color) {
        if (statusText == null) return;
        statusText.setText(msg);
        statusText.setTextColor(color);
    }

    // ================================================================
    //                        UPDATE CHECK
    // ================================================================
    private String ModXLab() {
        try {
            android.content.pm.PackageInfo pinfo = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            return pinfo.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private void checkUpdate() {
        update.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    if (snapshot == null || !snapshot.exists()) {
                        // Update node নেই → সরাসরি login screen
                        return;
                    }
                    DataSnapshot up = snapshot.child("up");
                    if (!up.exists()) return;
                    Object v = up.child("version").getValue();
                    Object m = up.child("message").getValue();
                    if (v == null) return;

                    app_version = v.toString();
                    message = (m != null) ? m.toString() : "";

                    dataSp.edit().putString("cached_app_version", app_version).apply();

                    if (!ModXLab().equals(app_version)) {
                        showUpdateDialog(app_version, message);
                    }
                } catch (Exception e) {
                    Log.e("Mod_menu", "Update check: " + e);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) { }
        });
    }

    private void showUpdateDialog(String version, String msg) {
        if (updateDialogShowing) return;
        updateDialogShowing = true;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), COLOR_ACCENT);
        box.setBackground(bg);

        TextView t = new TextView(this);
        t.setText("🚀 New Update Available");
        t.setTextColor(COLOR_ACCENT);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);

        TextView body = new TextView(this);
        String txt = "Current : " + ModXLab() + "\nLatest  : " + version;
        if (msg != null && !msg.isEmpty()) txt += "\n\n" + msg;
        body.setText(txt);
        body.setTextColor(COLOR_TEXT);
        body.setTextSize(13);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.setMargins(0, dp(12), 0, 0);
        box.addView(body, bLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.setMargins(0, dp(18), 0, 0);
        box.addView(row, rLp);

        Button exitBtn = new Button(this);
        exitBtn.setText("Exit");
        exitBtn.setAllCaps(false);
        exitBtn.setTextColor(COLOR_MUTED);
        exitBtn.setTextSize(12);
        GradientDrawable exitBg = new GradientDrawable();
        exitBg.setColor(0x14FFFFFF);
        exitBg.setCornerRadius(dp(10));
        exitBtn.setBackground(exitBg);
        exitBtn.setPadding(dp(20), dp(10), dp(20), dp(10));

        Button updateBtn = new Button(this);
        updateBtn.setText("Update");
        updateBtn.setAllCaps(false);
        updateBtn.setTextColor(Color.WHITE);
        updateBtn.setTextSize(12);
        updateBtn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable updBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0093C4, 0xFF6A47F5});
        updBg.setCornerRadius(dp(10));
        updateBtn.setBackground(updBg);
        updateBtn.setPadding(dp(20), dp(10), dp(20), dp(10));
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uLp.setMargins(dp(8), 0, 0, 0);
        updateBtn.setLayoutParams(uLp);

        final AlertDialog[] ref = new AlertDialog[1];

        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
                finishAffinity();
            }
        });

        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse("https://t.me/kayesahmmedpro"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "Could not open link", Toast.LENGTH_SHORT).show();
                }
            }
        });

        row.addView(exitBtn);
        row.addView(updateBtn);

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setView(box);
        AlertDialog d = b.create();
        d.setCanceledOnTouchOutside(false);
        d.setCancelable(false);
        if (d.getWindow() != null)
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ref[0] = d;
        d.show();
    }

    // ================================================================
    //                          LOGIN LOGIC
    // ================================================================
    private void _login() {
        if (loginInProgress) return;

        // Internet check
        if (!isNetworkAvailable()) {
            setStatus("⚠ No internet connection", COLOR_DANGER);
            Toast.makeText(this, "Connection Error !", Toast.LENGTH_SHORT).show();
            return;
        }

        final String inputUser = edittext1.getText().toString().trim();
        final String inputPass = edittext2.getText().toString().trim();

        if (inputUser.isEmpty() || inputPass.isEmpty()) {
            setStatus("⚠ Please fill all fields", COLOR_DANGER);
            Toast.makeText(this, "Please Fill Details", Toast.LENGTH_SHORT).show();
            return;
        }

        loginInProgress = true;
        button1.setEnabled(false);
        button1.setText("VERIFYING...");
        setStatus("⏳ Checking credentials...", COLOR_ACCENT);

        // Save for remember
        save.edit().putString("edittext1", inputUser).apply();
        save.edit().putString("edittext2", inputPass).apply();

        // Firebase Realtime Database read
        User.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                loginInProgress = false;
                button1.setEnabled(true);
                button1.setText("LOGIN");

                if (isFinishing() || isDestroyed()) return;

                UserMap = new ArrayList<>();

                try {
                    GenericTypeIndicator<HashMap<String, Object>> ind =
                            new GenericTypeIndicator<HashMap<String, Object>>() {};
                    for (DataSnapshot d : dataSnapshot.getChildren()) {
                        HashMap<String, Object> m = d.getValue(ind);
                        if (m != null) UserMap.add(m);
                    }
                } catch (Exception e) {
                    setStatus("⚠ Database error", COLOR_DANGER);
                    return;
                }

                HashMap<String, Object> matched = null;
                for (int i = 0; i < UserMap.size(); i++) {
                    try {
                        Object u = UserMap.get(i).get("user");
                        Object p = UserMap.get(i).get("pass");
                        if (u == null || p == null) continue;
                        if (inputUser.equals(u.toString()) && inputPass.equals(p.toString())) {
                            matched = UserMap.get(i);
                            break;
                        }
                    } catch (Exception ignored) { }
                }

                if (matched == null) {
                    setStatus("❌ Invalid username or password", COLOR_DANGER);
                    Toast.makeText(MainActivity.this,
                            "Invalid Username or Password!", Toast.LENGTH_SHORT).show();
                    return;
                }

                Object statusObj = matched.get("status");
                Object timeObj   = matched.get("time");
                if (statusObj == null || timeObj == null) {
                    setStatus("⚠ Account data missing", COLOR_DANGER);
                    return;
                }

                boolean expired = false;
                try {
                    long expireTime  = (long) Double.parseDouble(timeObj.toString());
                    long currentTime = System.currentTimeMillis();
                    if (currentTime > expireTime) expired = true;
                } catch (Exception ignored) { }

                // ── KEY EXPIRED / BANNED ──
                if (!statusObj.toString().equals("true") || expired) {
                    setStatus("⚠ Key expired or blocked", COLOR_DANGER);
                    showKeyExpiredDialog();
                    return;
                }

                // ── SAVE SESSION ──
                try {
                    KEY.edit().putString("User",     matched.get("user").toString()).apply();
                    KEY.edit().putString("Status",   matched.get("status").toString()).apply();
                    KEY.edit().putString("Register", matched.get("rgtime").toString()).apply();
                    KEY.edit().putString("time",     matched.get("time").toString()).apply();
                    KEY.edit().putString("Valid",    matched.get("Validity").toString()).apply();
                    KEY.edit().putString("key",      matched.get("key").toString()).apply();
                } catch (Exception e) {
                    setStatus("⚠ Session save failed", COLOR_DANGER);
                    return;
                }

                setStatus("✅ Login successful", COLOR_SUCCESS);
                Toast.makeText(MainActivity.this, "Login Success", Toast.LENGTH_SHORT).show();

                // Anonymous Firebase Auth (optional — keeps session alive)
                Auth.signInAnonymously().addOnCompleteListener(
                        new OnCompleteListener<AuthResult>() {
                            @Override public void onComplete(@NonNull Task<AuthResult> task) { }
                        });

                // Small delay → show success dialog → launch game
                new Handler().postDelayed(new Runnable() {
                    @Override public void run() { showSuccessDialog(); }
                }, 400);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                loginInProgress = false;
                button1.setEnabled(true);
                button1.setText("LOGIN");
                setStatus("⚠ Connection error", COLOR_DANGER);
                Toast.makeText(MainActivity.this,
                        "Connection Error !", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return true; }
    }

    // ================================================================
    //                    KEY EXPIRED DIALOG
    // ================================================================
    private void showKeyExpiredDialog() {
        if (keyExpiredDialogShowing) return;
        keyExpiredDialogShowing = true;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), COLOR_DANGER);
        box.setBackground(bg);

        TextView t = new TextView(this);
        t.setText("🔒 Key Expired");
        t.setTextColor(COLOR_DANGER);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);

        TextView body = new TextView(this);
        body.setText("Your key has expired or your account has been blocked.\n\n" +
                "Please contact the seller to renew your subscription.");
        body.setTextColor(COLOR_TEXT);
        body.setTextSize(13);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.setMargins(0, dp(12), 0, 0);
        box.addView(body, bLp);

        Button contact = new Button(this);
        contact.setText("CONTACT");
        contact.setAllCaps(false);
        contact.setTextColor(Color.WHITE);
        contact.setTextSize(13);
        contact.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable cbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF00B489, 0xFF00F5A0});
        cbg.setCornerRadius(dp(24));
        contact.setBackground(cbg);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        cLp.setMargins(0, dp(18), 0, 0);
        contact.setLayoutParams(cLp);
        box.addView(contact);

        final AlertDialog[] ref = new AlertDialog[1];

        contact.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                keyExpiredDialogShowing = false;
                if (ref[0] != null) ref[0].dismiss();
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/kayesahmmedpro"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "Could not open Telegram!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setView(box);
        AlertDialog d = b.create();
        d.setCanceledOnTouchOutside(false);
        d.setCancelable(true);
        d.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface di) {
                keyExpiredDialogShowing = false;
                finishAffinity();
            }
        });
        if (d.getWindow() != null)
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ref[0] = d;
        d.show();
    }

    // ================================================================
    //                    SUCCESS DIALOG → START
    // ================================================================
    private void showSuccessDialog() {
        String user     = KEY.getString("User", "");
        String register = KEY.getString("Register", "");
        String valid    = KEY.getString("Valid", "");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(20), dp(22), dp(18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), COLOR_SUCCESS);
        box.setBackground(bg);

        TextView t = new TextView(this);
        t.setText("✅ Login Success");
        t.setTextColor(COLOR_SUCCESS);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);

        TextView body = new TextView(this);
        body.setText("User        : " + user + "\n" +
                     "Registered  : " + register + "\n" +
                     "Valid Till  : " + valid + "\n" +
                     "Seller      : ModX Lab\n" +
                     "Status      : Activated");
        body.setTextColor(COLOR_TEXT);
        body.setTextSize(12);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.setMargins(0, dp(12), 0, 0);
        box.addView(body, bLp);

        Button ok = new Button(this);
        ok.setText("CONTINUE");
        ok.setAllCaps(false);
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(13);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable obg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0093C4, 0xFF6A47F5});
        obg.setCornerRadius(dp(24));
        ok.setBackground(obg);
        LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        oLp.setMargins(0, dp(18), 0, 0);
        ok.setLayoutParams(oLp);
        box.addView(ok);

        final AlertDialog[] ref = new AlertDialog[1];
        ok.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
                launchGameAndMenu();
            }
        });

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setView(box);
        AlertDialog d = b.create();
        d.setCanceledOnTouchOutside(false);
        d.setCancelable(false);
        if (d.getWindow() != null)
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        ref[0] = d;
        d.show();
    }

    // ================================================================
    //                    LAUNCH GAME + FLOATING MENU
    // ================================================================
    private void launchGameAndMenu() {
        if (!hasLaunched) {
            hasLaunched = true;
            try {
                startActivity(new Intent(MainActivity.this,
                        Class.forName(GameActivity)));
            } catch (ClassNotFoundException e) {
                Log.e("Mod_menu", "Game activity not found: " + GameActivity);
            }
        }
        // Start floating menu (which asks overlay permission via native)
        Main.Start(this);
    }
}
