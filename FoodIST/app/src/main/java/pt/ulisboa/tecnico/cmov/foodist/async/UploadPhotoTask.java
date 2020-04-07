package pt.ulisboa.tecnico.cmov.foodist.async;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import foodist.server.grpc.contract.Contract;
import foodist.server.grpc.contract.FoodISTServerServiceGrpc;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.cmov.foodist.activity.FoodMenuActivity;
import pt.ulisboa.tecnico.cmov.foodist.domain.Photo;


public class UploadPhotoTask extends AsyncTask<Photo, Integer, Boolean> {


    FoodISTServerServiceGrpc.FoodISTServerServiceStub stub;
    private WeakReference<FoodMenuActivity> activity;

    public UploadPhotoTask(FoodISTServerServiceGrpc.FoodISTServerServiceStub stub, FoodMenuActivity activity) {
        this.stub = stub;
        this.activity = new WeakReference<>(activity);
    }

    private static final String TAG = "UPLOADPHOTO-TASK";

    @Override
    protected Boolean doInBackground(Photo... photo) {
        if (photo.length != 1) {
            return false;
        }
        final CountDownLatch finishLatch = new CountDownLatch(1);
        int sequence = 0;

        StreamObserver<Empty> responseObserver = new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty empty) {
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("Error uploading file, does that file already exist?" + throwable.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("File uploaded successfully");
                finishLatch.countDown();
            }
        };

        StreamObserver<Contract.AddPhotoRequest> requestObserver = stub.addPhoto(responseObserver);

        byte[] data = new byte[1024 * 1024];

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(photo[0].getPhotoPath()))) {

            int numRead;

            //Send file chunks to server
            while ((numRead = in.read(data)) >= 0) {
                Contract.AddPhotoRequest.Builder addPhotoRequestBuilder = Contract.AddPhotoRequest.newBuilder();

                addPhotoRequestBuilder.setContent(ByteString.copyFrom(Arrays.copyOfRange(data, 0, numRead)));
                addPhotoRequestBuilder.setMenuName(photo[0].getMenuName());
                addPhotoRequestBuilder.setSequenceNumber(sequence);
                addPhotoRequestBuilder.setFoodService(photo[0].getFoodServiceName());
                addPhotoRequestBuilder.setPhotoName(getFileFromPath(photo[0].getPhotoPath()));

                requestObserver.onNext(addPhotoRequestBuilder.build());
                sequence++;
            }

            requestObserver.onCompleted();

            //Wait for server to finish saving file to Database
            finishLatch.await();
            return true;
        } catch (FileNotFoundException e) {
            Log.d(TAG, String.format("File with filename: %s not found.", photo[0].getPhotoPath()));
            return false;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }


    @Override
    protected void onPostExecute(Boolean result) {
        if (result) {
            Log.d(TAG, "Photo uploaded successfully");

        } else {
            Log.d(TAG, "Photo unable to be uploaded");
        }
        FoodMenuActivity act = activity.get();
        if (act != null && !act.isFinishing() && !act.isDestroyed()) {
            act.launchUpdateMenuTask();
        }
    }

    public static String getFileFromPath(String path) {
        String[] split_path = path.split("/");
        int position = split_path.length - 1;
        return split_path[position];
    }
}
