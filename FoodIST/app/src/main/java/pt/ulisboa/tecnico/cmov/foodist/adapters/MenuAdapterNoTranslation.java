package pt.ulisboa.tecnico.cmov.foodist.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Locale;

import pt.ulisboa.tecnico.cmov.foodist.R;
import pt.ulisboa.tecnico.cmov.foodist.activity.FoodMenuActivity;
import pt.ulisboa.tecnico.cmov.foodist.domain.Menu;

import static pt.ulisboa.tecnico.cmov.foodist.activity.data.IntentKeys.MENU_ID;
import static pt.ulisboa.tecnico.cmov.foodist.activity.data.IntentKeys.MENU_PHOTO_IDS;
import static pt.ulisboa.tecnico.cmov.foodist.activity.data.IntentKeys.MENU_RATING;

public class MenuAdapterNoTranslation extends ArrayAdapter<Menu> {
    private static final String MENU_NAME = "Menu_name";
    private static final String MENU_PRICE = "Menu_price";
    private static final String MENU_SERVICE = "Menu_service";
    private static final String DISPLAY_NAME = "Display_name";


    public MenuAdapterNoTranslation(Context context, ArrayList<Menu> menus) {
        super(context, 0, menus);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Menu menu = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.food_menu, parent, false);
        }

        TextView menuFood = convertView.findViewById(R.id.menuFood);
        TextView menuCost = convertView.findViewById(R.id.menuCost);
        RatingBar ratingBar = convertView.findViewById(R.id.foodMenuRating);

        menuFood.setText(menu.getMenuName());
        menuCost.setText(String.format(Locale.US, "%.2f", menu.getPrice()));
        ratingBar.setRating((float)menu.getAverageRating());


        convertView.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), FoodMenuActivity.class);
            intent.putExtra(MENU_NAME, menu.getMenuName());
            intent.putExtra(MENU_PRICE, menu.getPrice());
            intent.putExtra(MENU_SERVICE, menu.getFoodServiceName());
            intent.putExtra(MENU_ID, menu.getMenuId());
            intent.putExtra(MENU_RATING, menu.getAverageRating());
            intent.putExtra(DISPLAY_NAME, menuFood.getText());
            intent.putStringArrayListExtra(MENU_PHOTO_IDS, menu.getPhotoIds());
            getContext().startActivity(intent);

        });
        return convertView;
    }

}
