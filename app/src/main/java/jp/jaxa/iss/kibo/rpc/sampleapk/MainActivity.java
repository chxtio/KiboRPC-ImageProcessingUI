package jp.jaxa.iss.kibo.rpc.sampleapk;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private List<String> imageFileNames;
    private List<String> mainImageFileNames;
    private Mat mainImage;
    private List<Mat> templates = new ArrayList<>();
    private RecyclerView recyclerView;
    private TemplateAdapter templateAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "Test asdfasdf");

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed.");
        } else {
            Log.d(TAG, "OpenCV initialization succeeded.");
        }

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.templatesRecyclerView);
//        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3)); // 3 columns
//        templateAdapter = new TemplateAdapter(templates); // Pass templates list
        // Load images and templates

        loadImages(); // Ensure this method populates imageFileNames
        mainImage = new Mat();
        loadMainImage();
        Mat[] templateMats = loadTemplateImages(imageFileNames);
        templates.addAll(Arrays.asList(templateMats));

        templateAdapter = new TemplateAdapter(templates, imageFileNames);
        recyclerView.setAdapter(templateAdapter);

        run();
    }

    private void loadMainImage() {
        // Load image from assets
        AssetManager assetManager = getAssets();
        try {
            // Replace "image_detected_markers.png" with your actual file name
            InputStream inputStream = assetManager.open("images/image_detected_markers.png");
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            Log.e(TAG, "Loading mainImage: " + "image_detected_markers.png");

            // Convert bitmap to Mat
//            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mainImage);

            // Convert to grayscale
            Imgproc.cvtColor(mainImage, mainImage, Imgproc.COLOR_RGB2GRAY);

//            templates[i] = mat; // Assign template image to array
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void run() {
//        loadImages();
        Log.e(TAG, "run: Loaded image filenames: " + imageFileNames);
        // Pattern matching
        Mat[] templates = loadTemplateImages(imageFileNames);
        this.templates.addAll(Arrays.asList(templates));
        templateAdapter.notifyDataSetChanged();
    }

    private void loadImages() {
        // Retrieve image file names from assets
        try {
            String[] assetFileNames = getAssets().list("images");
            imageFileNames = new ArrayList<>(Arrays.asList(assetFileNames));
            // Remove specific files
            imageFileNames.removeAll(Arrays.asList("android-logo-mask.png", "android-logo-shine.png", "clock64.png", "clock_font.png", "image_detected_markers.png"));
            Log.e(TAG, "Loaded " + imageFileNames.size() + " image filenames: " + imageFileNames);

            // Load image with marker
//            mainImageFileNames = new ArrayList<>(Arrays.asList(assetFileNames));
        } catch (IOException e) {
            Log.e(TAG, "Error loading image filenames from assets", e);
            e.printStackTrace();
        }
    }

    private Mat[] loadTemplateImages(List<String> imageFileNames) {
        Log.e(TAG, "Loading template images");
        Mat[] templates = new Mat[imageFileNames.size()];
        for (int i = 0; i < imageFileNames.size(); i++) {
            String fileName = imageFileNames.get(i);
            try {
                // Open template image file in Bitmap from the filename and convert to Mat
                Log.e(TAG, "Loading template: " + fileName);
                // Load image from asset into bitmap
                InputStream inputStream = getAssets().open("images/" + fileName);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                // Convert bitmap to Mat
                Mat mat = new Mat();
                Utils.bitmapToMat(bitmap, mat);

                // Convert to grayscale
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);

                templates[i] = mat; // Assign template image to array
                inputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error loading template: " + fileName, e);
                e.printStackTrace();
            }
        }
        return templates;
    }

    // RecyclerView Adapter for displaying template images
    private class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.TemplateViewHolder> {
        private List<Mat> templates;
        private List<String> imageFileNames;

        TemplateAdapter(List<Mat> templates, List<String> imageFileNames) {
            this.templates = templates;
            this.imageFileNames = imageFileNames;
        }

        class TemplateViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView textViewLabel;

            TemplateViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageViewTemplate);
                textViewLabel = itemView.findViewById(R.id.textViewLabel);
            }
        }

        @Override
        public TemplateViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_template_image, parent, false);
            return new TemplateViewHolder(view);
        }

        @Override
        public void onBindViewHolder(TemplateViewHolder holder, int position) {
            if (position < templates.size()) {
                Mat mat = templates.get(position);

                // Convert Mat to Bitmap for display
                Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(mat, bitmap);

                // Set bitmap to ImageView
                holder.imageView.setImageBitmap(bitmap);

                // Set label text (using filename)
                if (position < imageFileNames.size()) {
                    String fileName = imageFileNames.get(position);
                    holder.textViewLabel.setText(fileName);
                } else {
                    Log.e(TAG, "Invalid position for imageFileNames: " + position + ", Size: " + imageFileNames.size());
                }
            } else {
                Log.e(TAG, "Invalid position for templates: " + position + ", Size: " + templates.size());
            }
        }

        @Override
        public int getItemCount() {
            return templates.size();
        }
    }

}
