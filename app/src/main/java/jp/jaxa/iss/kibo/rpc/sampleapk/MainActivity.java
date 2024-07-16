package jp.jaxa.iss.kibo.rpc.sampleapk;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.content.res.AssetManager;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jp.jaxa.iss.kibo.rpc.sampleapk.YourService;

public class MainActivity extends AppCompatActivity{
    private static final String TAG = MainActivity.class.getSimpleName();
    private List<String> imageFileNames;
    private AssetManager assetManager;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "Test asdfasdf");

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed.");
        } else {
            Log.d(TAG, "OpenCV initialization succeeded.");
        }
        run();
    }

    private void run(){
        loadImages();
        Log.e(TAG, "run: Loaded image filenames: " + imageFileNames);
        // Pattern matching
        Mat[] templates = loadTemplateImages(imageFileNames);
//        int[] templateMatchCnt = getNumTemplateMatch(templates, image);
    }

    private void loadImages() {
        // Retrieve image file names from assets
        try {
            String[] assetFileNames = getAssets().list("images");
            imageFileNames = new ArrayList<>(Arrays.asList(assetFileNames));

            // Remove files
            imageFileNames.removeAll(Arrays.asList("android-logo-mask.png", "android-logo-shine.png", "clock64.png"));

            Log.e(TAG, "Loaded " + imageFileNames.size() + " image filenames: " + imageFileNames); //+ Arrays.toString(imageFileNames));
        } catch (IOException e) {
//            imageFileNames = new String[0]; // Initialize empty array in case of exception
            Log.e(TAG, "Error loading image filenames from assets", e);
            e.printStackTrace();
        }
    }

    private Mat[] loadTemplateImages(List<String> imageFileNames) {
        Log.e(TAG, "test loadTemplateImages");
//        Log.e(TAG, "loadTemplateImages: image filenames: " + imageFileNames);

        Mat[] templates = new Mat[imageFileNames.size()];
        for (int i = 0; i < imageFileNames.size(); i++) {
            String fileName = imageFileNames.get(i);
//            if (!fileName.endsWith(".png")) {
//                continue;
//            }
            try {
                // Open template image file in Bitmap from the filename and convert to Mat
                Log.e(TAG, "Loading template: " + fileName);

                // Load image from asset into bitmap
                InputStream inputStream = getAssets().open("images/" + imageFileNames.get(i));
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                // Convert bitmap to Mat
                Mat mat = new Mat();
                Utils.bitmapToMat(bitmap, mat);

                // Convert to grayscale
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);

                templates[i] = mat; // Assign template image to array
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return templates;
    }



}

