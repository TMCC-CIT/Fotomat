package edu.tmcc.cit230.fotomat;

import android.location.Location;
import android.os.Handler;

import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.model.LatLng;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Created by joe on 4/2/17.
 */

@SuppressWarnings("MissingPermission")
public class MockLocationSource implements LocationSource {

    private static final float ACCURACY = 1; // Meters
    private static final int MAX_SPEED = 10; // m/s
    private static LatLng center = new LatLng(39.5222339, -119.8078669);
    private static final double DELTA_LAT = 0.05;
    private static final double DELTA_LON = 0.05;

    private static final long UPDATE_PERIOD = TimeUnit.SECONDS.toMillis(5);

    private final Handler handler = new Handler();
    private final Location currentLocation;
    private final long updatePeriod;
    private LatLng lastCoordinate = center;
    private OnLocationChangedListener listener;

    public MockLocationSource(Location currentLocation, long updatePeriod) {
        this.currentLocation = currentLocation;
        this.updatePeriod = updatePeriod;
        if(currentLocation != null) {
            center = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
        }
        lastCoordinate = center;
    }

    private void scheduleNewFix() {
        handler.postDelayed(updateLocationRunnable, UPDATE_PERIOD);
    }

    private final Runnable updateLocationRunnable = new Runnable() {

        @Override
        public void run() {
            Location randomLocation = generateRandomLocation();
            listener.onLocationChanged(randomLocation);
            scheduleNewFix();
        }
    };

    public Location generateRandomLocation() {

        Location location = new Location(getClass().getSimpleName());
        location.setTime(System.currentTimeMillis());
        location.setAccuracy(ACCURACY);
        location.setBearing(randomizer.nextInt(360));
        location.setSpeed(randomizer.nextInt(MAX_SPEED));
        location.setLatitude(lastCoordinate.latitude + scaleOffset(DELTA_LAT));
        location.setLongitude(lastCoordinate.longitude + scaleOffset(DELTA_LON));

        lastCoordinate = new LatLng(location.getLatitude(), location.getLongitude());

        return location;
    }

    private final static Random randomizer = new Random();

    private double scaleOffset(double value) {
        return (randomizer.nextDouble() - 0.5) * value;
    }

    @Override
    public void activate(OnLocationChangedListener locationChangedListener) {
        listener = locationChangedListener;
        scheduleNewFix();
    }

    @Override
    public void deactivate() {
        handler.removeCallbacks(updateLocationRunnable);
    }

}