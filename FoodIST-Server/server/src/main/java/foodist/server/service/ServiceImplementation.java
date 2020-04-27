package foodist.server.service;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import foodist.server.data.*;
import foodist.server.data.exception.ServiceException;
import foodist.server.data.exception.StorageException;
import foodist.server.grpc.contract.Contract;
import foodist.server.grpc.contract.Contract.AddPhotoRequest;
import foodist.server.grpc.contract.Contract.DownloadPhotoReply;
import foodist.server.grpc.contract.Contract.ListMenuReply;
import foodist.server.grpc.contract.Contract.PhotoReply;
import foodist.server.grpc.contract.FoodISTServerServiceGrpc.FoodISTServerServiceImplBase;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ServiceImplementation extends FoodISTServerServiceImplBase {

    public static final int COOKIE_SIZE = 256;

    public static final int NUM_PHOTOS = 3;

    private final Map<String, Account> users = new ConcurrentHashMap<>();
    private final Map<String, Account> sessions = new ConcurrentHashMap<>();

    private final Map<Long, Menu> menus = new ConcurrentHashMap<>();
    private final Map<String, Service> services = new ConcurrentHashMap<>();


    @Override
    public void addMenu(Contract.AddMenuRequest request, StreamObserver<Empty> responseObserver) {
        try {
            Service service = getService(request.getFoodService());
            Menu menu = Menu.fromContract(request);
            service.addMenu(menu);
            menus.put(menu.getMenuId(), menu);

            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        } catch (ServiceException e) {
            responseObserver.onError(Status.ALREADY_EXISTS.asRuntimeException());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.asRuntimeException());
        }
    }

    @Override
    public void listMenu(Contract.ListMenuRequest request, StreamObserver<Contract.ListMenuReply> responseObserver) {
        Service service = getService(request.getFoodService());
        List<Contract.Menu> menus = service.getContractMenus(request.getLanguage());

        ListMenuReply reply = ListMenuReply.newBuilder().addAllMenus(menus).build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();

    }

    @Override
    public void updateMenu(Contract.UpdateMenuRequest request, StreamObserver<Contract.PhotoReply> responseObserver) {
        Long id = request.getMenuId();
        Menu menu = menus.get(id);
        if (menu == null) {
            responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
            return;
        }

        PhotoReply reply = PhotoReply.newBuilder()
                .addAllPhotoID(menu.getPhotos())
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<Contract.AddPhotoRequest> addPhoto(StreamObserver<Empty> responseObserver) {
        return new StreamObserver<>() {
            private int counter = 0;
            private ByteString photoByteString = ByteString.copyFrom(new byte[0]);
            private String menuName;
            private String foodService;
            private String photoName;
            private final Object lock = new Object();


            @Override
            public void onNext(AddPhotoRequest value) {
                //Synchronize onNext calls by sequence
                synchronized (lock) {
                    while (counter != value.getSequenceNumber()) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            //Should never happen
                        }
                    }
                    //Renew Lease
                    if (counter == 0) {
                        menuName = value.getMenuName();
                        foodService = value.getFoodService();
                        photoName = value.getPhotoName();
                    }
                    photoByteString = photoByteString.concat(value.getContent());
                    counter++;
                    lock.notify();
                }
            }

            @Override
            public void onError(Throwable t) {
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                try {
                    responseObserver.onNext(Empty.newBuilder().build());
                    Storage.addPhotoToMenu(photoName, foodService, menuName, photoByteString);
                    responseObserver.onCompleted();
                } catch (StatusRuntimeException e) {
                    throw new IllegalArgumentException(e.getMessage());
                } catch (StorageException e) {
                    this.onError(e);
                }
            }
        };
    }

    @Override
    public void downloadPhoto(Contract.DownloadPhotoRequest request, StreamObserver<Contract.DownloadPhotoReply> responseObserver) {
        String photoId = request.getPhotoId();

        int sequence = 0;

        try {
            byte[] photo = Storage.fetchPhotoBytes(photoId);
            //Send file 1MB chunk at a time

            for (int i = 0; i < photo.length; i += 1024 * 1024, sequence++) {
                int chunkSize = Math.min(1024 * 1024, photo.length - i);
                DownloadPhotoReply.Builder downloadPhotoReplyBuilder = Contract.DownloadPhotoReply.newBuilder();
                downloadPhotoReplyBuilder.setContent(ByteString.copyFrom(Arrays.copyOfRange(photo, i, i + chunkSize)));
                downloadPhotoReplyBuilder.setSequenceNumber(sequence);
                responseObserver.onNext(downloadPhotoReplyBuilder.build());
            }

            responseObserver.onCompleted();
        } catch (StorageException e) {
            responseObserver.onError(e);
        }
    }

    /**
     * Fetches the oldest 3 photoIds of each Menu
     */
    @Override
    public void requestPhotoIDs(Empty request, StreamObserver<foodist.server.grpc.contract.Contract.PhotoReply> responseObserver) {
        List<String> photoIds = menus.values()
                .stream()
                .map(menu -> menu.getPhotos(NUM_PHOTOS))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        PhotoReply reply = PhotoReply.newBuilder().addAllPhotoID(photoIds).build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void register(Contract.RegisterRequest request, StreamObserver<Contract.AccountMessage> responseObserver) {
        try {
            Account account = Account.fromContract(request.getProfile(), request.getPassword());

            var curr = users.putIfAbsent(account.getUsername(), account);
            if (curr != null) {
                responseObserver.onError(Status.ALREADY_EXISTS.asRuntimeException());
                return;
            }

            String cookie = generateRandomCookie();
            sessions.put(cookie, account);

            responseObserver.onNext(account.toReply(cookie));
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.asRuntimeException());
        }
    }

    @Override
    public void login(Contract.LoginRequest request, StreamObserver<Contract.AccountMessage> responseObserver) {
        try {
            String username = request.getUsername();
            String password = request.getPassword();
            Account account = users.get(username);
            if (account == null) {
                responseObserver.onError(Status.UNAUTHENTICATED.asRuntimeException());
                return;
            }
            if (!account.checkPassword(password)) {
                responseObserver.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                return;
            }
            String cookie = generateRandomCookie();
            sessions.put(cookie, account);

            responseObserver.onNext(account.toReply(cookie));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.asRuntimeException());
        }
    }

    @Override
    public void changeProfile(Contract.AccountMessage request, StreamObserver<Empty> responseObserver) {
        responseObserver.onError(Status.UNIMPLEMENTED.asRuntimeException());
    }

    private String generateRandomCookie() {
        return RandomStringUtils.random(COOKIE_SIZE);
    }

    public Map<String, Account> getUsers() {
        return users;
    }

    public Map<String, Account> getSessions() {
        return sessions;
    }

    public Service getService(String name) {
        return services.computeIfAbsent(name, Service::new);
    }
}
