package pt.ulisboa.tecnico.cmov.foodist.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import pt.ulisboa.tecnico.cmov.foodist.R;
import pt.ulisboa.tecnico.cmov.foodist.activity.base.BaseActivity;
import pt.ulisboa.tecnico.cmov.foodist.async.DownloadPhotoTask;
import pt.ulisboa.tecnico.cmov.foodist.async.UploadPhotoTask;
import pt.ulisboa.tecnico.cmov.foodist.async.base.CancelableTask;
import pt.ulisboa.tecnico.cmov.foodist.async.base.SafePostTask;
import pt.ulisboa.tecnico.cmov.foodist.domain.Photo;
import pt.ulisboa.tecnico.cmov.foodist.status.GlobalStatus;

public class FoodMenuActivity extends BaseActivity {
    //Intent tags
    public static final String NUMBER_PHOTOS = "Number_photos";
    public static final String MENU_NAME = "Menu_name";
    public static final String MENU_PRICE = "Menu_price";
    public static final String MENU_SERVICE = "Menu_service";
    public static final String PHOTO_LIST = "Menu_photo_list";

    //Camera/Gallery tags
    private static final int PICK_FROM_GALLERY = 1;
    private static final int PICK_FROM_CAMERA = 2;
    private static final int REQUEST_PIC = 3;
    private static final int GALLERY_PIC = 4;
    private static final int CAMERA_PIC = 5;

    private static final String TAG = "TAG_FoodMenuActivity";

    private String imageFilePath = null;

