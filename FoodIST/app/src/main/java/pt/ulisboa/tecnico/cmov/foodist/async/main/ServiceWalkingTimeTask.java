package pt.ulisboa.tecnico.cmov.foodist.async.main;

import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import pt.ulisboa.tecnico.cmov.foodist.R;
import pt.ulisboa.tecnico.cmov.foodist.activity.MainActivity;
import pt.ulisboa.tecnico.cmov.foodist.async.base.BaseAsyncTask;
import pt.ulisboa.tecnico.cmov.foodist.async.data.WalkingTimeData;
import pt.ulisboa.tecnico.cmov.foodist.domain.FoodService;
import pt.ulisboa.tecnico.cmov.foodist.utils.CoordinateUtils;

public class ServiceWalkingTimeTask extends BaseAsyncTask<WalkingTimeData, Integer, Boolean, MainActivity> {

    private static final String TAG = "TAG_FoodServiceWalkingTimeTask";

    public static AtomicBoolean isRunning = new AtomicBoolean(false);

    public ServiceWalkingTimeTask(MainActivity activity) {
        super(activity);
    }

    @Override
    protected Boolean doInBackground(WalkingTimeData... walkingTimeResources) {
        if (walkingTimeResources == null || walkingTimeResources.length != 1) {
            return null;
        }
        WalkingTimeData resource = walkingTimeResources[0];
        Double latitude = resource.getLatitude();
        Double longitude = resource.getLongitude();
        String language = resource.getLanguage();
        if (latitude == null || longitude == null) {
            return false;
        }
        String apiKey = resource.getApiKey();
        List<FoodService> services = resource.getServices();

        for (FoodService service : services) {
            try {
                double serviceLatitude = service.getLatitude();
                double serviceLongitude = service.getLongitude();
                String walkingTime = CoordinateUtils.getWalkingTimeTo(latitude, longitude, serviceLatitude, serviceLongitude, apiKey, language);
                service.setDistance(walkingTime);
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Unable to get walking distance to service " + service.getName() + " due to cause: " + e.getCause());
            }
        }
        return true;
    }

    @Override
    public void onPostExecute(Boolean result) {
        if (result) {
            //Services of global status are now updated, just need to draw them
            // (If they have been overridden nothing new will happen)
            getActivity().drawServices();
        } else {
            getActivity().showToast(getActivity().getString(R.string.service_walking_time_unable_calculate_time));
        }
    }
}
