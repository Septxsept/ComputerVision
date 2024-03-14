package com.example.cameradetection;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.cameradetection.ml.SsdMobilenetV11Metadata1;

import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActvity2 extends AppCompatActivity {

    List<Integer> colors = Arrays.asList(
            Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY,
            Color.BLACK, Color.DKGRAY, Color.MAGENTA, Color.YELLOW
    );

    private Button goBack;
    private Button selectImage;
    private ImageView imageView;
    private List<String> labels = new ArrayList<>();
    private   SsdMobilenetV11Metadata1 model;
    private ImageProcessor imageProcessor;
    private    Paint paint = new Paint();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_actvity2);
        get_permission();
        selectImage = (Button) findViewById(R.id.selectImage);
        imageView = (ImageView) findViewById(R.id.imageView);



        // Load labels from file
        AssetManager assetManager = getAssets();
        try (InputStream inputStream = assetManager.open("labels.txt")) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                labels.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Load the model and setup image processor
        try {
            model = SsdMobilenetV11Metadata1.newInstance(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        imageProcessor = new ImageProcessor.Builder().add(new ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build();

        selectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open file picker to select an image
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, 1);
            }
        });





        goBack = (Button) findViewById(R.id.backup);
        goBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Close the current activity and go back to the previous one
                finish();

            }
        });

    }



    // Handle permission request result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
    // Check and request permission to read external storage
    public void get_permission() {
        // Check if permission to read external storage is granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted, check if user has denied the permission before
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // Explain why the app needs this permission
                Toast.makeText(this, "The app needs permission to access image files.", Toast.LENGTH_SHORT).show();
            }
            // Request permission to read external storage
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 101);
        }
    }

    // Handle activity result from file picker
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 1) {
                // Process the selected image
                try {
                    InputStream inputStream = getContentResolver().openInputStream(data.getData());
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    inputStream.close();
                    detectObjects(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // Process the image for object detection
    private void detectObjects(Bitmap bitmap) {
        TensorImage image = TensorImage.fromBitmap(bitmap);
        image = imageProcessor.process(image);
        SsdMobilenetV11Metadata1.Outputs outputs = model.process(image);
        float[] locations = outputs.getLocationsAsTensorBuffer().getFloatArray();
        float[] classes = outputs.getClassesAsTensorBuffer().getFloatArray();
        float[] scores = outputs.getScoresAsTensorBuffer().getFloatArray();
        float[] numberOfDetections = outputs.getNumberOfDetectionsAsTensorBuffer().getFloatArray();

        // Draw bounding boxes and labels on the bitmap
        //creat a muttable var to pass in the canvas
        Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        //canvas function to draw the rectangle
        Canvas canvas = new Canvas(mutable );

        float h = mutable.getHeight();
        float w = mutable.getWidth();
        paint.setTextSize(h/20f);
        paint.setStrokeWidth(h/100f);


        int x = 0;
        for (int index = 0; index < scores.length ; index++) {
            float fl = scores[index];
            x = index;
            x *= 4;


            if (fl > 0.5) {
                paint.setColor(colors.get(index));
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(new RectF(locations[x+1]*w, locations[x]*h, locations[x+3]*w, locations[x+2]*h), paint);
                paint.setStyle(Paint.Style.FILL);
                try {
                    int classIndex = (int) classes[index];
                    String tx = labels.get(classIndex) + " " + Float.toString(fl);
                    canvas.drawText(tx, locations[x+1]*w, locations[x]*h, paint);
                    // Rest of the code that uses the 'tx' variable
                } catch (ArrayIndexOutOfBoundsException e) {
                    // Handle ArrayIndexOutOfBoundsException
                    e.printStackTrace(); // Print the stack trace for debugging
                    // You can also show an error message to the user, log the error, or take other appropriate actions
                } catch (NullPointerException e) {
                    // Handle NullPointerException
                    e.printStackTrace(); // Print the stack trace for debugging
                    // You can also show an error message to the user, log the error, or take other appropriate actions
                }





            }
        }
        // Display the processed image on imageView
        imageView.setImageBitmap(mutable
        );
    }




    @Override
    protected void onDestroy() {
        super.onDestroy();
        model.close();
    }
}
