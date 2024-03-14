package com.example.cameradetection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.cameradetection.ml.SsdMobilenetV11Metadata1;

import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    List<Integer> colors = Arrays.asList(
            Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY,
            Color.BLACK, Color.DKGRAY, Color.MAGENTA, Color.YELLOW
    );

   private Bitmap bitmap;
    private TextureView textureView;
    private CameraManager cameraManager;
    private  Handler handler;
    private CameraDevice cameraDevice;
    private ImageView imageView ;
    private   SsdMobilenetV11Metadata1 model;
    private   ImageProcessor imageProcessor;
    private    AssetManager assetManager ;

    private  List<String> labels = new ArrayList<>();

    private    Paint paint = new Paint();

    private Button Close ;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        get_permission();




     assetManager = getAssets();

        InputStream inputStream = null;
        try {
            inputStream = assetManager.open("labels.txt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        // Open and read the text file
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                // Add each line to the string list
                labels.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }



        // resize the image processing
        imageProcessor = new ImageProcessor.Builder().add(new ResizeOp(300,300, ResizeOp.ResizeMethod.BILINEAR)).build();
        try {
          model = SsdMobilenetV11Metadata1.newInstance(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        imageView = findViewById(R.id.imageView);
        // set the handler for the camrera as video and the
        HandlerThread handlerThread = new HandlerThread("videoThread");
        //star the video processing
        handlerThread.start();
        handler =new Handler(handlerThread.getLooper());



        // assign text view
       textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                // open the camera with try and catch  for error

                try {
                    open_Camera();
                } catch (CameraAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                // Implementation of onSurfaceTextureSizeChanged method
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                // Implementation of onSurfaceTextureDestroyed method
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                // Implementation of onSurfaceTextureUpdated method
                bitmap = textureView.getBitmap();



                // Check for null and perform further operations
                if (bitmap != null) {

                    // Creates inputs for reference.
                    TensorImage image = TensorImage.fromBitmap(bitmap);

                    image = imageProcessor.process(image);

                    // Runs model inference and gets result. convert to float array
                    SsdMobilenetV11Metadata1.Outputs outputs = model.process(image);
                    float[] locations = outputs.getLocationsAsTensorBuffer().getFloatArray();
                    float[] classes = outputs.getClassesAsTensorBuffer().getFloatArray();
                    float[] scores = outputs.getScoresAsTensorBuffer().getFloatArray();
                    float[] numberOfDetections = outputs.getNumberOfDetectionsAsTensorBuffer().getFloatArray();

                    // Releases model resources if no longer used.

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

                imageView.setImageBitmap(mutable);



                    // Use the obtained bitmap for further processing
                    // ...
                }
                else {
                    // Handle case where bitmap is null
                    // e.g., show an error message to the user, log the error, or take other appropriate actions
                }

            }
        });

        // set the camera manager for service:
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        Close = (Button) findViewById(R.id.CloseCamera);
        Close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Cleanup camera resources before closing the activity
                cleanupCamera();
                // Close the current activity and go back to the previous one
                finish();

            }
        });


    }

    @Override
    protected void onPause() {
        // Cleanup camera resources before closing the activity
        cleanupCamera();
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        // Cleanup camera resources before closing the activity
        cleanupCamera();
        super.onDestroy();
        model.close();
    }

    // camera use function
   @SuppressLint("MissingPermission")
   public void open_Camera() throws CameraAccessException {

        // get list of the camera with and past the main camera index [0] in to the camera function
       String[] cameraIds = cameraManager.getCameraIdList();

       cameraManager.openCamera(cameraIds[0], new CameraDevice.StateCallback() {

           // open the camera change the name to op
           @Override
           public void onOpened(@NonNull CameraDevice p0) {
               // Implementation of onOpened method

               // initialisation of cameraDevice as pO
            cameraDevice = p0;
            // surface of streaming of the Video
               SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
               //get the surface by passing it to the surface class
              Surface surface = new Surface(surfaceTexture);

              // request a capture with a template/ add the target ass mainView
               try {
                   CaptureRequest.Builder captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                   captureRequest.addTarget(surface);

                   // capture the lit of surface, impliment and returne
                   List<Surface> surfaceList = new ArrayList<>();
                   surfaceList.add(surface);
                   cameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback(){

                       @Override
                       public void onConfigured(@NonNull CameraCaptureSession pO1) {
                           try {
                               pO1.setRepeatingRequest(captureRequest.build(), null,null);
                                 } catch (CameraAccessException e) {
                               throw new RuntimeException(e);
                           }

                       }

                       @Override
                       public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                       }
                   }, handler);

               } catch (CameraAccessException e) {
                   throw new RuntimeException(e);
               }


           }

           @Override
           public void onDisconnected(@NonNull CameraDevice cameraDevice) {
               // Implementation of onDisconnected method
           }

           @Override
           public void onError(@NonNull CameraDevice cameraDevice, int error) {
               // Implementation of onError method
           }

           // passion the loop handler
       }, handler);


   };

    public void get_permission(){


        // camera permission get

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)) {
                // Explain why the app needs this permission
                Toast.makeText(this, "The app needs camera permission to take pictures.", Toast.LENGTH_SHORT).show();
            }
            // Request camera permission
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 101);
        }

    }



 // check of the user accept or refuse permission: if refuse, ask again
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[1] != PackageManager.PERMISSION_GRANTED){
            get_permission();
        }

    }

    private void cleanupCamera() {
        // Release camera resources
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        // Cleanup other camera-related resources
        // Stop any camera processing or streaming
        // Release any additional resources related to camera processing
    }



}