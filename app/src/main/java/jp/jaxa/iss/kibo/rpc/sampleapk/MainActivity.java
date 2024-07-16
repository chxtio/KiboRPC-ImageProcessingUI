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
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
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

//        YourService yourService = new YourService();
        int[] templateMatchCnt = getNumTemplateMatch(templates, mainImage);

        // Handle template match counts
        for (int i = 0; i < templateMatchCnt.length; i++) {
            String fileName = imageFileNames.get(i);
            Log.i(TAG, "Template " + i + " " + fileName + " match count: " + templateMatchCnt[i]);
        }

        int mostMatchTemplateNum = getMaxIndex(templateMatchCnt);
        Log.i(TAG, "Best match: " + imageFileNames.get(mostMatchTemplateNum));
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

    public int[] getNumTemplateMatch(Mat[] templates, Mat img){
        // Get the number of template matches
//        int templateMatchCnt[] = new int[10];
        int templateMatchCnt[] = new int[templates.length];
        Mat result = new Mat();
        for (int tempNum = 0; tempNum < templates.length; tempNum++) {
//            Log.e(TAG, "Template match: " + tempNum);
            String fileName = imageFileNames.get(tempNum);
            Log.e(TAG, "Template matching with: " +  tempNum + " " +  fileName);
            if (templates[tempNum] == null || templates[tempNum].empty()) {
                Log.e(TAG, "Template is null or empty at index: " + tempNum);
                continue; // Skip to the next template
            }

            // Number of matches
            int matchCnt = 0;
            // Coordinates of the matched location
            List<org.opencv.core.Point> matches = new ArrayList<>();

            // Check if image or template is null
//            Log.e(TAG, "img test");
//            if (img.empty()) {
//                Log.e(TAG, "img is empty");
//            }
            if(templates[tempNum].empty()){
                Log.e(TAG, "template is empty");
            }

            Mat template = templates[tempNum].clone();
            Mat targetImg = img.clone();

            // Todo: fix error: (-215) (depth == CV_8U || depth == CV_32F) && type == _templ.type()
            //  && _img.dims() <= 2 in function cv::matchTemplate
            // Check if template and image are in compatible format
            // Check depth
//            Log.i(TAG, "Saving template: ");
//            api.saveMatImage(template, "template-test");

//            Log.i(TAG, "Template size: " + template.size());
//            Log.i(TAG, "Template type: " + template.type() + " (" + CvType.typeToString(template.type()) + ")");
//            Log.i(TAG, "Template channels (dims): " + template.dims());
//            Log.i(TAG, "Template channels: " + template.channels());
//
//            Log.i(TAG, "Image size: " +targetImg.size());
//            Log.i(TAG, "Image type: " + targetImg.type() + " (" + CvType.typeToString(img.type()) + ")");
//            Log.i(TAG, "Image channels (dims): " + targetImg.dims());
//            Log.i(TAG, "Image channels: " + targetImg.channels());



            // Pattern matching
            int widthMin = 20; //[px]
            int widthMax = 100; //[px]
            int changeWidth = 5; //[px]
            int changeAngle = 45; //[px]

            for (int i = widthMin; i <= widthMax; i += changeWidth) {
                for (int j = 0; j <= 360; j += changeAngle) {
                    Mat resizedTemp = resizeImg(template, i);
                    Mat rotResizedTemp = rotImage(resizedTemp, j);

//                    Mat result = new Mat();
                    Imgproc.matchTemplate(targetImg, rotResizedTemp, result, Imgproc.TM_CCOEFF_NORMED);

                    // Get coordinates with similarity greater than or equal to the threshold
                    double threshold = 0.75;
                    Core.MinMaxLocResult mmlr = Core.minMaxLoc(result);
                    double maxVal = mmlr.maxVal;
                    if (maxVal >= threshold) {
                        // Extract only results greater than or equal to the threshold
                        Mat thresholdedResult = new Mat();
                        Imgproc.threshold(result, thresholdedResult, threshold, 1.0, Imgproc.THRESH_TOZERO);

                        // Get match counts
                        for (int y = 0; y < thresholdedResult.rows(); y++) {
                            for (int x = 0; x < thresholdedResult.cols(); x++) {
                                if (thresholdedResult.get(y, x)[0] > 0) {
                                    matches.add(new org.opencv.core.Point(x,y));
//                                    matchCnt++;
                                }
                            }
                        }
                        thresholdedResult.release();
                    }
                    resizedTemp.release();
                    rotResizedTemp.release();
                }
            }

            // Avoid detecting the same location multiple times
            List<org.opencv.core.Point> filteredMatches = removeDuplicates(matches);
            matchCnt += filteredMatches.size();
            Log.i(TAG, "Template " + tempNum + " match count: " + matchCnt);

            // Number of matches for each template
            templateMatchCnt[tempNum] = matchCnt;

            template.release();
            targetImg.release();

        }

        result.release();
        return templateMatchCnt;
    }

    // Remove multiple detections
    public static List<org.opencv.core.Point> removeDuplicates (List<org.opencv.core.Point> points) {
        double length = 10; //Width 10px
        List<org.opencv.core.Point> filteredList = new ArrayList<>();

        for(org.opencv.core.Point point : points) {
            boolean isInclude = false;
            for (org.opencv.core.Point checkPoint : filteredList) {
                double distance = calculateDistance(point, checkPoint);

                if(distance <= length){
                    isInclude = true;
                    break;
                }
            }

            if(!isInclude){
                filteredList.add(point);
            }
        }
        return filteredList;
    }

    // Resize image
    public Mat resizeImg(Mat img, int width){
        int height = (int) (img.rows() * ((double) width / img.cols()));
        Mat resizedImg = new Mat();
        Imgproc.resize(img, resizedImg, new Size(width, height));

        return resizedImg;
    }

    // Rotate image
    public Mat rotImage(Mat img, int angle) {
        org.opencv.core.Point center = new org.opencv.core.Point(img.cols() / 2.0, img.rows() / 2.0);
        Mat rotatedMat = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        Mat rotatedImg = new Mat();
        Imgproc.warpAffine(img, rotatedImg, rotatedMat, img.size());

        return rotatedImg;
    }

    // Find the distance between two points
    public static double calculateDistance(org.opencv.core.Point p1, org.opencv.core.Point p2){
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;

        return Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
    }

    // Get max value of an array
    public int getMaxIndex(int[] array) {
        int max = 0;
        int maxIndex = 0;

        // Find the index of the element with largest value
        for (int i = 0; i < array.length; i++) {
            if(array[i] > max) {
                max = array[i];
                maxIndex = i;
            }
        }

        return maxIndex;
    }
}


