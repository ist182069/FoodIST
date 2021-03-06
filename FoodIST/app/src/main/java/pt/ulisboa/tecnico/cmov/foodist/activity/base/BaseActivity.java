package pt.ulisboa.tecnico.cmov.foodist.activity.base;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import pt.ulisboa.tecnico.cmov.foodist.R;
import pt.ulisboa.tecnico.cmov.foodist.broadcast.PreLoadingNetworkReceiver;
import pt.ulisboa.tecnico.cmov.foodist.status.GlobalStatus;

public abstract class BaseActivity extends AppCompatActivity {
    private Set<AsyncTask> tasks = Collections.synchronizedSet(new HashSet<>());
    private Set<BroadcastReceiver> receivers = new HashSet<>();

    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isLoggedIn() {
        return getGlobalStatus().isLoggedIn();
    }

    public GlobalStatus getGlobalStatus() {
        return (GlobalStatus) getApplicationContext();
    }

    private void cancelTasks() {
        tasks.forEach(task -> task.cancel(true));
    }

    private void cancelReceivers() {
        receivers.forEach(this::unregisterReceiver);
        receivers = new HashSet<>();
    }

    public void addTask(AsyncTask task) {
        tasks.add(task);
    }

    public void removeTask(AsyncTask task) {
        tasks.remove(task);
    }

    public void addReceiver(BroadcastReceiver receiver, String content, String... intents) {
        IntentFilter filter = new IntentFilter(content);
        Arrays.stream(intents).forEach(intent -> filter.addAction(getPackageName() + intent));

        registerReceiver(receiver, filter);
        receivers.add(receiver);
    }

    public void addReceivers() {
    }

    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public static Context setLocale(Context context) {
        SharedPreferences pref = context.getSharedPreferences(context.getString(R.string.profile_file), 0);

        Locale locale = new Locale(pref.getString(context.getString(R.string.shared_prefs_profile_language), "en"));
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        context = context.createConfigurationContext(config);
        return context;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(setLocale(base));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTasks();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelReceivers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        addReceivers();
        addReceiver(new PreLoadingNetworkReceiver(), ConnectivityManager.CONNECTIVITY_ACTION, WifiManager.NETWORK_STATE_CHANGED_ACTION);
    }


    public String timeString(Long time) {
        String res = "";
        if (time > (3600 * 24)) {
            long days = time / (3600 * 24);
            res += Long.toString(days);
            String suffix = days == 1 ? getString(R.string.day) : getString(R.string.days);
            res += " " + suffix + " ";
            time = time % (3600 * 24);
        }
        if (time > 3600) {
            long hours = time / 3600;
            res += Long.toString(hours);
            String suffix = hours == 1 ? getString(R.string.hour) : getString(R.string.hours);
            res += " " + suffix + " ";
            time = time % 3600;
        }
        if (time > 60) {
            long minutes = time / 60;
            res += Long.toString(minutes);
            String suffix = minutes == 1 ? getString(R.string.minute) : getString(R.string.minutes);
            res += " " + suffix + " ";
            time = time % 60;
        }
        res += Long.toString(time);
        String suffix = time == 1 ? getString(R.string.second) : getString(R.string.seconds);
        res += " " + suffix;
        return res;
    }
}
