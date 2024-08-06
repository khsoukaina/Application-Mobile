package com.example.intelligentcameraapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class BitmapUtils {
    public static Bitmap imageToBitmap(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Unsupported image format");
        }

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);

        int uvPixelStride = image.getPlanes()[1].getPixelStride();
        int uvRowStride = image.getPlanes()[1].getRowStride();
        int uvBufferSize = uBuffer.remaining();

        for (int i = 0; i < uvBufferSize; i += uvPixelStride) {
            int offset = ySize + i / uvPixelStride;
            nv21[offset] = uBuffer.get(i);
            nv21[offset + 1] = vBuffer.get(i);
        }

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }
}
