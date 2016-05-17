package com.akaver.gmapdemo02;
import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.w3c.dom.Text;

import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener {

    private final static String TAG = "MainActivity";

    private GoogleMap mGoogleMap;
    private Menu mOptionsMenu;

    private LocationManager locationManager;

    private String provider;

    private int markerCount = 0;
    private Location locationPrevious;
    //private double totalDistance;

    private Polyline mPolyline;
    private PolylineOptions mPolylineOptions;


    private TextView textViewWPCount;
    private TextView textViewSpeed;
    private TextView textViewTotalDistanceLine;
    private TextView textViewTotalDistance;
    private TextView textViewResettedDistanceLine;
    private TextView textViewResettedDistance;
    private TextView textViewWayPointDistanceLine;
    private TextView textViewWayPointDistance;
    //Notification textViews
    //private TextView textViewTripmeterMetrics;
    //private TextView textViewWayPointMetrics;

    //values to calculate speed
    private double firstLat = 0;
    private double firstLng = 0;
    //values to calculate distances
    private double realFirstLat = 0;
    private double realFirstLng = 0;
    private double resetLat = 0;
    private double resetLng = 0;
    private double wayPointLat = 0;
    private double wayPointLng = 0;

    //values to display to user
    private long totalDistance = 0; //real distance that the user has travelled
    private long distance = 0; //real distance between two latest WPs
    private long birdDistance = 0; //straight cut distance between first and last WPs
    private long resetDistance = 0; //real distance since reset
    private long birdResetDistance = 0; //birds distance since reset
    private long wayPointDistance = 0;
    private long birdWayPointDistance = 0;

    private NotificationManager mNotificationManager;
    private BroadcastReceiver mBroadcastReceiver;
    private boolean notificationCalled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if(action.equals("notification-broadcast-addwaypoint")) {
                    buttonAddWayPointClicked(null);
                } else if (action.equals("notification-broadcast-resettripmeter")) {
                    buttonCResetClicked(null);
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("notification-broadcast");
        intentFilter.addAction("notification-broadcast-addwaypoint");
        intentFilter.addAction("notification-broadcast-resettripmeter");
        registerReceiver(mBroadcastReceiver, intentFilter);


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");

        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");

        }
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        //check if GPS is turned on
        boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        //if not turned on, send user to settings menu
        if(!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        } else {
            Criteria criteria = new Criteria();

            // get the location provider (GPS/CEL-towers, WIFI)
            provider = locationManager.getBestProvider(criteria, false);

            //Log.d(TAG, "PROVIDER ON SIIIIN!!!!! ----------------- " + provider);
            locationPrevious = locationManager.getLastKnownLocation(provider);

            if (locationPrevious != null) {
                // do something with initial position?
            }


            textViewWPCount = (TextView) findViewById(R.id.textview_wpcount);
            textViewSpeed = (TextView) findViewById(R.id.textview_speed);
            textViewTotalDistance = (TextView) findViewById(R.id.textview_total_distance);
            textViewTotalDistanceLine = (TextView) findViewById(R.id.textview_total_line);
            textViewResettedDistanceLine = (TextView) findViewById(R.id.textview_creset_line);
            textViewResettedDistance = (TextView) findViewById(R.id.textview_creset_distance);
            textViewWayPointDistanceLine = (TextView) findViewById(R.id.textview_wp_line);
            textViewWayPointDistance = (TextView) findViewById(R.id.textview_wp_distance);
            //textViewTripmeterMetrics = (TextView) findViewById(R.id.textViewTripmeterMetrics);
            //textViewWayPointMetrics = (TextView) findViewById(R.id.textViewWayPointMetrics);

        }

        final Handler h = new Handler();
        final int delay = 1000; //milliseconds

        h.postDelayed(new Runnable(){
            public void run(){

                if(locationPrevious != null) {
                    //get new latitude and longitude
                    double lastLat = locationPrevious.getLatitude();
                    double lastLng = locationPrevious.getLongitude();

                    //cuz firstLat and firstLng were just initialized, give them values
                    if(firstLat == 0 && firstLng == 0) {
                        firstLat = lastLat;
                        firstLng = lastLng;
                    }

                    if(realFirstLat == 0 && realFirstLng == 0) {
                        realFirstLat = lastLat;
                        realFirstLng = lastLng;
                    }

                    if(resetLat == 0 && resetLng == 0) {
                        resetLat = lastLat;
                        resetLng = lastLng;
                    }

                    //calculate bird/straight distance
                    birdResetDistance = calculateDistance(resetLat, resetLng, lastLat, lastLng);
                    distance = calculateDistance(firstLat, firstLng, lastLat, lastLng);
                    birdDistance = calculateDistance(realFirstLat, realFirstLng, lastLat, lastLng);
                    //calculate totalDistance (real distance not straight line)
                    totalDistance += distance;
                    //calculate real distance after reset
                    resetDistance += distance;

                    if(wayPointLat != 0 || wayPointLng != 0) {
                        birdWayPointDistance = calculateDistance(wayPointLat, wayPointLng, lastLat, lastLng);
                        wayPointDistance += distance;
                    }

                    firstLat = lastLat; //set lastLat as firstLat, so next time a new value can be calculated
                    firstLng = lastLng; //-------||------------
                }

                //Notification textViews
                if(notificationCalled) {

                    //textViewTripmeterMetrics.setText(Long.toString(totalDistance)); //setText(Long.toString(totalDistance))
                    //textViewWayPointMetrics.setText(Long.toString(wayPointDistance));
                }
                //In app textViews
                textViewWayPointDistanceLine.setText(Long.toString(birdWayPointDistance));
                textViewWayPointDistance.setText(Long.toString(wayPointDistance));
                textViewResettedDistanceLine.setText(Long.toString(birdResetDistance));
                textViewResettedDistance.setText(Long.toString(resetDistance));
                textViewTotalDistance.setText(Long.toString(totalDistance));
                textViewTotalDistanceLine.setText(Long.toString(birdDistance));
                textViewSpeed.setText(Long.toString(distance) + "m/s");

                h.postDelayed(this, delay);
            }
        }, delay);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        mOptionsMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.menu_mylocation:
                item.setChecked(!item.isChecked());
                updateMyLocation();
                return true;
            case R.id.menu_trackposition:
                item.setChecked(!item.isChecked());
                updateTrackPosition();
                return true;
            case R.id.menu_keepmapcentered:
                item.setChecked(!item.isChecked());
                return true;
            case R.id.menu_map_type_hybrid:
            case R.id.menu_map_type_none:
            case R.id.menu_map_type_normal:
            case R.id.menu_map_type_satellite:
            case R.id.menu_map_type_terrain:
                item.setChecked(true);
                updateMapType();
                return true;

            case R.id.menu_map_zoom_10:
            case R.id.menu_map_zoom_15:
            case R.id.menu_map_zoom_20:
            case R.id.menu_map_zoom_in:
            case R.id.menu_map_zoom_out:
            case R.id.menu_map_zoom_fittrack:
                updateMapZoomLevel(item.getItemId());
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }


    }


    private void updateMapZoomLevel(int itemId) {
        if (!checkReady()) {
            return;
        }

        switch (itemId) {
            case R.id.menu_map_zoom_10:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(10));
                break;
            case R.id.menu_map_zoom_15:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(15));
                break;
            case R.id.menu_map_zoom_20:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(20));
                break;
            case R.id.menu_map_zoom_in:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomIn());
                break;
            case R.id.menu_map_zoom_out:
                mGoogleMap.moveCamera(CameraUpdateFactory.zoomOut());
                break;
            case R.id.menu_map_zoom_fittrack:
                updateMapZoomFitTrack();
                break;
        }
    }

    private void updateMapZoomFitTrack() {
        if (mPolyline == null) {
            return;
        }

        List<LatLng> points = mPolyline.getPoints();

        if (points.size() <= 1) {
            return;
        }
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }
        LatLngBounds bounds = builder.build();
        int padding = 0; // offset from edges of the map in pixels
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));

    }

    private void updateTrackPosition() {
        if (!checkReady()) {
            return;
        }

        if (mOptionsMenu.findItem(R.id.menu_trackposition).isChecked()) {
            mPolylineOptions = new PolylineOptions().width(5).color(Color.RED);
            mPolyline = mGoogleMap.addPolyline(mPolylineOptions);
        }


    }

    private void updateMapType() {
        if (!checkReady()) {
            return;
        }

        if (mOptionsMenu.findItem(R.id.menu_map_type_normal).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_hybrid).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_none).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_NONE);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_satellite).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else if (mOptionsMenu.findItem(R.id.menu_map_type_terrain).isChecked()) {
            mGoogleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        }

    }

    private boolean checkReady() {
        if (mGoogleMap == null) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void updateMyLocation() {
        if (mOptionsMenu.findItem(R.id.menu_mylocation).isChecked()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mGoogleMap.setMyLocationEnabled(true);
            return;
        }

        mGoogleMap.setMyLocationEnabled(false);
    }

    public void buttonAddWayPointClicked(View view){
        if (locationPrevious==null){
            return;
        }

        markerCount++;

        wayPointLat = locationPrevious.getLatitude();
        wayPointLng = locationPrevious.getLongitude();
        wayPointDistance = 0;
        //siin saaks asendada locationPrevious.getLatitude... ja teise kysimise eelnevate muutujas olevate vaartustega
        mGoogleMap.addMarker(new MarkerOptions().position(new LatLng(locationPrevious.getLatitude(), locationPrevious.getLongitude())).title(Integer.toString(markerCount)));
        textViewWPCount.setText(Integer.toString(markerCount));
    }

    public void buttonCResetClicked(View view){
        if (locationPrevious==null){
            return;
        }

        resetDistance = 0;
        birdResetDistance = 0;
        resetLat = 0;
        resetLng = 0;

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;

        //mGoogleMap.setMyLocationEnabled(false);

        //LatLng latLngITK = new LatLng(59.3954789, 24.6621282);
        //mGoogleMap.addMarker(new MarkerOptions().position(latLngITK).title("ITK"));
        //mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngITK, 17));

        // set zoom level to 15 - street
        mGoogleMap.moveCamera(CameraUpdateFactory.zoomTo(17));

        // if there was initial location received, move map to it
        if (locationPrevious != null) {
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(locationPrevious.getLatitude(), locationPrevious.getLongitude())));
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng newPoint = new LatLng(location.getLatitude(), location.getLongitude());

        if (mGoogleMap==null) return;

        if (mOptionsMenu.findItem(R.id.menu_keepmapcentered).isChecked() || locationPrevious == null) {
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(newPoint));
        }

        if (mOptionsMenu.findItem(R.id.menu_trackposition).isChecked()) {
            List<LatLng> points = mPolyline.getPoints();
            points.add(newPoint);
            mPolyline.setPoints(points);
        }
        locationPrevious = location;
    //    notificationCustomLayout();
        //Log.d(TAG, "Location Updated..................................");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }


    @Override
    protected void onResume(){
        super.onResume();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");
        }

        if (locationManager!=null){
            locationManager.requestLocationUpdates(provider, 500, 1, this);
        }
    }


    @Override
    protected void onPause(){
        super.onPause();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No COARSE location permissions!");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No FINE location permissions!");
        }

        if (locationManager!=null){
            locationManager.removeUpdates(this);
        }
