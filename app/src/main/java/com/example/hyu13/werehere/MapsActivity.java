package com.example.hyu13.werehere;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback{ //might not need GoogleApiClient

    private GoogleMap mMap;
    //GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    GeoQuery geoQuery;
    Marker meetMarker;

    private Button mLogout, mRequest, mProfile;
    private Switch mLocationSwitch;
    private Boolean isLoggingOut = false;

    private SupportMapFragment mapFragment;
    private LatLng meetLocation;

    private String loomerId = "";
    private boolean requestBol = false;

    private int radius = 50;    //locating loomers within 50 km
    private Boolean loomerFound = false;
    private String loomerFoundID;

    private Marker loomerMarker;
    private Marker mLoomerMarker;

    private DatabaseReference assignedLoomerLocation;
    private ValueEventListener assignedLoomerLocationListener;

    private FusedLocationProviderClient mFusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);



        /*
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }else{
            mapFragment.getMapAsync(this);
        }
        */

        //logout and take back user to main screen
        mLogout = (Button) findViewById(R.id.logout);
        mLogout.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                isLoggingOut = true;

                disconnectDriver();
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(MapsActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        mProfile = (Button) findViewById(R.id.Profile);
        mProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MapsActivity.this, InfoActivity.class);
                startActivity(intent);
                return;
            }
        });

        mLocationSwitch = (Switch) findViewById(R.id.locationSwitch);
        mLocationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    connectLocation();
                }else{
                    disconnectDriver();
                }
            }
        });

        mRequest = (Button) findViewById(R.id.request);
        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(requestBol){ //to cancel request after tapping again
                    requestBol = false;
                    geoQuery.removeAllListeners();
                    assignedLoomerLocation.removeEventListener(assignedLoomerLocationListener);

                    if(loomerFoundID != null){
                        DatabaseReference loomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child(loomerFoundID);
                        loomerRef.setValue(true);
                        loomerFoundID = null;
                    }
                    loomerFound = false;
                    radius = 50;
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("MetRequest");

                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.removeLocation(userId);

                    //remove marker on cancel request
                    if(loomerMarker != null){
                        loomerMarker.remove();
                    }
                    mRequest.setText("Loom");

                }else{
                    requestBol = true;

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("MetRequest");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                    meetLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    loomerMarker = mMap.addMarker(new MarkerOptions().position(meetLocation).title("Let's Meet Here!"));

                    mRequest.setText("Locating fellow Loomer....");

                    getLoomer();
                }
            }
        });

        getAssignedLoomer();
    }

    private void getAssignedLoomer(){
        String meeterId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child(meeterId).child("loomerRideId");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){

                    loomerId = dataSnapshot.getValue().toString();
                    getAssignedLocation();
                }else{
                    loomerId = "";
                    if(loomerMarker != null){
                        meetMarker.remove();
                    }
                    if(assignedLoomerLocationListener != null) {
                        assignedLoomerLocation.removeEventListener(assignedLoomerLocationListener);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void getLoomer(){
        DatabaseReference loomerLocation = FirebaseDatabase.getInstance().getReference().child("UsersAvailable");

        GeoFire geoFire = new GeoFire(loomerLocation);
        geoQuery = geoFire.queryAtLocation(new GeoLocation(meetLocation.latitude, meetLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {    //key = ID of driver
                if(!loomerFound && requestBol) {
                    loomerFound = true;
                    loomerFoundID = key;

                    DatabaseReference loomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child(loomerFoundID);
                    String loomerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    HashMap map = new HashMap();
                    map.put("loomerRideId", loomerId);
                    loomerRef.updateChildren(map);

                    getLoomerLocation();
                    mRequest.setText("Found!"); //change text after loomer has been found

                    //not og
                    double locationLat = 0;
                    double locationLng = 0;
                    LatLng loomerLatLng = new LatLng(locationLat, locationLng);
                    //to remove markers o
                    if(mLoomerMarker != null){
                        mLoomerMarker.remove();
                    }
                    Location loc1 = new Location("");
                    loc1.setLatitude(meetLocation.latitude);
                    loc1.setLongitude(meetLocation.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(loomerLatLng.latitude);
                    loc2.setLongitude(loomerLatLng.longitude);

                    float distance = loc1.distanceTo(loc2);
                    if(distance<100){
                        mRequest.setText("Loomer Arrived");
                    }else{
                        //mRequest.setText("Loomer Distance: " + String.valueOf(distance));
                        mRequest.setText("Loomer Found");
                    }

                    mLoomerMarker = mMap.addMarker(new MarkerOptions().position(loomerLatLng).title("Loomer"));
                    //end og
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                //increment km by 50 if loomer not found and repeat until 150km
                if(!loomerFound && radius < 500){
                    radius+=20;
                    getLoomer();
                }
                /*
                else{
                    Toast.makeText(MapsActivity.this, "No Active Loomers in the Area", Toast.LENGTH_LONG).show();
                    if(loomerMarker != null){
                        loomerMarker.remove();
                    }
                    mRequest.setText("Loom");
                }
                */
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void getAssignedLocation(){
        assignedLoomerLocation = FirebaseDatabase.getInstance().getReference().child("MetRequest").child(loomerId).child("l");
        assignedLoomerLocationListener = assignedLoomerLocation.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && !loomerId.equals("")){
                    List<Object> map = (List<Object>)dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    if(map.get(0) != null) {    //in firebase the lat is 0
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null) {    //in firebase the lng is 1
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }


                    LatLng loomerLatLng = new LatLng(locationLat, locationLng);
                    meetMarker = mMap.addMarker(new MarkerOptions().position(loomerLatLng).title("Meet Location")); //.icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_loomer))); //DEBUG erased before and removed marker
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void getLoomerLocation(){
        DatabaseReference loomerLocationRef = FirebaseDatabase.getInstance().getReference().child("MetUp").child(loomerFoundID).child("l");
        loomerLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && requestBol){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    mRequest.setText("Loomers Found!");
                    if(map.get(0) != null) {    //in firebase the lat is 0
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null) {    //in firebase the lng is 1
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng loomerLatLng = new LatLng(locationLat, locationLng);
                    //to remove markers o
                    if(mLoomerMarker != null){
                        mLoomerMarker.remove();
                    }
                    Location loc1 = new Location("");
                    loc1.setLatitude(meetLocation.latitude);
                    loc1.setLongitude(meetLocation.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(loomerLatLng.latitude);
                    loc2.setLongitude(loomerLatLng.longitude);

                    float distance = loc1.distanceTo(loc2);

                    mRequest.setText("Loomer Distance: " + String.valueOf(distance));

                    mLoomerMarker = mMap.addMarker(new MarkerOptions().position(loomerLatLng).title("Your Loomer"));

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
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

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            }else{
                checkLocationPermission();
            }
        }
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }

    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for(Location location : locationResult.getLocations()){
                if(getApplicationContext()!= null) {

                    mLastLocation = location;
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

                    //to move camera with the user
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    mMap.animateCamera(CameraUpdateFactory.zoomTo(10));

                    String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();    //get userID that is currently logged in
                    DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("UsersAvailable");  //for currently available users
                    DatabaseReference refNotAvailable = FirebaseDatabase.getInstance().getReference("MetUp");  //for currently NOT available users

                    GeoFire geoFireAvailable = new GeoFire(refAvailable); //where we want the geofire to store our data
                    GeoFire geoFireNotAvailable = new GeoFire(refNotAvailable); //where we want the geofire to store our data

                    if(!getUsersAround) {
                        showUsers();
                    }

                    //turns non available to available
                    switch(loomerId){
                        case  "":
                            geoFireNotAvailable.removeLocation(userID);
                            geoFireAvailable.setLocation(userID, new GeoLocation(location.getLatitude(), location.getLongitude())); //pass long and lat to the child userID
                            break;

                        default:
                            geoFireAvailable.removeLocation(userID);
                            geoFireNotAvailable.setLocation(userID, new GeoLocation(location.getLatitude(), location.getLongitude())); //pass long and lat to the child userID
                            break;
                    }

                }
            }
        }
    };

    private void checkLocationPermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                new AlertDialog.Builder(this).setTitle("Permission Needed").setMessage("Allow Permission for Location")
                        .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                            }
                        }).create().show();
            }
            else{
                ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

            }
        }
    }

    //For Map to Work with Location Services
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case 1:{
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                }else{
                    Toast.makeText(getApplicationContext(),"Please Accept Permission", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private void connectLocation(){
        checkLocationPermission();
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }

    private void disconnectDriver(){
        if(mFusedLocationClient != null){
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
        String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();    //get userID that is currently logged in
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("UsersAvailable");  //for currently available users
        DatabaseReference ref1 = FirebaseDatabase.getInstance().getReference("MetUp");  //for currently available users
        DatabaseReference ref2 = FirebaseDatabase.getInstance().getReference("MetRequest");

        //fix this
        //DatabaseReference user = FirebaseDatabase.getInstance().getReference("Users").child(userID); //.child("loomerRideId");
        DatabaseReference user = FirebaseDatabase.getInstance().getReference("Users").child(userID).child("loomerRideId"); //OG

        GeoFire geoFire = new GeoFire(ref);
        GeoFire geoFire1 = new GeoFire(ref1);
        GeoFire geoFire2 = new GeoFire(ref2);
        GeoFire geoFireUser = new GeoFire(user);
        geoFire.removeLocation(userID);
        geoFire1.removeLocation(userID);
        geoFire2.removeLocation(userID);
        geoFireUser.removeLocation(userID);
    }

    boolean getUsersAround = false;
    List<Marker> markerList = new ArrayList<Marker>();
    private void showUsers(){
        getUsersAround = true;
        DatabaseReference usersLocation = FirebaseDatabase.getInstance().getReference().child(("UsersAvailable"));
        GeoFire geoFire = new GeoFire(usersLocation);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()), 5000);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                for(Marker markerIt: markerList){
                    if(markerIt.getTag().equals(key)){
                        return;
                    }
                    LatLng userLocation = new LatLng(location.latitude, location.longitude);

                    Marker mUserMarker = mMap.addMarker(new MarkerOptions().position(userLocation).title(key));
                    mUserMarker.setTag(key);
                    markerList.add(mUserMarker) ;
                }
            }

            @Override
            public void onKeyExited(String key) {
                for(Marker markerIt: markerList){
                    if(markerIt.getTag().equals(key)){
                        markerIt.remove();
                        markerList.remove(markerIt);
                        return;
                    }
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                for(Marker markerIt: markerList){
                    if(markerIt.getTag().equals(key)){
                        markerIt.setPosition(new LatLng(location.latitude, location.longitude));
                    }
                }
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }
}
