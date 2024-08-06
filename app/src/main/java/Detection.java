package com.example.intelligentcameraapp;

import android.graphics.RectF;

public class Detection {
    private RectF bbox;
    private String label;
    private int color;

    public Detection(RectF bbox, String label, int color) {
        this.bbox = bbox;
        this.label = label;
        this.color = color;
    }

    public RectF getBbox() {
        return bbox;
    }

    public String getLabel() {
        return label;
    }

    public int getColor() {
        return color;
    }
}
