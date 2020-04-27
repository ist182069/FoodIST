package foodist.server;

import foodist.server.data.Menu;
import foodist.server.data.Service;
import foodist.server.grpc.contract.Contract;
import foodist.server.grpc.contract.FoodISTServerServiceGrpc;
import foodist.server.service.ServiceImplementation;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ListMenuTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private ServiceImplementation impl;
    private FoodISTServerServiceGrpc.FoodISTServerServiceBlockingStub stub;


    private static final String NAME = "NAME";
    private static final String NAME2 = "NAME2";
    private static final String NAME3 = "NAME3";
    private static final double PRICE = 2.0d;
    private static final double DELTA = 0.01d;
    private static final String LANGUAGE = "pt";
    private static final String SERVICE = "SERVICE";
    private static final String SERVICE2 = "SERVICE2";
    private static final long MENU_ID = 0;

    private static Contract.AddMenuRequest request;
    private static Contract.AddMenuRequest secondRequest;
    private static Contract.AddMenuRequest thirdRequest;


    private static Contract.ListMenuRequest listRequest;
    private static Contract.ListMenuRequest secondListRequest;

    @BeforeClass
    public static void oneTimeSetup() {
        request = Contract.AddMenuRequest.newBuilder()
                .setName(NAME)
                .setPrice(PRICE)
                .setLanguage(LANGUAGE)
                .setFoodService(SERVICE)
                .setType(Contract.FoodType.Meat)
                .build();
        secondRequest = Contract.AddMenuRequest.newBuilder()
                .setName(NAME2)
                .setPrice(PRICE)
                .setLanguage(LANGUAGE)
                .setFoodService(SERVICE)
                .setType(Contract.FoodType.Meat)
                .build();
        thirdRequest = Contract.AddMenuRequest.newBuilder()
                .setName(NAME3)
                .setPrice(PRICE)
                .setLanguage(LANGUAGE)
                .setFoodService(SERVICE2)
                .setType(Contract.FoodType.Meat)
                .build();

        listRequest = Contract.ListMenuRequest.newBuilder()
                .setFoodService(SERVICE)
                .setLanguage(LANGUAGE)
                .build();

        secondListRequest = Contract.ListMenuRequest.newBuilder()
                .setFoodService(SERVICE2)
                .setLanguage(LANGUAGE)
                .build();

    }


    @Before
    public void setup() throws IOException {
        String serverName = InProcessServerBuilder.generateName();

        impl = new ServiceImplementation();

        grpcCleanup.register(InProcessServerBuilder.forName(serverName).directExecutor().addService(impl).build().start());
        ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());

        this.stub = FoodISTServerServiceGrpc.newBlockingStub(channel);
        stub.addMenu(request);
        stub.addMenu(secondRequest);
        stub.addMenu(thirdRequest);
    }

    @After
    public void teardown() {
        Menu.resetCounter();
    }

    @Test
    public void validTest() {
        Contract.ListMenuReply reply = stub.listMenu(listRequest);
        assertEquals(reply.getMenusCount(), 2);
        Contract.Menu menu = reply.getMenus(0);
        assertEquals(menu.getPhotoIdList().size(), 0);
        assertEquals(menu.getLanguage(), LANGUAGE);
        assertEquals(menu.getOriginalName(), NAME2);
        assertEquals(menu.getTranslatedName(), NAME2);
        assertEquals(menu.getMenuId(), MENU_ID + 1);
        assertEquals(menu.getType(), Contract.FoodType.Meat);
        assertEquals(menu.getPrice(), PRICE, DELTA);

        menu = reply.getMenus(1);
        assertEquals(menu.getPhotoIdList().size(), 0);
        assertEquals(menu.getLanguage(), LANGUAGE);
        assertEquals(menu.getOriginalName(), NAME);
        assertEquals(menu.getTranslatedName(), NAME);
        assertEquals(menu.getMenuId(), MENU_ID);
        assertEquals(menu.getType(), Contract.FoodType.Meat);
        assertEquals(menu.getPrice(), PRICE, DELTA);

        reply = stub.listMenu(secondListRequest);
        assertEquals(reply.getMenusCount(), 1);
        menu = reply.getMenus(0);
        assertEquals(menu.getPhotoIdList().size(), 0);
        assertEquals(menu.getLanguage(), LANGUAGE);
        assertEquals(menu.getOriginalName(), NAME3);
        assertEquals(menu.getTranslatedName(), NAME3);
        assertEquals(menu.getMenuId(), MENU_ID + 2);
        assertEquals(menu.getType(), Contract.FoodType.Meat);
        assertEquals(menu.getPrice(), PRICE, DELTA);
    }
}
