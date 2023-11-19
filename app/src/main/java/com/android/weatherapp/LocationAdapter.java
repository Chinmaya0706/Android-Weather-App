package com.android.weatherapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class LocationAdapter extends ArrayAdapter<String> {

    public LocationAdapter(Context context, List<String> suggestions) {
        super(context, 0, suggestions);
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item, parent, false);
        }

        TextView textView = convertView.findViewById(R.id.text1);
        textView.setText(getItem(position));

        return convertView;
    }


    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }
}