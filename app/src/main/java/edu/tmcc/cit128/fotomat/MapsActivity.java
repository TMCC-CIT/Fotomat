package edu.tmcc.cit128.fotomat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, LocationListener, GoogleApiClient.OnConnectionFailedListener, LocationSource.OnLocationChangedListener, GoogleMap.InfoWindowAdapter {
    private static final String REQUESTING_LOCATION_UPDATES_KEY = "requestLocationUpdates";
    private static final String LOCATION_KEY = "location";
    private static final String LAST_UPDATED_TIME_STRING_KEY = "lastUpdatedTimeString";
    private static final int REQUEST_CURRENT_LOCATION = 1;
    private static final String TAG = "Fotomat:MapActivity";

    private static final int TAKE_A_PHOTO = 1;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;
    private Location mLastLocation;
    private String mLastUpdateTime;
    private boolean mRequestingLocationUpdates = true;
    private LocationRequest mLocationRequest;
    private String mCurrentPhotoPath;
    private ArrayList<Marker> mMarkerArray = new ArrayList<>();

    public MapsActivity() {
        mLocationRequest = LocationRequest.create();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(edu.tmcc.cit128.fotomat.R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(edu.tmcc.cit128.fotomat.R.id.map);
        mapFragment.getMapAsync(this);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        updateValuesFromBundle(savedInstanceState);

        Button takePhotoButton = (Button) findViewById(edu.tmcc.cit128.fotomat.R.id.takeAPhoto);

        takePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MapsActivity.this, edu.tmcc.cit128.fotomat.PhotoActivity.class);
                startActivityForResult(intent, TAKE_A_PHOTO);
            }
        });

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, mRequestingLocationUpdates);
        savedInstanceState.putParcelable(LOCATION_KEY, mCurrentLocation);
        savedInstanceState.putString(LAST_UPDATED_TIME_STRING_KEY, mLastUpdateTime);
        super.onSaveInstanceState(savedInstanceState);
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected() && !mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    protected void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TAKE_A_PHOTO) {
            if (resultCode == RESULT_OK) {
                mCurrentPhotoPath = data.getStringExtra(edu.tmcc.cit128.fotomat.PhotoActivity.EXTRA_PHOTO_URL);
                updateMapUI("Photo");
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        if (BuildConfig.DEBUG) {
            MockLocationSource source = new MockLocationSource(mCurrentLocation, 20);
            source.activate(this);
        }

        mMap.setMyLocationEnabled(true);
        mMap.setInfoWindowAdapter(this);
    }

    private Bitmap getPhotoSized(String markerPhoto, int scale) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(markerPhoto, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW / scale, photoH / scale);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        return BitmapFactory.decodeFile(markerPhoto, bmOptions);
    }

    private Bitmap getPhotoThumbnail(String markerPhoto) {
        return getPhotoSized(markerPhoto, 80);
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_CURRENT_LOCATION
        );
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mCurrentLocation != null) {
            Log.d(MapsActivity.TAG, String.format("Last Known Location: LAT=%s, LON=%s", String.valueOf(mCurrentLocation.getLatitude()), String.valueOf(mCurrentLocation.getLongitude())));
            onLocationChanged(mCurrentLocation);
            updateMapUI("Location");
            if (mLastLocation == null) {
                mLastLocation = mCurrentLocation;
            }
        }
        startLocationUpdates();
    }


    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    private void updateMapUI(String locationTitle) {
        if (mCurrentLocation == null || mLastLocation == null) {
            return;
        }
        AddMarkerToMap(locationTitle, mCurrentLocation.getLatitude(),
                mCurrentLocation.getLongitude());
        AddPolyLineToMap(mLastLocation, mCurrentLocation);
    }

    private void AddMarkerToMap(String locationTitle, double lat, double lon) {
        LatLng latLng = new LatLng(lat, lon);
        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .anchor(0.5f, 1));
        mMarkerArray.add(marker);
        if (mCurrentPhotoPath != null && mCurrentPhotoPath.isEmpty() == false) {
            String markerPhotoPath = mCurrentPhotoPath;
            mCurrentPhotoPath = null;
            Bitmap bitmap = getPhotoThumbnail(markerPhotoPath);
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap));
            marker.setTitle(String.format("%s-%s: %.3f,%.3f", locationTitle,
                    mMarkerArray.size(), lat, lon));
            marker.setTag(new PhotoMarkerInfo("Photo", latLng, markerPhotoPath));
        } else {
            marker.setTitle(locationTitle);
            marker.setTag(new LocationMarkerInfo(locationTitle, latLng));
        }
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(new
                CameraPosition.Builder()
                .target(latLng)
                .zoom(13) // City
                .bearing(0) // North
                .tilt(30)
                .build()));
    }

    private void AddPolyLineToMap(Location mLastLocation, Location mCurrentLocation) {
        if (mCurrentLocation == null || mLastLocation == null) {
            return;
        }
        if (mLastLocation.distanceTo(mCurrentLocation) > 3.0f) { // distance in meters
            mMap.addPolyline(new PolylineOptions()
                    .clickable(true)
                    .add(
                            new LatLng(mLastLocation.getLatitude(),
                                    mLastLocation.getLongitude()),
                            new LatLng(mCurrentLocation.getLatitude(),
                                    mCurrentLocation.getLongitude())));
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended called");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionSuspended called");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CURRENT_LOCATION) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
            }
        }
    }

    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and
            // make sure that the Start Updates and Stop Updates buttons are
            // correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        REQUESTING_LOCATION_UPDATES_KEY);
            }

            // Update the value of mCurrentLocation from the Bundle and update the
            // UI to show the correct latitude and longitude.
            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that
                // mCurrentLocationis not null.
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY)) {
                mLastUpdateTime = savedInstanceState.getString(
                        LAST_UPDATED_TIME_STRING_KEY);
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(MapsActivity.TAG, "Potential location change...");
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        if (location != null) {
            Log.d(MapsActivity.TAG, String.format("Last Known Location: LAT=%s, LON=%s", String.valueOf(location.getLatitude()), String.valueOf(location.getLongitude())));
            updateMapUI("Location");
            mLastLocation = mCurrentLocation;
            mCurrentLocation = location;
        }
    }

    private View prepareInfoView(Marker marker) {
        Object o = marker.getTag();

        LinearLayout infoView = new LinearLayout(MapsActivity.this);
        LinearLayout.LayoutParams infoViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        infoView.setOrientation(LinearLayout.HORIZONTAL);
        infoView.setLayoutParams(infoViewParams);

        String markerDateTime = "";
        if (o instanceof PhotoMarkerInfo) {
            PhotoMarkerInfo photoMarkerInfo = (PhotoMarkerInfo) o;
            Bitmap bitmap = getPhotoSized(photoMarkerInfo.getCurrentPhotoPath(), 160);
            ImageView infoImageView = new ImageView(MapsActivity.this);
            infoImageView.setImageBitmap(bitmap);
            infoView.addView(infoImageView);
            markerDateTime = String.format("Taken: %s",
                    DateFormat.getDateTimeInstance().format(photoMarkerInfo.getTimestamp()));
        } else {
            LocationMarkerInfo locationMarkerInfo = (LocationMarkerInfo) o;
            ImageView infoImageView = new ImageView(MapsActivity.this);
            Drawable drawable =
                    getResources().getDrawable(android.R.drawable.ic_dialog_info, this.getTheme());
            infoImageView.setImageDrawable(drawable);
            infoView.addView(infoImageView);
            markerDateTime = String.format("Visited: %s",
                    DateFormat.getDateTimeInstance().format(locationMarkerInfo.getTimestamp()));
        }
        LinearLayout subInfoView = new LinearLayout(MapsActivity.this);
        LinearLayout.LayoutParams subInfoViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subInfoView.setOrientation(LinearLayout.VERTICAL);
        subInfoView.setLayoutParams(subInfoViewParams);
        TextView subInfoLat = new TextView(MapsActivity.this);
        subInfoLat.setText(String.format("Lat: %.3f", marker.getPosition().latitude));
        subInfoView.addView(subInfoLat);
        TextView subInfoLng = new TextView(MapsActivity.this);
        subInfoLng.setText(String.format("Lng: %.3f", marker.getPosition().longitude));
        subInfoView.addView(subInfoLng);
        if (!markerDateTime.isEmpty()) {
            TextView photoTimeTextView = new TextView(MapsActivity.this);
            photoTimeTextView.setText(markerDateTime);
            subInfoView.addView(photoTimeTextView);
        }
        infoView.addView(subInfoView);
        return infoView;
    }

    @Override
    public View getInfoWindow(Marker marker) {
        return null;
    }

    @Override
    public View getInfoContents(Marker marker) {
        return prepareInfoView(marker);
    }

    private class LocationMarkerInfo {
        private final String locationTitle;
        private final LatLng latLng;
        private final Date timestamp;

        public LocationMarkerInfo(String locationTitle, LatLng latLng) {
            this.locationTitle = locationTitle;
            this.latLng = latLng;
            timestamp = new Date();
        }

        public String getLocationTitle() {
            return locationTitle;
        }

        public LatLng getLatLng() {
            return latLng;
        }

        public Date getTimestamp() {
            return timestamp;
        }
    }

    private class PhotoMarkerInfo extends LocationMarkerInfo {
        private final String currentPhotoPath;

        public PhotoMarkerInfo(String locationTitle, LatLng latLng, String currentPhotoPath) {
            super(locationTitle, latLng);
            this.currentPhotoPath = currentPhotoPath;
        }

        public String getCurrentPhotoPath() {
            return currentPhotoPath;
        }

    }

}
