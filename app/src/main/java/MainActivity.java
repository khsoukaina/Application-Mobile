package com.example.intelligentcameraapp;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.util.Size;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.app.Dialog;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.Button;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.util.AttributeSet;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.impl.CameraCaptureCallback;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private Module module;
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private TextView resultTextView;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;

    private static final float[] NO_MEAN_RGB = {0.0f, 0.0f, 0.0f};
    private static final float[] NO_STD_RGB = {1.0f, 1.0f, 1.0f};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called");
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        resultTextView = findViewById(R.id.resultTextView);
        cameraExecutor = Executors.newFixedThreadPool(2);  // Use two threads for background tasks

        Button captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(v -> capturePhoto());

        try {
            module = LiteModuleLoader.load(assetFilePath("model.ptl"));
        } catch (IOException e) {
            Toast.makeText(this, "Error loading model: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Failed to get camera provider: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Failed to get camera provider.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .build();


        ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(new Size(640, 480)) // Reduce resolution for faster processing
                .build();
        imageAnalyzer.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            @ExperimentalGetImage
            public void analyze(@NonNull ImageProxy image) {
                analyzeImage(image);
            }
        });

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer);
            Log.d(TAG, "Camera use cases bound successfully.");
        } catch (Exception e) {
            Toast.makeText(this, "Failed to bind use cases: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to bind use cases.", e);
        }
    }

    private void capturePhoto() {
        if (imageCapture == null) {
            return;
        }

        // Récupérez le résultat actuellement affiché
        String result = resultTextView.getText().toString();

        // Créez les options de fichier de sortie avec le résultat
        ImageCapture.OutputFileOptions outputOptions = createOutputFileOptions(result);

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Uri savedUri = outputFileResults.getSavedUri();
                if (savedUri != null) {
                    showImagePreviewDialog(savedUri);
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(MainActivity.this, "Photo capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        Log.d(TAG, "capturePhoto called");
    }



    private ImageCapture.OutputFileOptions createOutputFileOptions(String result) {

        String sanitizedResult = result.replace(" ", "_").toLowerCase();

        String fileName = sanitizedResult + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SYLENS");

        return new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build();
    }


    private void showImagePreviewDialog(Uri imageUri) {
        // Create a new dialog
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_image_preview);

        // Get the ImageView from the dialog
        ImageView previewImageView = dialog.findViewById(R.id.previewImageView);

        // Load the image into the ImageView
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
            previewImageView.setImageBitmap(bitmap);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        // Set up Save button
        Button saveButton = dialog.findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Photo saved: " + imageUri.toString(), Toast.LENGTH_SHORT).show();
            dialog.dismiss();  // Close the dialog
        });

        // Set up Discard button
        Button discardButton = dialog.findViewById(R.id.discardButton);
        discardButton.setOnClickListener(v -> {
            // Remove the saved image
            getContentResolver().delete(imageUri, null, null);
            Toast.makeText(MainActivity.this, "Photo discarded", Toast.LENGTH_SHORT).show();
            dialog.dismiss();  // Close the dialog
        });

        // Show the dialog
        dialog.show();
    }

    @OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void analyzeImage(@NonNull ImageProxy image) {
        Image mediaImage = image.getImage();
        if (mediaImage != null) {
            Bitmap bitmap = BitmapUtils.imageToBitmap(mediaImage);
            if (bitmap != null) {
                runModelInference(bitmap);
            }
        }
        image.close();
    }

    private void runModelInference(Bitmap bitmap) {
        if (module == null) {
            Toast.makeText(this, "Model is not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        // Resize bitmap for faster processing (if needed)
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

        Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, NO_MEAN_RGB, NO_STD_RGB);

        IValue[] outputTuple = module.forward(IValue.from(inputTensor)).toTuple();
        Tensor pyloneTensor = outputTuple[0].toTensor();
        Tensor antenneTensor = outputTuple[1].toTensor();
        Tensor fhTensor = outputTuple[2].toTensor();

        float[] pyloneScores = pyloneTensor.getDataAsFloatArray();
        float[] antenneScores = antenneTensor.getDataAsFloatArray();
        float[] fhScores = fhTensor.getDataAsFloatArray();

        Log.d(TAG, "Pylone Scores: " + Arrays.toString(pyloneScores));
        Log.d(TAG, "Antenne Scores: " + Arrays.toString(antenneScores));
        Log.d(TAG, "FH Scores: " + Arrays.toString(fhScores));

        // Process the model output to determine OK or NOK status for each component
        String pyloneStatus = pyloneScores[1] > pyloneScores[0] ? "Pylone OK" : "Pylone NOK";
        String antenneStatus = antenneScores[1] > antenneScores[0] ? "Antenne OK" : "Antenne NOK";
        String fhStatus = fhScores[1] > fhScores[0] ? "FH OK" : "FH NOK";

        String result = pyloneStatus + "\n" + antenneStatus + "\n" + fhStatus;
        runOnUiThread(() -> resultTextView.setText(result));
    }

    private String assetFilePath(String assetName) throws IOException {
        File file = new File(getFilesDir(), assetName);
        try (FileOutputStream os = new FileOutputStream(file);
             java.io.InputStream is = getAssets().open(assetName)) {
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
        return file.getAbsolutePath();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
