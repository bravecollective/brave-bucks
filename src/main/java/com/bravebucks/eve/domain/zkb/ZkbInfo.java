package com.bravebucks.eve.domain.zkb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ZkbInfo {
    private boolean npc;
    private int points;
    private double totalValue;
    private String hash;

    public boolean isNpc() {
        return npc;
    }

    public void setNpc(final boolean npc) {
        this.npc = npc;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(final int points) {
        this.points = points;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(final double totalValue) {
        this.totalValue = totalValue;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
