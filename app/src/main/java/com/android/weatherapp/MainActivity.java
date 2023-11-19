package com.android.weatherapp;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.textfield.TextInputLayout;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;



public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_CODE = 1;

    private ProgressBar progressBar;
    private RelativeLayout homeRL,mainRl;
    private TextView cityNameTV, temperatureTV, conditionTV;
    private AutoCompleteTextView cityEdt;
    private ImageView backIV, iconIV, searchIV, loadWeatherIC;
    private RecyclerView weatherRV;

    private ArrayList<WeatherRVModel> weatherRVModalArrayList;
    private WeatherRVAdapter weatherRVAdapter;

    private boolean isDialogShown = false;
    private Geocoder geocoder;
    private TextInputLayout cityTIL;
    private Handler handler = new Handler();
    private Runnable fetchSuggestionsRunnable;
    private LocationAdapter adapter;
    private boolean isSuggestionSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.idPBLoading);
        homeRL = findViewById(R.id.idRLHome);
        cityNameTV = findViewById(R.id.idTVCityName);
        temperatureTV = findViewById(R.id.idTVTemperature);
        conditionTV = findViewById(R.id.idTVCondition);
        weatherRV = findViewById(R.id.idRVWeather);
        cityEdt = findViewById(R.id.idEdtCity);
        backIV = findViewById(R.id.idIVBack);
        iconIV = findViewById(R.id.idIVIcon);
        searchIV = findViewById(R.id.idIVSearch);
        loadWeatherIC = findViewById(R.id.loadWeatherIcon);
        cityTIL=findViewById(R.id.idTILCity);
        mainRl=findViewById(R.id.mainRL);


        weatherRVModalArrayList = new ArrayList<>();
        weatherRVAdapter = new WeatherRVAdapter(this, weatherRVModalArrayList);
        weatherRV.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        weatherRV.setAdapter(weatherRVAdapter);

        // Initialize the Geocoder
        geocoder = new Geocoder(this, Locale.getDefault());

        //If internet connection is not available
        if (!isInternetAvailable()) {
            Toast.makeText(MainActivity.this, "No internet connection available", Toast.LENGTH_SHORT).show();
            // Code to open the notification drawer
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                Intent intent = new Intent(Settings.ACTION_DATA_USAGE_SETTINGS);
                startActivity(intent);
            }else
            {
                Toast.makeText(this, "Please provide Internet Access", Toast.LENGTH_SHORT).show();
                homeRL.setVisibility(View.VISIBLE);
            }

//            Intent intent = new Intent(Intent.ACTION_MAIN);
//            intent.setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$DataUsageSummaryActivity"));
//            startActivity(intent);
            onPause(); //For pausing the activity while granting the internet access.
        }

        // Check location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Location permission granted, get user's current location
            getCurrentLocation(); //To get the user location in lattitude in longitude format.
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.VISIBLE);
            // Request location permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Display a rationale for the location permission
                Toast.makeText(MainActivity.this, "Location permission is required to get the weather information", Toast.LENGTH_SHORT).show();
            }
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_CODE);
        }

        // When focus changes in the TextInputLayout
        cityEdt.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                cityTIL.setHint(null); // Clear the hint text
            } else {
                // Set the hint text back when losing focus
                cityTIL.setHint("Enter City Name");
            }
        });