    private int numPhoto = 0;
    private String foodService;
    private String menuName;
    private String[] photoIDs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_food_menu);
        TextView numberPhoto = findViewById(R.id.photoNumber);

        intentInitialization(getIntent());
        if (photoIDs.length > 0) {
            Photo photo = new Photo(this.foodService, this.menuName, null, photoIDs[numPhoto]);
            launchDownloadPhotoTask(photo);
            numberPhoto.setText(String.format(Locale.US,"%d/%d", numPhoto+1, photoIDs.length));
        } else {
            Toast.makeText(getApplicationContext(), "No photos available for this menu", Toast.LENGTH_SHORT).show();
        }
        setButtons();
    }

    protected void setButtons() {

        Button previousPhoto = findViewById(R.id.previousPhoto);
        previousPhoto.setOnClickListener(v -> previousPhoto());

        Button nextPhoto = findViewById(R.id.nextPhoto);
        nextPhoto.setOnClickListener(v -> nextPhoto());

        Button addPhoto = findViewById(R.id.add_photo_button);
        addPhoto.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                askGalleryPermission();
            } else {
                showToast("Cannot upload photo without internet");
            }
        });
    }

    private void intentInitialization(Intent intent) {
        initializeMenuName(intent.getStringExtra(MENU_NAME));
        initializeMenuCost(intent.getDoubleExtra(MENU_PRICE, -1.0));
        initializeFoodService(intent.getStringExtra(MENU_SERVICE));
        initializePhotoIDs(intent.getStringArrayExtra(PHOTO_LIST));
    }

    private void initializePhotoIDs(String[] photoIDs) {
        if (photoIDs == null) {
            Log.d(TAG, "Unable to obtain IDs");
        } else {
            this.photoIDs = photoIDs;
        }
    }

    private void initializeMenuName(String menuName) {
        if (menuName == null) {
            Log.d(TAG, "Unable to obtain menu name");
            Toast.makeText(this, "Unable to obtain menu name", Toast.LENGTH_SHORT).show();
        } else {
            this.menuName = menuName;
            TextView menuNameText = findViewById(R.id.menuName);
            menuNameText.setText(menuName);
        }
    }

    private void initializeMenuCost(Double menuCost) {
        if (menuCost == -1.0) {
            Log.d(TAG, "Unable to obtain menu cost");
            Toast.makeText(this, "Unable to obtain menu cost", Toast.LENGTH_SHORT).show();
        } else {
            TextView menuCostText = findViewById(R.id.menuCost);
            menuCostText.setText(String.format(Locale.US, "%.2f", menuCost));
        }
    }

    private void initializeFoodService(String foodService) {
        if (foodService == null) {
            Log.d(TAG, "Unable to obtain correspondent food service");
        } else {
            this.foodService = foodService;
        }
    }

    private void nextPhoto() {
        if (photoIDs.length > 0) {
            TextView numberPhoto = findViewById(R.id.photoNumber);

            numPhoto = ++numPhoto % this.photoIDs.length;
            Photo photo = new Photo(this.foodService, this.menuName, null, photoIDs[numPhoto]);
            launchDownloadPhotoTask(photo);
            numberPhoto.setText(String.format(Locale.US,"%d/%d", numPhoto+1, photoIDs.length));

        }
    }

    private void previousPhoto() {
        if (photoIDs.length > 0) {
            TextView numberPhoto = findViewById(R.id.photoNumber);

            numPhoto = --numPhoto == -1 ? this.photoIDs.length - 1 : numPhoto;
            Photo photo = new Photo(this.foodService, this.menuName, null, photoIDs[numPhoto]);
            launchDownloadPhotoTask(photo);
            numberPhoto.setText(String.format(Locale.US,"%d/%d", numPhoto+1, photoIDs.length));
        }
    }

    private void askGalleryPermission() {
        int galleryPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (galleryPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PICK_FROM_GALLERY);
        } else {
            askCameraPermission();
        }
    }

    private void askCameraPermission() {
        int cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PICK_FROM_CAMERA);
        } else {
            cameraOrGalleryChooser();
        }
    }

    private void cameraOrGalleryChooser() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Intent galleryintent = new Intent(Intent.ACTION_PICK);
            galleryintent.setType("image/*");

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

                Intent chooser = new Intent(Intent.ACTION_CHOOSER);
                chooser.putExtra(Intent.EXTRA_INTENT, galleryintent);
                chooser.putExtra(Intent.EXTRA_TITLE, "Select from:");

                Intent[] intentArray = {createCameraIntent()};
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                startActivityForResult(chooser, REQUEST_PIC);
            } else {
                startActivityForResult(galleryintent, GALLERY_PIC);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

                startActivityForResult(createCameraIntent(), CAMERA_PIC);
            }
        }
    }

    public Intent createCameraIntent() {
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            showToast("Could not get image provided");
        }

        if (photoFile != null) {
            Uri photoURI = FileProvider.getUriForFile(this, "pt.ulisboa.tecnico.cmov.foodist.provider", photoFile);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        }

        return cameraIntent;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PICK_FROM_GALLERY:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "phone gallery permission granted");
                }
                askCameraPermission();
                break;

            case PICK_FROM_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "phone camera permission granted");
                }
                cameraOrGalleryChooser();
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            SharedPreferences pref = getApplicationContext().getSharedPreferences(getString(R.string.profile_file), 0);
            SharedPreferences.Editor editor = pref.edit();

            switch (requestCode) {
                case GALLERY_PIC:

                    galleryReturn(editor, data);
                    break;
                case CAMERA_PIC:
                    Log.d(TAG, "I should not entered here");

                    cameraReturn(editor, data);
                    break;
                case REQUEST_PIC:
                    choiceReturn(editor, data);
                    break;
            }
        }
    }

    private void galleryReturn(SharedPreferences.Editor editor, Intent data) {
        Uri selectedImage = data.getData();

        String[] filePath = {MediaStore.Images.Media.DATA};

        Cursor cursor = getContentResolver().query(selectedImage, filePath, null, null, null);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePath[0]);
        String absoluteFilePath = cursor.getString(columnIndex);
        cursor.close();

        Photo photo = new Photo(this.foodService, this.menuName, absoluteFilePath);
        launchUploadPhotoTask(photo);
        //Save path for future reference
        //TODO - add directly to cache
        /*
        editor.putString(getString(R.string.user_photo), absoluteFilePath);
        editor.apply();
        */
    }

    private void cameraReturn(SharedPreferences.Editor editor, Intent data) {
        Photo photo = new Photo(this.foodService, this.menuName, this.imageFilePath);
        launchUploadPhotoTask(photo);
        //TODO - add directly to cache
        /*
        editor.putString(getString(R.string.user_photo), imageFilePath);
        editor.apply();

         */
    }


    private void launchDownloadPhotoTask(Photo photo) {
        if (isNetworkAvailable()) {
            new CancelableTask<>(new SafePostTask<>(new DownloadPhotoTask(this))).execute(photo);
        } else {
            showToast("Cannot download menu photo without network connection");
        }
    }

    private void launchUploadPhotoTask(Photo photo) {
        if (isNetworkAvailable()) {
            new UploadPhotoTask(((GlobalStatus) FoodMenuActivity.this.getApplicationContext()).getAssyncStub()).execute(photo);
        } else {
            showToast("Cannot upload menu photo without network connection");
        }
    }

    private void choiceReturn(SharedPreferences.Editor editor, Intent data) {
        Bitmap tryingPhoto = BitmapFactory.decodeFile(imageFilePath);

        if (tryingPhoto == null) {
            galleryReturn(editor, data);
        } else {
            Photo photo = new Photo(this.foodService, this.menuName, this.imageFilePath);
            launchUploadPhotoTask(photo);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp =
                new SimpleDateFormat("yyyyMMdd_HHmmss",
                        Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);

        imageFilePath = image.getAbsolutePath();
        return image;
    }
}
