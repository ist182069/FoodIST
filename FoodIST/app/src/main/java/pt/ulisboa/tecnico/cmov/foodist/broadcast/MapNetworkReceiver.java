package pt.ulisboa.tecnico.cmov.foodist.broadcast;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Button;

import java.util.Set;

import pt.ulisboa.tecnico.cmov.foodist.activity.base.ActivityWithMap;
import pt.ulisboa.tecnico.cmov.foodist.broadcast.base.BaseNetworkReceiver;

public class MapNetworkReceiver extends BaseNetworkReceiver {
    private final static String TAG = "SERVICE-ACTIVITY-NETWORK-RECEIVER";

    private ActivityWithMap activity;

    public MapNetworkReceiver(ActivityWithMap activity) {
        super();
        this.activity = activity;
    }

    public MapNetworkReceiver(Set<Button> buttons) {
        super(buttons);
    }

    @Override
    protected void onNetworkUp(Context context, Intent intent) {
        Log.d(TAG, "On Network Up");
        activity.startLocationUpdates();
    }

    @Override
    protected void onNetworkDown(Context context, Intent intent) {
        Log.d(TAG, "On Network Down");
        activity.stopLocationUpdates();
    }
}
