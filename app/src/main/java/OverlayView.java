package com.example.intelligentcameraapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends AppCompatImageView {
    private List<Detection> detections = new ArrayList<>();

    public OverlayView(@NonNull Context context) {
        super(context);
    }

    public OverlayView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setDetections(List<Detection> detections) {
        this.detections = detections;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (detections != null) {
            for (Detection detection : detections) {
                Paint paint = new Paint();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(8);
                paint.setColor(detection.getColor());
                canvas.drawRect(detection.getBbox(), paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(48);
                canvas.drawText(detection.getLabel(), detection.getBbox().left, detection.getBbox().top, paint);
            }
        }
    }
}
