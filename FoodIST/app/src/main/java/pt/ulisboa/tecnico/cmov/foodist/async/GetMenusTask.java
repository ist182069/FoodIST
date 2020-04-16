package pt.ulisboa.tecnico.cmov.foodist.async;

import android.util.Log;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import foodist.server.grpc.contract.Contract;
import foodist.server.grpc.contract.FoodISTServerServiceGrpc.FoodISTServerServiceBlockingStub;
import io.grpc.StatusRuntimeException;
import pt.ulisboa.tecnico.cmov.foodist.R;
import pt.ulisboa.tecnico.cmov.foodist.activity.FoodServiceActivity;
import pt.ulisboa.tecnico.cmov.foodist.adapters.MenuAdapter;
import pt.ulisboa.tecnico.cmov.foodist.async.base.BaseAsyncTask;
import pt.ulisboa.tecnico.cmov.foodist.domain.Menu;


public class GetMenusTask extends BaseAsyncTask<String, Integer, List<Contract.Menu>, FoodServiceActivity> {

    private FoodISTServerServiceBlockingStub stub;
    private String foodService;

    public GetMenusTask(FoodServiceActivity activity) {
        super(activity);
        this.stub = activity.getGlobalStatus().getStub();
    }

    private static final String TAG = "GET-MENU-TASK";

    @Override
    protected List<Contract.Menu> doInBackground(String... foodService) {
        if (foodService.length != 1) {
            return null;
        }
        this.foodService = foodService[0];

        Contract.ListMenuRequest.Builder listMenuBuilder = Contract.ListMenuRequest.newBuilder();

        listMenuBuilder.setFoodService(this.foodService);

        Contract.ListMenuRequest request = listMenuBuilder.build();

        try {
            Contract.ListMenuReply reply = this.stub.listMenu(request);
            return reply.getMenusList();
        } catch (StatusRuntimeException e) {
            return null;
        }
    }

    @Override
    public void onPostExecute(List<Contract.Menu> result) {
        if (result == null) {
            menuError(getActivity(), getActivity().getString(R.string.error_getting_menus_message));
            return;
        }

        if (result.size() == 0) {
            getActivity().showToast(getActivity().getString(R.string.no_menus_avaliable_message));
            return;
        }

        ListView foodServiceList = getActivity().findViewById(R.id.menus);
        List<Menu> menus = result.stream()
                .map(menu -> Menu.parseContractMenu(this.foodService, menu))
                .collect(Collectors.toList());

        getActivity().setMenus(new ArrayList<>(menus));

        Map<Contract.FoodType, Boolean> constraints = getActivity().getGlobalStatus().getUserConstraints();

        List<Menu> filteredMenus = menus.stream()
                .filter(menu -> menu.isConstrained(constraints))
                .collect(Collectors.toList());

        final MenuAdapter menuAdapter = new MenuAdapter(getActivity(), new ArrayList<>(filteredMenus));

        if (filteredMenus.size() !=  menus.size()) {
            getActivity().showToast(getActivity().getString(R.string.filter_menu_message));
            getActivity().doShowAllButton();
        }

        foodServiceList.setAdapter(menuAdapter);
        Log.d(TAG, "Menus obtained successfully");

    }

    private void menuError(FoodServiceActivity activity, String message) {
        activity.showToast(message);
        Log.d(TAG, "Unable to request menus");
    }

}
