package com.indiealexh.models;

import java.util.List;

public class StateFireData {

    public final String StateName;

    public final List<GeoLocation> FireLocations;

    public StateFireData(String stateName, List<GeoLocation> fireLocations) {
        StateName = stateName;
        FireLocations = fireLocations;
    }
}
