package com.vikas.dtu.safetyfirst.mDiscussion;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.firebase.storage.OnProgressListener;
import com.vikas.dtu.safetyfirst.BaseActivity;
import com.vikas.dtu.safetyfirst.R;
import com.vikas.dtu.safetyfirst.mData.Post;
import com.vikas.dtu.safetyfirst.mData.User;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.UploadTask;
import com.google.firebase.storage.StorageReference;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.vikas.dtu.safetyfirst.model.PostNotify;

import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.CursorLoader;
import android.support.v7.app.AlertDialog;

import io.realm.Realm;

public class NewPostActivity extends BaseActivity {

    private static final String TAG = "NewPostActivity";
    private static final String REQUIRED = "Required";
    private static final int REQUEST_CAMERA = 1;
    private static final int SELECT_IMAGE = 2;
    private static final int SELECT_PDF = 3;
    private static final int SELECT_VIDEO = 4;
    private ImageView mImageView;
    private ProgressDialog mProgressDialog;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private FirebaseStorage storage;
    private UploadTask uploadTask;
    private String path;
    private Uri uriSavedImage;
    private Button PostButton;
    private String URL = "gs://safetyfirst-aec72.appspot.com/";

    private DatabaseReference mAttachmentsReference;

    private String key;

    ////Using paths to upload
    String imagePath = null, pdfPath = null, attachLink = null;
    //// these download URL are on firebase storage , USE them to render files wherever needed,
    //// and push them to post and user post first. A Post structure wasn`t made according to url, i have left it.
    String downloadImageURL = null, downloadVideoURL = null, downloadPdfURL = null;
    ////

    int LINK_ATTACH = 1, FILE_ATTACH = 2, IMAGE_ATTACH = 3, VIDEO_ATTACH = 4;

    private GoogleApiClient client;

    // [START declare_database_ref]
    private DatabaseReference mDatabase;
    // [END declare_database_ref]

