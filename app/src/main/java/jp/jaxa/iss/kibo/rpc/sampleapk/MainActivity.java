package jp.jaxa.iss.kibo.rpc.sampleapk;

import android.Manifest;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jp.jaxa.iss.kibo.rpc.api.KiboRpcApi;
import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;
import jp.jaxa.iss.kibo.rpc.sampleapk.YourService;

public class MainActivity extends AppCompatActivity {


    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static final String TAG = MainActivity.class.getSimpleName();
    private List<String> imageFileNames;
    private List<String> mainImageFileNames;
    private String assetFileName = "images/image.png";
    private Mat mainImage;
    private Mat mainImageView;
    private List<Mat> templates = new ArrayList<>();
    private int bestMatchIndex = -1;
    private boolean templateMatchingStarted = false;
    private boolean undistortStarted = false;
    private boolean detectARStarted = false;
    private boolean estimatePoseStarted = false;
    private RecyclerView recyclerView;
    private TemplateAdapter templateAdapter;

    private ImageView imageView;
    private Button buttonMatchTemplate;
    private Button buttonDisplayMainImage;
    private Button buttonDetectAR;

    private Mat cameraMatrix;
    private Mat cameraCoefficients;
    Dictionary dictionary;
    List<Mat> corners = new ArrayList<>();
    Mat markerIds;

//    private YourService yourService;
//    private KiboRpcApi api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        yourService = new YourService(); // Instantiate YourService
//        KiboRpcApi api = KiboRpcApi.getInstance(); // Obtain the KiboRpcApi instance as required


        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed.");
        } else {
            Log.d(TAG, "OpenCV initialization succeeded.");
        }

        // Initialize ar detection variables
        dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250); // Load predefined ArUco dict of 250 unique 5x5 markers
        markerIds = new Mat();

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView);
        imageView = findViewById(R.id.imageView);
        buttonMatchTemplate= findViewById(R.id.buttonMatchTemplate);
        buttonDisplayMainImage = findViewById(R.id.buttonDisplayMainImage);
        buttonDetectAR = findViewById(R.id.buttonDetectAR);

        // Load templates and image filenames
        loadImages();
        mainImage = new Mat();
        loadMainImageView();
        loadMainImage();
        Mat[] templateMats = loadTemplateImages(imageFileNames);
        templates.addAll(Arrays.asList(templateMats));

        // Setup RecyclerView with GridLayoutManager (3 columns)
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        recyclerView.setLayoutManager(layoutManager);
        templateAdapter = new TemplateAdapter(templates, imageFileNames);
        recyclerView.setAdapter(templateAdapter);

        displayMainImage(mainImageView); // NavCam sample image

        // Button click listener for viewing pose estimation
        buttonDetectAR.setOnClickListener(v -> {
            if (!undistortStarted){
                correctImageDistortion(mainImageView);
                buttonDetectAR.setText("Detect AR");
                undistortStarted = true;
            } else if (!detectARStarted){
                detectAR(mainImageView);
                buttonDetectAR.setText("Estimate Pose");
                detectARStarted = true;
            } else if (!estimatePoseStarted) {
                pose_estimation(mainImageView);
                estimatePoseStarted = true;
            }
              displayMainImage(mainImageView);
        });

        // Button click listener for processing image
        buttonMatchTemplate.setOnClickListener(v -> {
            if(bestMatchIndex == -1 && !templateMatchingStarted){
                runTemplateMatching();
            }
            showTemplates();
        });

        // Button click listener for displaying main image
        buttonDisplayMainImage.setOnClickListener(v -> displayMainImage(mainImageView));

    }


    private void displayMainImage(Mat image) {
        Bitmap bitmap = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, bitmap);
        imageView.setImageBitmap(bitmap);
        imageView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        setTitle("NavCam");
    }

    private void showTemplates() {
        imageView.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        if (bestMatchIndex != -1) {
            setTitle("Best Match: " + imageFileNames.get(bestMatchIndex).replace(".png", ""));
        } else {
            setTitle("Template Matching...");
        }
    }

    private void runTemplateMatching() {
        // Execute the AsyncTask for template matching
        TemplateMatchingTask templateMatchingTask = new TemplateMatchingTask();
        templateMatchingTask.execute();
        templateMatchingStarted = true;
    }

    private class TemplateMatchingTask extends AsyncTask<Void, Integer, Integer> {
        @Override
        protected Integer doInBackground(Void... voids) {
            Mat[] templatesArray = templates.toArray(new Mat[0]);
            int[] templateMatchCnt = new int[templatesArray.length];
            Mat result = new Mat();

            for (int tempNum = 0; tempNum < templatesArray.length; tempNum++) {
                String fileName = imageFileNames.get(tempNum);
                Log.e(TAG, "Template matching with: " + tempNum + " " + fileName);
                if (templatesArray[tempNum] == null || templatesArray[tempNum].empty()) {
                    Log.e(TAG, "Template is null or empty at index: " + tempNum);
                    continue; // Skip to the next template
                }

                publishProgress(tempNum); // Update UI with the current template index

                // Perform the matching process
                int matchCnt = 0;
                List<org.opencv.core.Point> matches = new ArrayList<>();
                Mat template = templatesArray[tempNum].clone();
//                Imgproc.cvtColor(mainImage, mainImage, Imgproc.COLOR_RGB2GRAY); // Convert main image to gray
                Mat targetImg = mainImage.clone();

                int widthMin = 20; // [px]
                int widthMax = 100; // [px]
                int changeWidth = 5; // [px]
                int changeAngle = 45; // [px]

                for (int i = widthMin; i <= widthMax; i += changeWidth) {
                    for (int j = 0; j <= 360; j += changeAngle) {
                        Mat resizedTemp = resizeImg(template, i);
                        Mat rotResizedTemp = rotImage(resizedTemp, j);

                        Imgproc.matchTemplate(targetImg, rotResizedTemp, result, Imgproc.TM_CCOEFF_NORMED);
                        double threshold = 0.69;//0.66;//0.75;
                        Core.MinMaxLocResult mmlr = Core.minMaxLoc(result);
                        double maxVal = mmlr.maxVal;

                        if (maxVal >= threshold) {
                            Mat thresholdedResult = new Mat();
                            Imgproc.threshold(result, thresholdedResult, threshold, 1.0, Imgproc.THRESH_TOZERO);

                            for (int y = 0; y < thresholdedResult.rows(); y++) {
                                for (int x = 0; x < thresholdedResult.cols(); x++) {
                                    if (thresholdedResult.get(y, x)[0] > 0) {
                                        matches.add(new org.opencv.core.Point(x, y));
                                    }
                                }
                            }
                            thresholdedResult.release();
                        }
                        resizedTemp.release();
                        rotResizedTemp.release();
                    }
                }

                List<org.opencv.core.Point> filteredMatches = removeDuplicates(matches);
                matchCnt += filteredMatches.size();
                Log.i(TAG, "Template " + tempNum + " match count: " + matchCnt);
                templateMatchCnt[tempNum] = matchCnt;

                template.release();
                targetImg.release();
            }

            result.release();
            int mostMatchTemplateNum = getMaxIndex(templateMatchCnt);
            return mostMatchTemplateNum;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            int currentTemplateIndex = values[0];
            templateAdapter.setCurrentTemplateIndex(currentTemplateIndex);
        }

        @Override
        protected void onPostExecute(Integer bestMatchIndex) {
            // Update UI with best match information
            String bestMatchFilename = imageFileNames.get(bestMatchIndex);
            String bestMatch = bestMatchFilename.substring(0, bestMatchFilename.length() - 4);

            // Set bestMatchIndex and update UI
            templateAdapter.setBestMatchIndex(bestMatchIndex);

            // Set title on the main thread
            runOnUiThread(() -> {
                if (recyclerView.getVisibility() == View.VISIBLE) {
                    setTitle("Best Match: " + bestMatch);
                }
            });
        }
    }

    private void loadMainImage() {
        AssetManager assetManager = getAssets();
        try {
//            String assetFileName = "images/file_name.png";
            Log.e(TAG, "Loading mainImage: " + assetFileName);
            InputStream inputStream = assetManager.open(assetFileName);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            // Convert bitmap to Mat
            Utils.bitmapToMat(bitmap, mainImage);
            // Convert to grayscale
//            Imgproc.cvtColor(mainImage, mainImage, Imgproc.COLOR_RGBA2RGB);
            Imgproc.cvtColor(mainImage, mainImage, Imgproc.COLOR_RGB2GRAY); // Original
            Log.i(TAG, "Image Mat type: " + mainImage.type() + " (" + CvType.typeToString(mainImage.type()) + ")");
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyAssetImage(String assetFileName, String targetFileName) {
        AssetManager assetManager = getAssets();
        try {
            InputStream in = assetManager.open(assetFileName);
            File outFile = new File(getCacheDir(), targetFileName);
            OutputStream out = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadMainImageView() {
//        AssetManager assetManager = getAssets();
        // Copy the image from assets to the cache directory
//        String assetFileName = "images/image.png";
        String targetFileName = "image_copy.png";
        copyAssetImage(assetFileName, targetFileName);
        try {
            Log.e(TAG, "Loading mainImageView: " + targetFileName);
            File copiedImageFile = new File(getCacheDir(), targetFileName);
            Bitmap bitmap = BitmapFactory.decodeFile(copiedImageFile.getAbsolutePath());

            mainImageView = new Mat();
            // Convert bitmap to Mat
            Utils.bitmapToMat(bitmap, mainImageView);
            // Convert to grayscale
//            Imgproc.cvtColor(mainImage, mainImage, Imgproc.COLOR_RGBA2RGB);
//            Imgproc.cvtColor(mainImage, mainImage, Imgproc.COLOR_RGB2GRAY); // Original
            Log.i(TAG, "Image Mat type: " + mainImageView.type() + " (" + CvType.typeToString(mainImageView.type()) + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Retrieve template image filenames from assets
    private void loadImages() {
        try {
            String[] assetFileNames = getAssets().list("images");
            imageFileNames = new ArrayList<>(Arrays.asList(assetFileNames));
            // Remove specific files
            imageFileNames.removeAll(Arrays.asList("android-logo-mask.png", "android-logo-shine.png", "clock64.png",
                    "clock_font.png", "image.png", "image_view.png", "file_name.png"));
            Log.e(TAG, "Loaded " + imageFileNames.size() + " image filenames: " + imageFileNames);

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
        private int currentTemplateIndex = -1;
        private int customHighlightColor = Color.RED; // Default highlight color

        TemplateAdapter(List<Mat> templates, List<String> imageFileNames) {
            this.templates = templates;
            this.imageFileNames = imageFileNames;
        }

        public void setCurrentTemplateIndex(int index) {
            currentTemplateIndex = index;
            notifyDataSetChanged();
        }

        public void setCustomHighlightColor(int color) {
            this.customHighlightColor = color;
            notifyDataSetChanged(); // Notify adapter that data set changed to reflect new highlight color
        }

        public void setBestMatchIndex(int index) {
            bestMatchIndex = index;
            notifyDataSetChanged(); // Notify adapter that data set changed to reflect new best match index
        }

        @NonNull
        @Override
        public TemplateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_template_image, parent, false);
            return new TemplateViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TemplateViewHolder holder, int position) {
            Mat mat = templates.get(position);
            Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bitmap);
            holder.imageView.setImageBitmap(bitmap);
            holder.textViewLabel.setText(imageFileNames.get(position));

            // Highlight the current template or the best match
            if (position == bestMatchIndex) {
                holder.itemView.setBackgroundColor(customHighlightColor);
            } else if (position == currentTemplateIndex){
                holder.itemView.setBackgroundColor(Color.YELLOW);
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        @Override
        public int getItemCount() {
            return templates.size();
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
    }

    // Remove multiple detections
    public static List<org.opencv.core.Point> removeDuplicates(List<org.opencv.core.Point> points) {
        double length = 10; // Width 10px
        List<org.opencv.core.Point> filteredList = new ArrayList<>();

        for (org.opencv.core.Point point : points) {
            boolean isInclude = false;
            for (org.opencv.core.Point checkPoint : filteredList) {
                double distance = calculateDistance(point, checkPoint);

                if (distance <= length) {
                    isInclude = true;
                    break;
                }
            }

            if (!isInclude) {
                filteredList.add(point);
            }
        }

        return filteredList;
    }


    // Resize image
    public Mat resizeImg(Mat img, int width) {
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
    public static double calculateDistance(org.opencv.core.Point p1, org.opencv.core.Point p2) {
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
            if (array[i] > max) {
                max = array[i];
                maxIndex = i;
            }
        }

        return maxIndex;
    }

    // Estimate camera pose
    private void pose_estimation(Mat image){
        Log.e(TAG, "Estimating pose");
        if(!markerIds.empty()){
            Mat rvecs = new Mat();
            Mat tvecs = new Mat();
            MatOfDouble rvec = new MatOfDouble();
            MatOfDouble tvec = new MatOfDouble();
            MatOfDouble distCoeffs = new MatOfDouble(cameraCoefficients);
            Aruco.estimatePoseSingleMarkers(corners, 0.05f, cameraMatrix, distCoeffs, rvecs, tvecs);

            for(int i = 0; i < markerIds.rows(); i++) {
                Aruco.drawAxis(image, cameraMatrix, distCoeffs, rvecs.row(i), tvecs.row(i), 0.1f);
            }

//            undistortImg.copyTo(image);
            Log.e(TAG, "Estimating pose complete: check output");
            Toast.makeText(getApplicationContext(), "Pose estimation complete!", Toast.LENGTH_SHORT).show();
        }
    }

    // Detect AR and draw markers
    private void detectAR(Mat image) {
//        Dictionary dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250); // Load predefined ArUco dict of 250 unique 5x5 markers
//        List<Mat> corners = new ArrayList<>();
//        Mat markerIds = new Mat();
        Imgproc.cvtColor(image, image, Imgproc.COLOR_RGBA2RGB);
//        Imgproc.cvtColor(mainImage, mainImage, Imgproc.COLOR_GRAY2RGB); // Convert to grayscale
        Log.i(TAG, "in detectAR: Image Mat type: " + image.type() + " (" + CvType.typeToString(image.type()) + ")");
        Aruco.detectMarkers(image, dictionary, corners, markerIds); // Detect markers and store the corners and IDs

        // Convert image to RGB color space
//        Imgproc.cvtColor(image, image, Imgproc.COLOR_GRAY2RGB);

        // Draw detected markers on image
        if (!markerIds.empty()) {
            Scalar green = new Scalar(0, 255, 0);
            Scalar red = new Scalar(255, 0, 0);
            Aruco.drawDetectedMarkers(image, corners); //, markerIds, green);

            // Draw marker ID label
            if (corners.size() > 0) {
                Mat firstCorner = corners.get(0);
                double x = firstCorner.get(0, 0)[0];
                double y = firstCorner.get(0, 0)[1];
                org.opencv.core.Point labelPos = new org.opencv.core.Point(x, y - 30); // Offset
                int markerId = (int) markerIds.get(0, 0)[0];
                Imgproc.putText(image, "id=" + markerId, labelPos, Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, red, 2);
            }
            Log.i(TAG, "Markers detected: " + markerIds.dump());
        } else {
            Log.i(TAG, "No markers detected.");
        }
        Toast.makeText(getApplicationContext(), "AR marker(s) detected", Toast.LENGTH_SHORT).show();
    }

    public void correctImageDistortion(Mat image) {
        loadCameraParameters();

        // Undistort image
        Mat undistortImg = new Mat();
        Calib3d.undistort(image, undistortImg, cameraMatrix, cameraCoefficients);

        Log.e(TAG, "Undistorted image");
        undistortImg.copyTo(image);
        Toast.makeText(getApplicationContext(), "Corrected image distortion", Toast.LENGTH_SHORT).show();
    }

    private void loadCameraParameters() {
        Log.e(TAG, "Getting calibration parameters");
        double[][] cameraMatrixValues = {
                {1000.0, 0.0, 320.0}, // Hardcoded values
                {0.0, 1000.0, 240.0},
                {0.0, 0.0, 1.0}
        };

        cameraMatrix = new Mat(3, 3, CvType.CV_64F);
        for (int i = 0; i < cameraMatrixValues.length; i++) {
            cameraMatrix.put(i, 0, cameraMatrixValues[i]);
        }

        double[] cameraCoefficientsValues = {0.1, -0.2, 0.0, 0.0, 0.1}; // Hardcoded values
        cameraCoefficients = new Mat(1, 5, CvType.CV_64F);
        cameraCoefficients.put(0, 0, cameraCoefficientsValues);
    }
}
