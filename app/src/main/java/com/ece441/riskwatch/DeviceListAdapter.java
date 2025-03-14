package com.ece441.riskwatch;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class DeviceListAdapter extends BaseAdapter {

    private ArrayList<BluetoothDevice> deviceList;
    private LayoutInflater inflater;
    private BluetoothActivity activity;

    public DeviceListAdapter(Context context) {
        deviceList = new ArrayList<>();
        inflater = LayoutInflater.from(context);
        if (context instanceof BluetoothActivity) {
            activity = (BluetoothActivity) context;
        } else {
            throw new RuntimeException("DeviceListAdapter requires BluetoothActivity context");
        }
    }

    public void addDevice(BluetoothDevice device) {
        String deviceName = device.getName();
        // Only add devices that have a name and aren't already in the list
        if (deviceName != null && !deviceName.isEmpty() && !deviceName.equals("Unknown Device") && !deviceList.contains(device)) {
            deviceList.add(device);
            notifyDataSetChanged();
        }
    }

    public void clear() {
        deviceList.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return deviceList.size();
    }

    @Override
    public Object getItem(int position) {
        return deviceList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.listitem_device, parent, false);
            holder = new ViewHolder();
            holder.deviceName = convertView.findViewById(R.id.device_name);
            holder.deviceAddress = convertView.findViewById(R.id.device_address);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        BluetoothDevice device = deviceList.get(position);
        holder.deviceName.setText(device.getName() != null ? device.getName() : "Unknown Device");
        holder.deviceAddress.setText(device.getAddress());

        convertView.setOnClickListener(v -> {
            if (activity != null) {
                activity.onDeviceSelected(device);
            }
        });

        return convertView;
    }
}