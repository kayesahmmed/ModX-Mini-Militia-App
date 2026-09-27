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

public class LoginHelper {

    public interface Callback { void onLoginSuccess(); }
    public interface CheckListener { void onChanged(boolean checked); }

    private static final String TAG = "LoginHelper";

    private static final int COLOR_BG_1       = Color.parseColor("#0C0E14");
    private static final int COLOR_CARD       = Color.parseColor("#181B24");
    private static final int COLOR_BORDER     = Color.parseColor("#2E313C");
    private static final int COLOR_FIELD_BG   = Color.parseColor("#14161F");
    private static final int COLOR_FIELD_BORD = Color.parseColor("#2A2D38");
    private static final int COLOR_ACCENT     = Color.parseColor("#D8B36C");
    private static final int COLOR_ACCENT_HI  = Color.parseColor("#F1DFAE");
    private static final int COLOR_SUCCESS    = Color.parseColor("#4FBA82");
    private static final int COLOR_DANGER     = Color.parseColor("#C25C56");
    private static final int COLOR_WARN       = Color.parseColor("#E0A94D");
    private static final int COLOR_TEXT       = Color.parseColor("#ECE8DF");
    private static final int COLOR_TEXT_MUTED = Color.parseColor("#8D8F99");
    private static final int COLOR_HINT       = Color.parseColor("#5C5F6A");
    private static final int COLOR_ON_ACCENT  = Color.parseColor("#14161F");

    private static final int COLOR_DIALOG_BG     = Color.parseColor("#1D2029");
    private static final int COLOR_DIALOG_TITLE  = Color.parseColor("#ECE8DF");
    private static final int COLOR_DIALOG_SUB    = Color.parseColor("#A7A9B2");
    private static final int COLOR_DIALOG_SUB2   = Color.parseColor("#7D7F89");
    private static final int COLOR_DIALOG_BODY   = Color.parseColor("#C6C8D0");
    private static final int COLOR_CTA           = Color.parseColor("#D8B36C");
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
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
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
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
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
            @Override public void onChanged(boolean checked) {
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
            @Override public void onChanged(boolean checked) {
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
            @Override public void onClick(View v) {
                v.animate().scaleX(0.975f).scaleY(0.975f).setDuration(100)
                        .withEndAction(new Runnable() {
                            @Override public void run() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) tv.setLetterSpacing(0.12f);
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
            @Override public void onFocusChange(View v, boolean hasFocus) {
                final GradientDrawable bg = (GradientDrawable) box.getBackground();
                int fromColor = hasFocus ? COLOR_FIELD_BORD : COLOR_ACCENT;
                int toColor   = hasFocus ? COLOR_ACCENT      : COLOR_FIELD_BORD;
                ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
                va.setDuration(200);
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator a) {
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
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.requestFocus();
                    v.postDelayed(new Runnable() {
                        @Override public void run() { forceShowKeyboard(et); }
                    }, 80);
                }
                return false;
            }
        });
        et.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.postDelayed(new Runnable() {
                    @Override public void run()