package jp.jaxa.iss.kibo.rpc.sampleapk;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import gov.nasa.arc.astrobee.Result;
import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OptionalDataException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;

import org.opencv.android.Utils;
import org.opencv.aruco.Aruco;
import org.opencv.core.Size;
import org.opencv.core.Core;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.aruco.Dictionary;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Class meant to handle commands from the Ground Data System and execute them in Astrobee.
 */
public class YourService extends KiboRpcService {

    private final String TAG = this.getClass().getSimpleName();
//    private static String[] TEMPLATE_FILE_NAMES;
//
//    static {
//        try {
//            TEMPLATE_FILE_NAMES = loadTemplateFileNames();
//        } catch (Exception e) {
//            TEMPLATE_FILE_NAMES = new String[0];
//            Log.e("YourService", "Error loading template file names", e);
//        }
//    }

    // Image Assets File Information
    private AssetManager assetManager;
    private String[] imageFileNames;

    @Override
    protected void runPlan1() {
        // The mission starts: Undocks Astrobee from docking station, starts timer, returns Success/Failure
        api.startMission();
        Log.i(TAG, "Start mission");

        // Initialize AssetManager
        assetManager = getAssets();
        // Retrieve image file names from assets
        try {
            imageFileNames = assetManager.list("");
            Log.e(TAG, "Loaded image filenames: " + Arrays.toString(imageFileNames));
        } catch (Exception e) {
            imageFileNames = new String[0];
            Log.e(TAG, "Error loading image filenames from assets", e);
        }

        // Move to point1 (1st attempt)
        Point point = new Point(11d, -9.88d, 5.195d);
        Quaternion quaternion = new Quaternion(0.5f, -0.6f, -0.707f, 0.707f);
        Result result = api.moveTo(point, quaternion, true);

        // Check result and loop while moveTo() has not succeeded
        final int LOOP_MAX = 7;
        int loopCounter = 0;
        while (!result.hasSucceeded() && loopCounter < LOOP_MAX) {
            // Retry
            result = api.moveTo(point, quaternion, true);
            ++loopCounter;
        }

        // To-do: Move to Point2

        // Get a camera image.
        Mat image = api.getMatNavCam();

        if (image == null) {
            // Error handling
            Log.i(TAG, "No image");
            return;
        }

        String imageStr = yourMethod();
        detectAR(image);
        api.saveMatImage(image, "image_detected_markers.png");
//        Mat undistortImg = correctImageDistortion(image);
//        api.saveMatImage(undistortImg, "undistort_image_detected_markers.png");

        // Pattern matching
        Mat[] templates = loadTemplateImages(imageFileNames);
        int[] templateMatchCnt = getNumTemplateMatch(templates, image);

        // Handle template match counts
        for (int i = 0; i < templateMatchCnt.length; i++) {
            Log.i(TAG, "Template " + i + " match count: " + templateMatchCnt[i]);
        }

        /*************************************************************************/
        /* Write your code to recognize type and number of items in each area! */
        /*************************************************************************/

        // When you recognize items, let’s set the type and number.
//        api.setAreaInfo(1, "item_name", 1);
        int mostMatchTemplateNum = getMaxIndex(templateMatchCnt);
        api.setAreaInfo(1, imageFileNames[mostMatchTemplateNum], templateMatchCnt[mostMatchTemplateNum]);

        /******************************************************/
        /* Let's move to the each area and recognize the items. */
        /******************************************************/

        // When you move to the front of the astronaut, report the rounding completion.
        api.reportRoundingCompletion();

        /************************************************************/
        /* Write your code to recognize which item the astronaut has. */
        /************************************************************/

        // Let's notify the astronaut when you recognize it.
        api.notifyRecognitionItem();

        /*********************************************************************************************************/
        /* Write your code to move Astrobee to the location of the target item (what the astronaut is looking for) */
        /*********************************************************************************************************/

        // Take a snapshot of the target item.
        api.takeTargetItemSnapshot();
    }


    @Override
    protected void runPlan2() {
        // Write your plan 2 here.
    }

    @Override
    protected void runPlan3() {
        // Write your plan 3 here.
    }

    // You can add your method.
    private String yourMethod() {
        return "your method";
    }

    // Detect AR and draw markers
    private void detectAR(Mat image) {
        Dictionary dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250); // Load predefined ArUco dict of 250 unique 5x5 markers
        List<Mat> corners = new ArrayList<>();
        Mat markerIds = new Mat();
        Aruco.detectMarkers(image, dictionary, corners, markerIds); // Detect markers and store the corners and IDs

