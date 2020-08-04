package com.botoseis.structs;

public final class Shot {

    public int pickAmount;
    public int seqNum;
    public int souStat;
    public float souX;
    public float souY;
    public float souElev;
    public Pick[] picks;

    public final Station getStation() {
        Station station = new Station();
        station.num = souStat;
        station.x = souX;
        station.y = souY;
        station.elev = souElev;
        return station;
    }
}