/*
        // get the view layout
        RemoteViews remoteView = new RemoteViews(
                getPackageName(), R.layout.custom_notification);

        //Update textView values in custom Notification
        remoteView.setTextViewText(R.id.textViewTripmeterMetrics, Long.toString(totalDistance));
        remoteView.setTextViewText(R.id.textViewWayPointMetrics, Long.toString(wayPointDistance));
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setContent(remoteView)
                        .setSmallIcon(R.drawable.ic_my_location_white_48dp);


        mNotificationManager.notify(0, mBuilder.build());
*/
        /*final Handler h = new Handler();
        final int delay = 1000; //milliseconds

        h.postDelayed(new Runnable(){
            public void run(){
                h.postDelayed(this, delay);
            }
        }, delay);*/
        //notificationCustomLayout();
    }

    @Override
    protected void onDestroy() {
        //unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    private long calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        long distanceInMeters = Math.round(6371000 * c);
        return distanceInMeters;
    }
/*
    public void notificationCustomLayout(){

        // get the view layout
        RemoteViews remoteView = new RemoteViews(
                getPackageName(), R.layout.custom_notification);

        //Update textView values in custom Notification
        remoteView.setTextViewText(R.id.textViewTripmeterMetrics, Long.toString(totalDistance));
        remoteView.setTextViewText(R.id.textViewWayPointMetrics, Long.toString(wayPointDistance));

        // define intents
        PendingIntent pIntentAddWaypoint = PendingIntent.getBroadcast(
                this,
                0,
                new Intent("notification-broadcast-addwaypoint"),
                0
        );

        PendingIntent pIntentResetTripmeter = PendingIntent.getBroadcast(
                this,
                0,
                new Intent("notification-broadcast-resettripmeter"),
                0
        );

        // bring back already running activity
        // in manifest set android:launchMode="singleTop"
        PendingIntent pIntentOpenActivity = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // attach events
        remoteView.setOnClickPendingIntent(R.id.buttonAddWayPoint, pIntentAddWaypoint);
        remoteView.setOnClickPendingIntent(R.id.buttonResetTripmeter, pIntentResetTripmeter);
        remoteView.setOnClickPendingIntent(R.id.buttonOpenActivity, pIntentOpenActivity);

        // build notification
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setContent(remoteView)
                        .setSmallIcon(R.drawable.ic_my_location_white_48dp);



        mNotificationManager.notify(0, mBuilder.build());
        //notificationCalled = true;
    }
*/
}