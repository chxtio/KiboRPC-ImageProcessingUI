package jp.jaxa.iss.kibo.rpc.sampleapk;

import android.util.Log;

import gov.nasa.arc.astrobee.Result;
import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;

import org.opencv.core.Mat;

import java.util.List;

/**
 * Class meant to handle commands from the Ground Data System and execute them in Astrobee.
 */

public class YourService extends KiboRpcService {

    private final String TAG = this.getClass().getSimpleName();

    @Override
    protected void runPlan1(){
        // The mission starts: Undocks Astrobee from docking station, starts timer, returns Success/Failure
        api.startMission();
        Log.i(TAG, "Start mission");

//        // Get active target id list
//        List<Integer> list = api.getActiveTargets();
//
//        for (int i = 0; i < list.size(); i++) {
//            // Try to add various parameters (e.g. your own coords, KOZ coords, remaining time, etc)
//            if (list.get(i) == 1) {
//                Point point = new Point(11.143d, -6.7607d,4.9654d);
//                Quaternion quaternion = new Quaternion(0f, 0f, -0.707f, 0.707f);
//                api.moveTo(point, quaternion, false);
//            }
//        }

        // Move to a point.
//        Point point = new Point(10.9d, -9.92284d, 5.195d);
//        Quaternion quaternion = new Quaternion(0f, 0f, -0.707f, 0.707f);
//        api.moveTo(point, quaternion, false);

        // Move to point 1 (1st attempt)
        Point point = new Point(9.815f, -9.806f, 4.293f);
        Quaternion quaternion = new Quaternion(0f, 0f, 0f, 1f);
        Result result = api.moveTo(point, quaternion, true);

        // Check result and loop while moveTo() has not succeeded
        final int LOOP_MAX = 5;
        int loopCounter = 0;
        while(!result.hasSucceeded() && loopCounter < LOOP_MAX) {
            // Retry
            result = api.moveTo(point, quaternion, true);
            ++loopCounter;
        }

        // Todo: Move to Point2

        // Get a camera image.
        Mat image = api.getMatNavCam();

//        try {
//            String imageStr = yourMethod();
//            Log.i(TAG, "If yourMethod() throws Exception, this step is not executed");
//        } catch (Exception e) {
//            Log.i(TAG, "If yourMethod() throws Exception, this step is executed");
//        }
//
        if (image == null) {
            // Error handling
            Log.i(TAG, "No image");
        } else {
            String imageStr = yourMethod();
//            readAR(image);
        }


        api.saveMatImage(image, "file_name.png");

        /* *********************************************************************** */
        /* Write your code to recognize type and number of items in the each area! */
        /* *********************************************************************** */

        // When you recognize items, let’s set the type and number.
        api.setAreaInfo(1, "item_name", 1);

        /* **************************************************** */
        /* Let's move to the each area and recognize the items. */
        /* **************************************************** */

        // When you move to the front of the astronaut, report the rounding completion.
        api.reportRoundingCompletion();

        /* ********************************************************** */
        /* Write your code to recognize which item the astronaut has. */
        /* ********************************************************** */

        // Let's notify the astronaut when you recognize it.
        api.notifyRecognitionItem();

        /* ******************************************************************************************************* */
        /* Write your code to move Astrobee to the location of the target item (what the astronaut is looking for) */
        /* ******************************************************************************************************* */

        // Take a snapshot of the target item.
        api.takeTargetItemSnapshot();
    }

    @Override
    protected void runPlan2(){
       // write your plan 2 here.
    }

    @Override
    protected void runPlan3(){
        // write your plan 3 here.
    }

    // You can add your method.
    private String yourMethod(){
        return "your method";
    }
}
