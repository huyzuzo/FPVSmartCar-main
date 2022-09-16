package com.example.tensorflow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Size;
import android.util.TypedValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.example.tensorflow.customview.OverlayView;
import com.example.tensorflow.customview.OverlayView.DrawCallback;
import com.example.tensorflow.env.BorderedText;
import com.example.tensorflow.env.ImageUtils;
import com.example.tensorflow.customview.tracking.MultiBoxTracker;
import com.example.tensorflow.ml.Detect;
import com.example.tensorflow.ml.Detect.DetectionResult;
import com.example.tensorflow.ml.Detect.Outputs;

import org.tensorflow.lite.support.image.TensorImage;

public class Detector {
    private Detect model;

    private static final int TF_OD_API_INPUT_SIZE = 300;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(1794, 1080);
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final float TEXT_SIZE_DIP = 10;

    private Context context;
    private OverlayView trackingOverlay;

    private int previewWidth;
    private int previewHeight;
    private int sensorOrientation = 0;

    private Bitmap croppedBitmap = null;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private BorderedText borderedText;


    public Detector(Context context, OverlayView trackingOverlay){
        this.context = context;
        this.trackingOverlay = trackingOverlay;
    }

    public void onPreviewSizeChosen() {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);
        tracker = new MultiBoxTracker(context);

        try{
            model = Detect.newInstance(context);
        } catch (IOException e) {
            e.printStackTrace();
        }

        previewWidth = DESIRED_PREVIEW_SIZE.getWidth();
        previewHeight = DESIRED_PREVIEW_SIZE.getHeight();

        croppedBitmap = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    protected void processImage(Bitmap rgbFrameBitmap) {
        MainActivity.computingDetection = true;

        trackingOverlay.postInvalidate();
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        TensorImage image = TensorImage.fromBitmap(croppedBitmap);
        Outputs outputs = model.process(image);

        final List<DetectionResult> results = outputs.getDetectionResultList();


        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(2.0f);

        final List<DetectionResult> mappedRecognitions =
                new ArrayList<DetectionResult>();

        for (final DetectionResult result : results) {
            final RectF location = result.getLocationAsRectF();
            if (location != null && result.getScoreAsFloat() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                canvas.drawRect(location, paint);

                cropToFrameTransform.mapRect(location);

                mappedRecognitions.add(result);
            }
        }

        tracker.trackResults(mappedRecognitions);
        trackingOverlay.postInvalidate();

        MainActivity.computingDetection = false;
    }
}