// When click outside of the TextInputLayout
        mainRl.setOnClickListener(view -> {
            if (cityEdt.hasFocus() && cityEdt.getText().toString().isEmpty()) {
                cityEdt.clearFocus();

                // Close the keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(cityEdt.getWindowToken(), 0);
            }
        });



        //Give Suggestions of location while entering the address

        // Set the initial threshold to show suggestions from the first character
        cityEdt.setThreshold(1);

        // Create a custom adapter to display location suggestions
        adapter = new LocationAdapter(this, new ArrayList<>());
        cityEdt.setAdapter(adapter);

        // Set an item click listener to handle the selected suggestion
        cityEdt.setOnItemClickListener((adapterView, view, position, id) -> {
            String selectedLocation = (String) adapterView.getItemAtPosition(position);
            // Set the suggestion selected flag to true
            isSuggestionSelected = true;
            cityEdt.dismissDropDown();
        });

        // Set a text change listener to fetch location suggestions based on user input
        cityEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                // Cancel any previously scheduled fetchSuggestionsRunnable
                handler.removeCallbacks(fetchSuggestionsRunnable);

                // Schedule the fetchSuggestionsRunnable after a delay (e.g., 500 milliseconds)
                fetchSuggestionsRunnable = new Runnable() {
                    @Override
                    public void run() {
                        String input = charSequence.toString().trim();
                        if (!input.isEmpty() && !isSuggestionSelected) {
                            int threshold=input.length();
                            cityEdt.setThreshold(threshold);
                            fetchLocationSuggestions(input);
                        }
                        // Reset the suggestion selected flag
                        isSuggestionSelected = false;
                    }
                };
                handler.postDelayed(fetchSuggestionsRunnable, 150); // Adjust the delay as needed
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // Not used
            }
            
        });

        //Search button click in keyboard or in the activity
        searchIV.setOnClickListener(view -> performSearch());

        //Keyboard search action invoke
        cityEdt.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });
    }

    //Search button click or keyboard search action
    private void performSearch() {
        String input = cityEdt.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please enter a city name", Toast.LENGTH_SHORT).show();
        } else {
            Pair<Double, Double> location = getLatLngFromLocationName(input);
            if (location != null) {
                double latitude = location.first;
                double longitude = location.second;
                weatherRVModalArrayList.clear();
                getWeatherInfo(latitude, longitude);
                cityEdt.setText("");

                //Close the keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(cityEdt.getWindowToken(), 0);

                // Clear focus from the TextInputEditText
                cityEdt.clearFocus();
            } else {
                Toast.makeText(MainActivity.this, "Enter a valid city name", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Method to fetch location suggestions using Geocoder
    private void fetchLocationSuggestions(String input) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            // Fetch up to 10 suggestions
            List<Address> addresses = geocoder.getFromLocationName(input, 4);
            List<String> suggestions = new ArrayList<>();
            for (Address address : addresses) {
                String suggestion = address.getAddressLine(0);
                // Add the suggestion to the list
                suggestions.add(suggestion);
            }
            // Update the existing adapter with the new suggestions
            adapter.clear();
            adapter.addAll(suggestions);
            adapter.notifyDataSetChanged();

            // Show the dropdown list
            cityEdt.showDropDown();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove any pending fetchSuggestionsRunnable to avoid memory leaks
        handler.removeCallbacks(fetchSuggestionsRunnable);
    }




    //Get user current location
    private void getCurrentLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                LocationRequest locationRequest = new LocationRequest();
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                locationRequest.setInterval(1000);
                locationRequest.setFastestInterval(1000);
                LocationCallback locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        super.onLocationResult(locationResult);
                        if (locationResult != null) {
                            Location location = locationResult.getLastLocation();
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();
                            getWeatherInfo(latitude, longitude);
                            // Remove location updates to conserve battery
                            LocationServices.getFusedLocationProviderClient(MainActivity.this).removeLocationUpdates(this);
                        }
                    }
                };
                LocationServices.getFusedLocationProviderClient(this)
                        .requestLocationUpdates(locationRequest, locationCallback, null);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } else {
            // Location is not enabled, prompt the user to turn it on
            Toast.makeText(MainActivity.this, "Please turn on the location", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
    }

    //Check if the permission is granted??
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Location permission granted, get user's current location
                getCurrentLocation();
            } else {
                // Location permission denied, show a message or handle accordingly
                Toast.makeText(MainActivity.this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //Get lat long from name of the city or area
    private Pair<Double, Double> getLatLngFromLocationName(String locationName) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(locationName, 1);
            if (!addresses.isEmpty()) {
                Address address = addresses.get(0);
                double latitude = address.getLatitude();
                double longitude = address.getLongitude();
                Log.d("LatLng", "Latitude: " + latitude + ", Longitude: " + longitude);
                return new Pair<>(latitude, longitude);
            } else {
                Log.d("LatLng", "No location found for the given name");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    //Parse data from API
    private void getWeatherInfo(double latitude, double longitude) {
        progressBar.setVisibility(View.VISIBLE);
        String api_baseUrl = "https://api.weatherapi.com/v1/forecast.json?key=6fa173ae627e453b976141700231707&q=";
        String url_lat = String.valueOf(latitude);
        String comma = ",";
        String url_long = String.valueOf(longitude);
        String url_query = "&days=1&aqi=yes&alerts=yes";

        String fullUrl = api_baseUrl + url_lat + comma + url_long + url_query;
        Log.e("urlname", fullUrl);

// Create a new JsonObjectRequest with the constructed URL
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, fullUrl, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            // Parse the JSON response
                            String cityName = getLocationName(latitude,longitude);

                            if(cityName.equals("Unknown"))
                            {
                                cityName = response.getJSONObject("location").getString("name");
                                cityNameTV.setText(cityName);
                            }
                            else {
                                cityNameTV.setText(cityName);
                            }

                            String temperature = response.getJSONObject("current").getString("temp_c");
                            temperatureTV.setText(temperature + "°c");

                            int isDay = response.getJSONObject("current").getInt("is_day");
                            String condition = response.getJSONObject("current").getJSONObject("condition").getString("text");
                            String conditionIcon = response.getJSONObject("current").getJSONObject("condition").getString("icon");
                            String url_param = "https:";
                            String cond_url = url_param + conditionIcon;
                            Picasso.get().load(cond_url).into(iconIV);
                            conditionTV.setText(condition);

                            String backgroundImageUrl = isDay == 1
                                    ? "https://images.unsplash.com/photo-1566228015668-4c45dbc4e2f5?ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D&auto=format&fit=crop&w=1500&q=80"
                                    : "https://images.unsplash.com/photo-1532074534361-bb09a38cf917?ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&ixlib=rb-1.2.1&auto=format&fit=crop&w=1500&q=80";
                            Picasso.get().load(backgroundImageUrl).into(backIV);

                            JSONObject forecastObj = response.getJSONObject("forecast");
                            JSONArray forecastArray = forecastObj.getJSONArray("forecastday");
                            if (forecastArray.length() > 0) {
                                JSONObject forecast = forecastArray.getJSONObject(0);
                                JSONArray hourArray = forecast.getJSONArray("hour");
                                for (int i = 0; i < hourArray.length(); i++) {
                                    JSONObject hourObj = hourArray.getJSONObject(i);
                                    String time = hourObj.getString("time");
                                    String temper = hourObj.getString("temp_c");
                                    String img = hourObj.getJSONObject("condition").getString("icon");
                                    String wind = hourObj.getString("wind_kph");
                                    weatherRVModalArrayList.add(new WeatherRVModel(time, temper, img, wind));
                                }
                            }
                            weatherRVAdapter.notifyDataSetChanged();
                            homeRL.setVisibility(View.VISIBLE);
                            progressBar.setVisibility(View.GONE);
                            loadWeatherIC.setVisibility(View.GONE);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, String.valueOf(e), Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(MainActivity.this, String.valueOf(error), Toast.LENGTH_SHORT).show();
                        Log.e("FGHIJ", "Error: " + error.getMessage());
                    }
                });

        // Add the request to the request queue
        Volley.newRequestQueue(this).add(jsonObjectRequest);

    }

    //Get exact name of the Location form geoCoder
    private String getLocationName(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        String areaName = "";

        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (!addresses.isEmpty()) {
                Address address = addresses.get(0);
                // Get the area or sub-locality name
                areaName = address.getSubLocality();
                if (areaName == null || areaName.isEmpty()) {
                    // Get the locality or city name if area name is not available
                    String locality = address.getLocality();
                    if (locality == null || locality.isEmpty()) {
                        areaName = "Unknown";
                    } else {
                        areaName = locality;
                    }

                } else {
                    // No address found for the given coordinates
                    areaName = "Unknown";
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            // Error occurred during geocoding
            areaName = "Unknown";
        }

        return areaName;
    }
    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Location permission granted, get user's current location
            getCurrentLocation();
        } else {
            // Request location permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Display a rationale for the location permission
                Toast.makeText(MainActivity.this, "Location permission is required to get the weather information", Toast.LENGTH_SHORT).show();
            }
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_CODE);
        }
    }

    @Override
    public void onBackPressed() {
        if (isDialogShown) {
            super.onBackPressed();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Do you want to exit?").setPositiveButton("Yes", (dialog, id) -> {
                isDialogShown = false;
                finish();
            }).setNegativeButton("No", (dialog, id) -> {
                isDialogShown = false;
                dialog.dismiss();
            });
            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(dialogInterface -> isDialogShown = true);
            dialog.show();
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }


}
