package com.android.support;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

public class TitanicTextView extends TextView {

    private AnimationSetupCallback animationSetupCallback;
    private float maskX, maskY;
    private boolean sinking;
    private boolean setUp;
    private BitmapShader shader;
    private Matrix shaderMatrix;
    private Drawable wave;
    private float offsetY;

    public TitanicTextView(Context context) {
        super(context);
        init();
    }

    public TitanicTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TitanicTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        shaderMatrix = new Matrix();
    }

    public AnimationSetupCallback getAnimationSetupCallback() {
        return animationSetupCallback;
    }

    public void setAnimationSetupCallback(AnimationSetupCallback animationSetupCallback) {
        this.animationSetupCallback = animationSetupCallback;
    }

    public float getMaskX() {
        return maskX;
    }

    public void setMaskX(float maskX) {
        this.maskX = maskX;
        invalidate();
    }

    public float getMaskY() {
        return maskY;
    }

    public void setMaskY(float maskY) {
        this.maskY = maskY;
        invalidate();
    }

    public boolean isSinking() {
        return sinking;
    }

    public void setSinking(boolean sinking) {
        this.sinking = sinking;
    }

    public boolean isSetUp() {
        return setUp;
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        createShader();
    }

    @Override
    public void setTextColor(android.content.res.ColorStateList colors) {
        super.setTextColor(colors);
        createShader();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        createShader();
        if (!setUp) {
            setUp = true;
            if (animationSetupCallback != null) {
                animationSetupCallback.onSetupAnimation(TitanicTextView.this);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void createShader() {
        // ✅ [FIX]: wave রিসোর্স লোড করার সময় সব এরর ক্যাচ করা
        if (wave == null) {
            try {
                wave = getResources().getDrawable(R.drawable.wave);
            } catch (Exception e) {
                Log.e("Titanic", "Wave drawable not found! Titanic animation disabled.");
                getPaint().setShader(null);
                return;
            }
        }
        try {
            int waveW = wave.getIntrinsicWidth();
            int waveH = wave.getIntrinsicHeight();

            if (waveW <= 0 || waveH <= 0) {
                return;
            }

            Bitmap b = Bitmap.createBitmap(waveW, waveH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(getCurrentTextColor());
            wave.setBounds(0, 0, waveW, waveH);
            wave.draw(c);
            shader = new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
            getPaint().setShader(shader);
            offsetY = (getHeight() - waveH) / 2;
        } catch (Exception e) {
            Log.e("Titanic", "Error setting up shader: " + e.getMessage());
            getPaint().setShader(null);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (sinking && shader != null) {
            if (getPaint().getShader() == null) {
                getPaint().setShader(shader);
            }
            shaderMatrix.setTranslate(maskX, maskY + offsetY);
            shader.setLocalMatrix(shaderMatrix);
        } else {
            getPaint().setShader(null);
        }
        super.onDraw(canvas);
    }
}