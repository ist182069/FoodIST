package pt.ulisboa.tecnico.cmov.foodist.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import foodist.server.grpc.contract.FoodISTServerServiceGrpc;
import pt.inesc.termite.wifidirect.SimWifiP2pBroadcast;
import pt.inesc.termite.wifidirect.SimWifiP2pDeviceList;
import pt.ulisboa.tecnico.cmov.foodist.activity.base.BaseActivity;
import pt.ulisboa.tecnico.cmov.foodist.async.queue.CancelJoinTask;
import pt.ulisboa.tecnico.cmov.foodist.async.queue.JoinQueueTask;
import pt.ulisboa.tecnico.cmov.foodist.async.queue.LeaveQueueTask;
import pt.ulisboa.tecnico.cmov.foodist.domain.FoodService;
import pt.ulisboa.tecnico.cmov.foodist.status.GlobalStatus;

public class SimWifiP2pBroadcastReceiver extends BroadcastReceiver {

    private final String uuid;
    private final FoodISTServerServiceGrpc.FoodISTServerServiceBlockingStub stub;
    private final static String TAG = "WIFI-DIRECT-RECEIVER";
    private boolean isInQueue = false;
    private Map<String, String> foodServices = new ConcurrentHashMap<>();
    private String currentFoodService;

    public SimWifiP2pBroadcastReceiver(GlobalStatus status, List<FoodService> foodServiceNames) {
        super();
        this.uuid = status.getUUID();
        this.stub = status.getStub();
        foodServiceNames.forEach(service -> this.foodServices.put(service.getBeacon(), service.getName()));

    }

    //Network is always available for this broadcast receiver
    @Override
    public void onReceive(Context context, Intent intent) {
        SimWifiP2pDeviceList deviceInfo = (SimWifiP2pDeviceList) intent.getSerializableExtra(SimWifiP2pBroadcast.EXTRA_DEVICE_LIST);

        if (deviceInfo == null) {
            //Just in case
            Log.d(TAG, "Device info was null");
            return;
        }
        Set<String> foodServiceBeacons = deviceInfo.getDeviceList().stream()
                .filter(Objects::nonNull)
                .map(device -> device.deviceName)
                .filter(deviceName -> foodServices.containsKey(deviceName))
                .map(foodServices::get)
                .collect(Collectors.toSet());

        if (foodServiceBeacons.isEmpty()) {
            if (isInQueue) {
                Log.d(TAG, "Left line of food service " + currentFoodService);
                isInQueue = false;
                new LeaveQueueTask(uuid, stub).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, currentFoodService);
                currentFoodService = null;
            }
        } else {
            String foodService = foodServiceBeacons.iterator().next();
            if (!foodService.equals(currentFoodService) && currentFoodService != null) {
                //Somehow moved to different foodService (probably disconnected my wifi)
                Log.d(TAG, "Left line of food service " + currentFoodService);
                new CancelJoinTask(uuid, stub).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, currentFoodService);
                isInQueue = false;
            }
            if (!isInQueue) {
                Log.d(TAG, "Entered line of food service " + foodService);
                currentFoodService = foodService;
                isInQueue = true;
                new JoinQueueTask(uuid, stub).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, currentFoodService);
            }
        }
    }
}