    private EditText mTitleField;
    private EditText mBodyField;
    private String mImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_post);
        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();

        // Enable the Up round_blue_dark
        ab.setDisplayHomeAsUpEnabled(true);


        // [START initialize_database_ref]
        mDatabase = FirebaseDatabase.getInstance().getReference();
        storage = FirebaseStorage.getInstance();
        // [END initialize_database_ref]

        mTitleField = (EditText) findViewById(R.id.field_title);
        mBodyField = (EditText) findViewById(R.id.field_body);


        findViewById(R.id.fab_submit_post).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                key = mDatabase.child("posts").push().getKey();
                submitPost();
                Realm realm = Realm.getDefaultInstance();
                realm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        PostNotify postNotify = realm.createObject(PostNotify.class);
                        postNotify.setUid(getUid());
                        postNotify.setPostKey(key);
                        postNotify.setNumComments(0);
                        postNotify.setNumStars(0);
                    }
                });
                DatabaseReference post_notify_ref = FirebaseDatabase.getInstance().getReference().child("post-notify");
                post_notify_ref.child(key).child("num_of_comments").setValue(0);
                post_notify_ref.child(key).child("num_of_stars").setValue(0);


                if (imagePath != null) uploadImage();
                if (pdfPath != null) uploadPDF();
            }
        });
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    private void submitPost() {
        final String title = mTitleField.getText().toString();
        final String body = mBodyField.getText().toString();
        final String image;
        if (mImageUri != null) image = mImageUri;
        else image = null;

        // Title is required
        if (TextUtils.isEmpty(title)) {
            mTitleField.setError(REQUIRED);
            return;
        }

        // Body is required
        if (TextUtils.isEmpty(body)) {
            mBodyField.setError(REQUIRED);
            return;
        }

        // [START single_value_read]
        final String userId = getUid();
        mDatabase.child("users").child(userId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        // Get user value
                        User user = dataSnapshot.getValue(User.class);

                        // [START_EXCLUDE]
                        if (user == null) {
                            // User is null, error out
                            Log.e(TAG, "User " + userId + " is unexpectedly null");
                            Toast.makeText(NewPostActivity.this,
                                    "Error: could not fetch user.",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            // Write new post
                            mAttachmentsReference = FirebaseDatabase.getInstance().getReference().child("post-attachments").child(key);
                            writeNewPost(userId, user.username, title, body, downloadImageURL, user.userImage, downloadVideoURL, downloadPdfURL, attachLink);
                        }

                        // Finish this Activity, back to the stream
                        finish();
                        // [END_EXCLUDE]
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.w(TAG, "getUser:onCancelled", databaseError.toException());
                    }
                });
        // [END single_value_read]
    }

    // [START write_fan_out]
    private void writeNewPost(String userId, String username, String title, String body, String downloadImageURL, String authorImageUrl,
                              String downloadPdfURL, String downloadVideoURL, String attachLink) {
        // Create new post at /user-posts/$userid/$postid and at
        // /posts/$postid simultaneously
        Post post = new Post(userId, username, title, body, downloadImageURL, authorImageUrl, downloadPdfURL, attachLink);
        Map<String, Object> postValues = post.toMap();

        // Obtaining and adding Keywords for search
        String[] keywords = title.split(" ");
        String[] commonWords = {"what", "why", "is", "are", "and", "in", "how", "to"};

        for (int i = 0; i < keywords.length; i++) {
            for (String commonWord : commonWords) {
                if (keywords[i].toLowerCase().equals(commonWord)) {
                    keywords[i] = "";
                }
            }
        }

        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("/posts/" + key, postValues);
        childUpdates.put("/user-posts/" + userId + "/" + key, postValues);

        for (int i = 0; i < keywords.length; i++) {
            if (keywords[i].length() > 0) {
                String tempKey = mDatabase.child("keywords").child(keywords[i]).push().getKey();
                //   mDatabase.child("keywords").child(keywords[i]).child(tempKey).setValue(key);
                childUpdates.put("/keywords/" + keywords[i].toLowerCase() + "/" + key, postValues);
            }
        }
        mDatabase.updateChildren(childUpdates);
    }
    // [END write_fan_out]

    private void startAction() {
        final CharSequence[] items = {"Take Photo", "Choose From Gallery", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(NewPostActivity.this);
        builder.setTitle("Select An Option");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (items[item].equals("Take Photo")) {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    path = System.currentTimeMillis() + "_compressed_" + ".jpg";
                    uriSavedImage = Uri.fromFile(new File(Environment.getExternalStorageDirectory(),
                            path));
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, uriSavedImage);
                    startActivityForResult(intent, REQUEST_CAMERA);
                } else if (items[item].equals("Choose From Gallery")) {
                    Toast.makeText(getBaseContext(), "choosing from gallery", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(
                            Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setType("image/*");
                    startActivityForResult(
                            Intent.createChooser(intent, "Select File"),
                            SELECT_IMAGE);
                } else if (items[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {

            if (requestCode == REQUEST_CAMERA) {
                String filename;
                filename = compressImage(String.valueOf(uriSavedImage));
                imagePath = filename;
                Toast.makeText(this, imagePath, Toast.LENGTH_SHORT).show();
            } else if (requestCode == SELECT_IMAGE) {
                Uri selectedImageUri = data.getData();
                Cursor cursor = getContentResolver().query(selectedImageUri, null, null, null, null);
                if (cursor == null) {
                    Log.d("response", "NUll");
                    imagePath = selectedImageUri.getPath();
                } else {
                    Log.d("response", "Not nUll");
                    cursor.moveToFirst();
                    int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                    imagePath = cursor.getString(index);
                }
                Toast.makeText(this, imagePath, Toast.LENGTH_SHORT).show();
            } else if (requestCode == SELECT_PDF) {
                pdfPath = data.getData().getPath();
                Toast.makeText(this, pdfPath, Toast.LENGTH_SHORT).show();
            }


        }
    }

    public String compressImage(String imageUri) {

        String filePath = getRealPathFromURI(imageUri);
        Bitmap scaledBitmap = null;

        BitmapFactory.Options options = new BitmapFactory.Options();

//      by setting this field as true, the actual bitmap pixels are not loaded in the memory. Just the bounds are loaded. If
//      you try the use the bitmap here, you will get null.
        options.inJustDecodeBounds = true;
        Bitmap bmp = BitmapFactory.decodeFile(filePath, options);

        int actualHeight = options.outHeight;
        int actualWidth = options.outWidth;

//      max Height and width values of the compressed image is taken as 816x612

        float maxHeight = 816.0f;
        float maxWidth = 612.0f;
        float imgRatio = actualWidth / actualHeight;
        float maxRatio = maxWidth / maxHeight;

//      width and height values are set maintaining the aspect ratio of the image

        if (actualHeight > maxHeight || actualWidth > maxWidth) {
            if (imgRatio < maxRatio) {
                imgRatio = maxHeight / actualHeight;
                actualWidth = (int) (imgRatio * actualWidth);
                actualHeight = (int) maxHeight;
            } else if (imgRatio > maxRatio) {
                imgRatio = maxWidth / actualWidth;
                actualHeight = (int) (imgRatio * actualHeight);
                actualWidth = (int) maxWidth;
            } else {
                actualHeight = (int) maxHeight;
                actualWidth = (int) maxWidth;

            }
        }

//      setting inSampleSize value allows to load a scaled down version of the original image

        options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);

//      inJustDecodeBounds set to false to load the actual bitmap
        options.inJustDecodeBounds = false;

//      this options allow android to claim the bitmap memory if it runs low on memory
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inTempStorage = new byte[16 * 1024];

        try {

            bmp = BitmapFactory.decodeFile(filePath, options);
        } catch (OutOfMemoryError exception) {
            Log.d("response", "Error 4");
            exception.printStackTrace();

        }
        try {
            scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError exception) {
            Log.d("response", "Error 3");
            exception.printStackTrace();
        }

        float ratioX = actualWidth / (float) options.outWidth;
        float ratioY = actualHeight / (float) options.outHeight;
        float middleX = actualWidth / 2.0f;
        float middleY = actualHeight / 2.0f;

        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

        assert scaledBitmap != null;
        Canvas canvas = new Canvas(scaledBitmap);
        canvas.setMatrix(scaleMatrix);
        canvas.drawBitmap(bmp, middleX - bmp.getWidth() / 2, middleY - bmp.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));
        ExifInterface exif;

        try {
            exif = new ExifInterface(filePath);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, 0);
            Log.d("EXIF", "Exif: " + orientation);
            Matrix matrix = new Matrix();
            if (orientation == 6) {
                matrix.postRotate(90);
                Log.d("EXIF", "Exif: " + orientation);
            } else if (orientation == 3) {
                matrix.postRotate(180);
                Log.d("EXIF", "Exif: " + orientation);
            } else if (orientation == 8) {
                matrix.postRotate(270);
                Log.d("EXIF", "Exif: " + orientation);
            }
            scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0,
                    scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix,
                    true);
        } catch (IOException e) {
            Log.d("response", "Error 2");
            e.printStackTrace();
        }

        FileOutputStream out = null;
        String filename = getFilename();
        try {
            out = new FileOutputStream(filename);
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        } catch (FileNotFoundException e) {
            Log.d("response", "Error Main");
            e.printStackTrace();
        }

        return filename;
    }

    private String getRealPathFromURI(String contentURI) {
        Uri contentUri = Uri.parse(contentURI);
        Cursor cursor = getContentResolver().query(contentUri, null, null, null, null);
        if (cursor == null) {
            Log.d("response", "NUll");
            return contentUri.getPath();
        } else {
            Log.d("response", "Not nUll");
            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            return cursor.getString(index);
        }
    }

    public String getFilename() {
        File destination = new File(Environment.getExternalStorageDirectory(),
                System.currentTimeMillis() + "_compressed_" + ".jpg");
        return String.valueOf(destination);

    }

    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        Log.d("response", "Sample Size");
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        final float totalPixels = width * height;
        final float totalReqPixelsCap = reqWidth * reqHeight * 2;
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Pic Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.vikas.dtu.safetyfirst/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Pic Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.vikas.dtu.safetyfirst/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

   /* @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_new_post, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Take appropriate action for each action item click
        switch (item.getItemId()) {
            case R.id.image:
                // image action
                startAction();
                return true;
            case R.id.file:
                // file action
                pickPDF();
                return true;
            case R.id.video:
                // video action
                pickVideo();
                return true;
            case R.id.link:
                // link action
                attachLink();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
*/
    private void attachLink() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.attach_link);
        final EditText etInputLink = new EditText(this);
        etInputLink.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(etInputLink);
        builder.setPositiveButton("Attach", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (etInputLink.getText().toString().trim().equals("")) {
                    Toast.makeText(NewPostActivity.this, "Add a link to attach", Toast.LENGTH_SHORT).show();
                } else {
                    /// this is link that user added. Add this to firebase wherever you want
                    attachLink = etInputLink.getText().toString();
                    Toast.makeText(NewPostActivity.this, "Link Attached", Toast.LENGTH_SHORT).show();
                }
            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        builder.show();
    }

    private void pickPDF() {
        Intent pickPdf = new Intent(Intent.ACTION_GET_CONTENT);
        pickPdf.setType("application/pdf");
        startActivityForResult(pickPdf, SELECT_PDF);
    }

    private void pickVideo() {
        Intent pickVideo = new Intent(Intent.ACTION_GET_CONTENT);
        pickVideo.setType("video/*");
        startActivityForResult(pickVideo, SELECT_VIDEO);
    }

    public void uploadImage() {
        Uri file = Uri.fromFile(new File(imagePath));
        /// upload destination. change according to your needs
        UploadTask uploadTask = storage.getReferenceFromUrl(URL).child(getUid() + "/image/" + file.getLastPathSegment()).putFile(file);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle unsuccessful uploads
                Toast.makeText(getBaseContext(), "Failed", Toast.LENGTH_SHORT).show();
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.

                Uri downloadUrl = taskSnapshot.getDownloadUrl();

                downloadImageURL = String.valueOf(downloadUrl);
                assert downloadUrl != null;
                Toast.makeText(getBaseContext(), "Image Uploaded", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent();
                intent.putExtra("DOWNLOAD_URI", downloadUrl);
                setResult(RESULT_OK, intent);

                Map<String, Object> imageAttach = new HashMap<>();
                imageAttach.put("/posts/" + key + "/image/", downloadImageURL);
                mDatabase.updateChildren(imageAttach);

                pushNode(IMAGE_ATTACH, downloadImageURL);

                finish();

            }
        });
    }

    public void uploadPDF() {
        Uri file = Uri.fromFile(new File(pdfPath));
        /// upload destination. change according to your needs
        UploadTask uploadTask = storage.getReferenceFromUrl(URL).child(getUid() + "/pdf/" + file.getLastPathSegment()).putFile(file);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle unsuccessful uploads
                Toast.makeText(getBaseContext(), "Failed", Toast.LENGTH_SHORT).show();
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.

                Uri downloadUrl = taskSnapshot.getDownloadUrl();
                assert downloadUrl != null;
                Toast.makeText(getBaseContext(), "PDF uploaded", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent();
                intent.putExtra("DOWNLOAD_URI", downloadUrl);
                setResult(RESULT_OK, intent);
                pushNode(FILE_ATTACH, String.valueOf(downloadUrl));
                finish();
            }
        });
    }

    public void pushNode(int ID, String AttachUrl) {
        String type;

        if (ID == LINK_ATTACH) {
            type = "LINK_ATTACH";
        } else if (ID == FILE_ATTACH) {
            type = "FILE_ATTACH";
        } else if (ID == IMAGE_ATTACH) {
            type = "IMAGE_ATTACH";
        } else type = "null";

        mAttachmentsReference.child(type).setValue(AttachUrl);
    }

    public void uploadImage(View view) {
        startAction();
    }

    public void uploadFile(View view) {
        pickPDF();
    }
}
