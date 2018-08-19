package com.example.hyu13.werehere;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    private Button mLogout, mRequest;
    private Boolean isLoggingOut = false;

    private SupportMapFragment mapFragment;
    private LatLng meetLocation;

    private String loomerId = "";
    private boolean requestBol = false;

    private FusedLocationProviderClient mFusedLocationProviderClient;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);


        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }else{
            mapFragment.getMapAsync(this);
        }

        //logout and take back user to main screen
        mLogout = (Button) findViewById(R.id.logout);
        mLogout.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                isLoggingOut = true;

                disconnectDriver();
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(MapsActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        mRequest = (Button) findViewById(R.id.request);
        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(requestBol){
                    requestBol = false;
                    geoQuery.removeAllListeners();
                    assignedLoomerLocation.removeEventListener(assignedLoomerLocationListener);
                }else{
                    requestBol = true;

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("LoomerRequest");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                    //to remove markers
                    if(mLoomerMarker != null){
                        mLoomerMarker.remove();
                    }

                    meetLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    mMap.addMarker(new MarkerOptions().position(meetLocation).title("Let's Meet Here!"));

                    mRequest.setText("Locating fellow Loomer....");

                    getLoomer();
                }

            }
        });

        getAssignedLoomer();
    }

    private void getAssignedLoomer(){
        String meeterId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child(meeterId); //.child("loomerRideId");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    loomerId = dataSnapshot.getValue().toString();
                    getAssignedLocation();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private DatabaseReference assignedLoomerLocation;
    private ValueEventListener assignedLoomerLocationListener;

    private void getAssignedLocation(){
        assignedLoomerLocation = FirebaseDatabase.getInstance().getReference().child("LoomerRequest").child(loomerId).child("l");
        assignedLoomerLocationListener = assignedLoomerLocation.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
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
                    //mMap.addMarker(new MarkerOptions().position(loomerLatLng).title("Meet Location"));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private int radius = 20;    //locating loomers within 20 miles
    private Boolean loomerFound = false;
    private String loomerFoundID;

    GeoQuery geoQuery;
    private void getLoomer(){
        DatabaseReference loomerLocation = FirebaseDatabase.getInstance().getReference().child("UsersAvailable");

        GeoFire geoFire = new GeoFire(loomerLocation);
        geoQuery = geoFire.queryAtLocation(new GeoLocation(meetLocation.latitude, meetLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {    //key = ID of driver
                if(!loomerFound) {
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
                        mRequest.setText("Loomer Distance: " + String.valueOf(distance));
                    }

                    mLoomerMarker = mMap.addMarker(new MarkerOptions().position(loomerLatLng).title("Your Loomer"));
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
                //increment miles by 20 if loomer not found and repeat until found
                if(!loomerFound){
                    radius+=20;
                    getLoomer();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private Marker mLoomerMarker;
    private void getLoomerLocation(){

        DatabaseReference loomerLocationRef = FirebaseDatabase.getInstance().getReference().child("LoomersLooming").child(loomerFoundID).child("l");
        loomerLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
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

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    protected synchronized void buildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).addApi(LocationServices.API).build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        if(getApplicationContext()!= null) {

            mLastLocation = location;
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            //to move camera with the user
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

            String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();    //get userID that is currently logged in
            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("UsersAvailable");  //for currently available users
            DatabaseReference refNotAvailable = FirebaseDatabase.getInstance().getReference("LoomersLooming");  //for currently NOT available users

            GeoFire geoFireAvailable = new GeoFire(refAvailable); //where we want the geofire to store our data
            GeoFire geoFireNotAvailable = new GeoFire(refNotAvailable); //where we want the geofire to store our data

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

    final int LOCATION_REQUEST_CODE = 1;

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    //For Map to Work with Location Services
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case LOCATION_REQUEST_CODE:{
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mapFragment.getMapAsync(this);
                }else{
                    Toast.makeText(getApplicationContext(),"Please Accept Permission", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private void disconnectDriver(){
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();    //get userID that is currently logged in
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("UsersAvailable");  //for currently available users
        DatabaseReference ref1 = FirebaseDatabase.getInstance().getReference("LoomersLooming");  //for currently available users
        DatabaseReference ref2 = FirebaseDatabase.getInstance().getReference("LoomerRequest");

        //fix this
        DatabaseReference user = FirebaseDatabase.getInstance().getReference("Users").child(userID); //.child("loomerRideId");
        //DatabaseReference user = FirebaseDatabase.getInstance().getReference("Users").child(userID).child("loomerRideId"); //OG

        GeoFire geoFire = new GeoFire(ref);
        GeoFire geoFire1 = new GeoFire(ref1);
        GeoFire geoFire2 = new GeoFire(ref2);
        GeoFire geoFireUser = new GeoFire(user);
        geoFire.removeLocation(userID);
        geoFire1.removeLocation(userID);
        geoFire2.removeLocation(userID);
        geoFireUser.removeLocation(userID);
    }
    @Override
    protected void onStop() {
        super.onStop();
        if(!isLoggingOut) {
            disconnectDriver();
        }
    }
}
