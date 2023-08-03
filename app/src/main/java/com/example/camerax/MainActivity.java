package com.example.camerax;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.app.NotificationManagerCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import android.Manifest;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {
    private static final String CHANNEL_ID = "my_channel_id";
    private static final int NOTIFICATION_ID = 1;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private static final int REQUEST_CODE_LOCATION_PERMISSION = 1;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 2;
    private static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION = 3;
    Button btnpicture;
    Button btnshare;
    ImageView imageView;
    TextView locationTV;
    ActivityResultLauncher<Intent> activityResultLauncher;
    String locationString;
    Uri imguri;
    String channelId = "silent_channel_id";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedPreferences = getSharedPreferences("MyPref", Context.MODE_PRIVATE);
        setContentView(R.layout.activity_main);
        btnpicture = findViewById(R.id.btncamera_id);
        btnshare = findViewById(R.id.btnshare_id);
        imageView = findViewById(R.id.image);
        locationTV = findViewById(R.id.location);
        locationTV.setText("Location: ");
        Log.i("TAG", "onCreate: " + imguri);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_LOCATION_PERMISSION);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4);
        }
        if (sharedPreferences.contains("photo")){
            imageView.setImageURI(Uri.parse(sharedPreferences.getString("photo", "")));
        }

        // Request location permission when the button is clicked
        btnpicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check for location permission before capturing the picture
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                    takePictureAndFetchLocation();
                } else {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
                    }
                }
            }
        });

        btnshare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                if (imguri == null){
                    Toast.makeText(v.getContext(), "No Image to Share", Toast.LENGTH_SHORT).show();
                } else {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("image/jpeg");
                        intent.putExtra(Intent.EXTRA_STREAM, imguri);
                        startActivity(Intent.createChooser(intent, "Share Image"));
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION);
                    }
                }

            }
        });

        // ActivityResultLauncher to handle the camera result
        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                Bundle extras = result.getData().getExtras();
                Uri imageUri;
                Bitmap imageBitmap = (Bitmap) extras.get("data");
                WeakReference<Bitmap> result_1 = new WeakReference<>(Bitmap.createScaledBitmap(imageBitmap,imageBitmap.getWidth(), imageBitmap.getHeight(), false).copy(Bitmap.Config.RGB_565, true));
                Bitmap bm = result_1.get();
                imageUri = saveImage(bm, MainActivity.this);
                imageView.setImageURI(imageUri);
                imguri = imageUri;
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("photo", imageUri.toString());
                editor.apply();
                // Fetch and display location after the picture is taken
                fetchLocation();
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "My Channel";
            String description = "My Channel Description";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            // Register the channel with the system
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence channelName = "Silent Channel";
            String channelId = "silent_channel_id";
            int importance = NotificationManager.IMPORTANCE_LOW; // Adjust importance as needed

            NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
            channel.setSound(null, null); // Make the channel silent

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Notification Title")
                .setContentText("This is a sample notification.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }



    private void takePictureAndFetchLocation() {
        NotificationManagerCompat notificationManager2 = NotificationManagerCompat.from(this);
        notificationManager2.cancel(NOTIFICATION_ID);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        activityResultLauncher.launch(cameraIntent);
    }

    private void fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // You have location permission. Proceed with getting the location.
            fusedLocationProviderClient.requestLocationUpdates(createLocationRequest(), locationCallback, null);
        }
    }

    private LocationRequest createLocationRequest() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000); // Update location every 10 seconds (adjust as needed)
        return locationRequest;
    }

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult != null) {
                Location location = locationResult.getLastLocation();
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                locationString = "Location: " + String.valueOf(latitude) + ", " + String.valueOf(longitude);
                locationTV.setText(locationString);
            }
        }
    };

    private Uri saveImage(Bitmap image, MainActivity context) {
        File imagefolder = new File(context.getCacheDir(), "images");
        Uri uri = null;
        try{
            imagefolder.mkdirs();
            File file = new File(imagefolder, "captured_image.jpg");
            FileOutputStream stream = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.flush();
            stream.close();
            uri = FileProvider.getUriForFile(context.getApplicationContext(), "com.example.camerax.provider", file);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return uri ;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i("TAG", "onRequestPermissionsResult: " + requestCode + " " + grantResults[0]);
    }
}