package jp.jaxa.iss.kibo.rpc.sampleapk;

import android.util.Log;
import gov.nasa.arc.astrobee.Result;
import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;

import java.util.ArrayList;
import java.util.List;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;

import org.opencv.aruco.Aruco;
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

    @Override
    protected void runPlan1() {
        // The mission starts: Undocks Astrobee from docking station, starts timer, returns Success/Failure
        api.startMission();
        Log.i(TAG, "Start mission");

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
        } else {
            String imageStr = yourMethod();
            // readAR(image);
        }

        // Detect AR
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

            Log.i(TAG, "Markers detected: " + markerIds.dump());
        } else {
            Log.i(TAG, "No markers detected.");
        }

        // Correct image distortion
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

        api.saveMatImage(undistortImg, "image_with_markers.png");

        /*************************************************************************/
        /* Write your code to recognize type and number of items in each area! */
        /*************************************************************************/

        // When you recognize items, let’s set the type and number.
        api.setAreaInfo(1, "item_name", 1);

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
}
