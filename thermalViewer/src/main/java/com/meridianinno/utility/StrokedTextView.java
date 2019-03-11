package com.meridianinno.utility;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by cchsu20 on 11/01/2018.
 */

public class StrokedTextView extends TextView{
    private TextView strokedText = null;

    public StrokedTextView(Context context){
        super(context);
        strokedText = new TextView(context);
        init();
    }

    public StrokedTextView(Context context, AttributeSet attr){
        super(context, attr);
        strokedText = new TextView(context, attr);
        init();
    }

    public void init(){
        TextPaint textPaint = strokedText.getPaint();
        textPaint.setStrokeWidth(10);
        textPaint.setStyle(Paint.Style.STROKE);
        strokedText.setTextColor(0xFFFFFFFF);
        strokedText.setGravity(getGravity());
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        super.setLayoutParams(params);
        strokedText.setLayoutParams(params);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        CharSequence text = strokedText.getText();

        if (text == null || !text.equals(this.getText())){
            strokedText.setText(getText());
            this.postInvalidate();
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        strokedText.measure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        strokedText.layout(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        strokedText.draw(canvas);
        super.onDraw(canvas);
    }
}