        // Convert image to RGB color space
        Imgproc.cvtColor(image, image, Imgproc.COLOR_GRAY2RGB);

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

//            // Convert back to gray
//            Imgproc.cvtColor(image, image, Imgproc.COLOR_RGB2BGR);

            Log.i(TAG, "Markers detected: " + markerIds.dump());
        } else {
            Log.i(TAG, "No markers detected.");
        }
    }

    // Attempt to straighten image
    private Mat correctImageDistortion(Mat image) {
        // Get camera matrix and populate with camera intrinsics
        Mat cameraMatrix = new Mat(3, 3, CvType.CV_64F);
        cameraMatrix.put(0, 0, api.getNavCamIntrinsics()[0]);

        // Get lens distortion parameters
        Mat cameraCoefficients = new Mat(1, 5, CvType.CV_64F);
        cameraCoefficients.put(0, 0, api.getNavCamIntrinsics()[1]);
        cameraCoefficients.convertTo(cameraCoefficients, CvType.CV_64F);

        // Undistort image
        Mat undistortImg = new Mat();
        Calib3d.undistort(image, undistortImg, cameraMatrix, cameraCoefficients);

        return undistortImg;
    }

    // Load template images
    private Mat[] loadTemplateImages(String[] imageFileNames){
        Mat[] templates = new Mat[imageFileNames.length];
        for (int i = 0; i < imageFileNames.length; i++) {
            try {
                // Open template image file in Bitmap from the filename and convert to Mat
                Log.e(TAG, "Loading template: " + imageFileNames[i]);
                InputStream inputStream = assetManager.open(imageFileNames[i]);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                Mat mat = new Mat();
                Utils.bitmapToMat(bitmap, mat);

                // Convert to grayscale
//                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BayerBG2GRAY);
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
                templates[i] = mat; // Assign to array of templates
                inputStream.close();

            } catch (IOException e) {
                e.printStackTrace();;
            }
        }

        return templates;
    }

    private int[] getNumTemplateMatch(Mat[] templates, Mat img){
        // Get the number of template matches
        int templateMatchCnt[] = new int[10];
        for (int tempNum = 0; tempNum < templates.length; tempNum++) {
            // Number of matches
            int matchCnt = 0;
            // Coordinates of the matched location
            List<org.opencv.core.Point> matches = new ArrayList<>();

            Mat template = templates[tempNum].clone();
            Mat targetImg = img.clone();

            // Pattern matching
            int widthMin = 20; //[px]
            int widthMax = 100; //[px]
            int changeWidth = 5; //[px]
            int changeAngle = 45; //[px]

            for (int i = widthMin; i <= widthMax; i += changeWidth) {
                for (int j = 0; j <= 360; j += changeAngle) {
                    Mat resizedTemp = resizeImg(template, i);
                    Mat rotResizedTemp = rotImage(resizedTemp, j);

                    Mat result = new Mat();
                    Imgproc.matchTemplate(targetImg, rotResizedTemp, result, Imgproc.TM_CCOEFF_NORMED);

                    // Get coordinates with similarity greater than or equal to the threshold
                    double threshold = 0.8;
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
                    }
                }
            }

            // Avoid detecting the same location multiple times
            List<org.opencv.core.Point> filteredMatches = removeDuplicates(matches);
            matchCnt += filteredMatches.size();

            // Number of matches for each template
            templateMatchCnt[tempNum] = matchCnt;

        }

        return templateMatchCnt;
    }

    // Remove multiple detections
    private static List<org.opencv.core.Point> removeDuplicates (List<org.opencv.core.Point> points) {
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
    private Mat resizeImg(Mat img, int width){
        int height = (int) (img.rows() * ((double) width / img.cols()));
        Mat resizedImg = new Mat();
        Imgproc.resize(img, resizedImg, new Size(width, height));

        return resizedImg;
    }

    // Rotate image
    private Mat rotImage(Mat img, int angle) {
        org.opencv.core.Point center = new org.opencv.core.Point(img.cols() / 2.0, img.rows() / 2.0);
        Mat rotatedMat = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        Mat rotatedImg = new Mat();
        Imgproc.warpAffine(img, rotatedImg, rotatedMat, img.size());

        return rotatedImg;
    }

    // Find the distance between two points
    private static double calculateDistance(org.opencv.core.Point p1, org.opencv.core.Point p2){
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;

        return Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
    }

    // Get max value of an array
    private int getMaxIndex(int[] array) {
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
