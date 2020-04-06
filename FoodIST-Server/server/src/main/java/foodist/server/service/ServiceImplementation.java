package foodist.server.service;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;

import foodist.server.common.Utils;
import foodist.server.grpc.contract.Contract;
import foodist.server.grpc.contract.Contract.AddPhotoRequest;
import foodist.server.grpc.contract.Contract.DownloadPhotoReply;
import foodist.server.grpc.contract.Contract.ListMenuReply;
import foodist.server.grpc.contract.Contract.Menu;
import foodist.server.grpc.contract.FoodISTServerServiceGrpc;
import foodist.server.grpc.contract.FoodISTServerServiceGrpc.FoodISTServerServiceImplBase;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceImplementation extends FoodISTServerServiceImplBase {
	
	private ConcurrentHashMap<String, HashSet<Menu>> menusHashMap = new ConcurrentHashMap<String, HashSet<Menu>>();
	
	public static FoodISTServerServiceGrpc.FoodISTServerServiceBlockingStub foodISTServerServiceGrpcStub;
	
    @Override
    public void addMenu(Contract.AddMenuRequest request, StreamObserver<Empty> responseObserver) {
    	Menu.Builder menuBuilder = Menu.newBuilder();
    	
	    String foodService = request.getFoodService();                     
	    menuBuilder.setName(request.getName());
	    menuBuilder.setPrice(request.getPrice());
	    Menu menu = menuBuilder.build();
	        
	    System.out.println(request.getName() + ":" + request.getPrice());
	    HashSet<Menu> menuSet = this.menusHashMap.get(foodService);
	      
	    if(menuSet!=null) {
	    	menuSet.add(menu);
	    	this.menusHashMap.put(foodService, menuSet);         
	    } 
	    else {
	    	HashSet<Menu> new_MenuSet = new HashSet<Menu>();
	    	new_MenuSet.add(menu);
	    	this.menusHashMap.put(foodService, new_MenuSet);         
	    } 
	      
	    responseObserver.onNext(null);
	    responseObserver.onCompleted();      
    }  
    
    @Override
    public void listMenu(Contract.ListMenuRequest request, StreamObserver<Contract.ListMenuReply> responseObserver) {
		String foodService = request.getFoodService();                

		HashSet<Menu> menuList = menusHashMap.get(foodService);
		    
		ListMenuReply.Builder listMenuReplyBuilder = ListMenuReply.newBuilder();
		    
		for(Menu m : menuList) {			
			Menu menu = Utils.fetchMenuPhotos(foodService, m.getName(), m.getPrice());
			listMenuReplyBuilder.addMenus(menu);        	
		}
		    
		ListMenuReply listMenuReply = listMenuReplyBuilder.build();        
		responseObserver.onNext(listMenuReply);
		responseObserver.onCompleted();  
    } 

    @Override
	public StreamObserver<Contract.AddPhotoRequest> addPhoto(StreamObserver<Empty> responseObserver) {
    	return new StreamObserver<Contract.AddPhotoRequest>() {    		
    		private int counter = 0;
    		private ByteString photoByteString = ByteString.copyFrom(new byte[]{});
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
    				Utils.addPhotoToMenu(photoName, foodService, menuName, photoByteString);
    				responseObserver.onCompleted();
    			} catch (StatusRuntimeException e) {
    				throw new IllegalArgumentException(e.getMessage());
    			}
    		}  
    	};
    }

    @Override
    public void downloadPhoto(Contract.DownloadPhotoRequest request, StreamObserver<Contract.DownloadPhotoReply> responseObserver) {
    	String foodService = request.getFoodService();
        String menuName = request.getMenuName();
        String photoId = request.getPhotoId();               
           
        int sequence = 0;
        
        byte[] photo = Utils.fetchPhotoBytes(photoId, foodService, menuName);
        //Send file 1MB chunk at a time
        for (int i = 0; i < photo.length; i += 1024 * 1024, sequence++) {
            int chunkSize = Math.min(1024 * 1024, photo.length - i);
            DownloadPhotoReply.Builder downloadPhotoReplyBuilder = Contract.DownloadPhotoReply.newBuilder();
            downloadPhotoReplyBuilder.setContent(ByteString.copyFrom(Arrays.copyOfRange(photo, i, i + chunkSize)));
            downloadPhotoReplyBuilder.setSequenceNumber(sequence);
            responseObserver.onNext(downloadPhotoReplyBuilder.build());
        }
        responseObserver.onCompleted();
    }    
    
}
