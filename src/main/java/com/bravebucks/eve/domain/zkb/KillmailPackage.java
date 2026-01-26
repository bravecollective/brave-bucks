package com.bravebucks.eve.domain.zkb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KillmailPackage {
    private long killID;
    private Killmail killmail;
    private ZkbInfo zkb;
    public Killmail getKillmail() {
        return killmail;
    }

    public long getKillID() {
        return killID;
    }

    public void setKillID(long killID) {
        this.killID = killID;
    }

    public void setKillmail(final Killmail killmail) {
        this.killmail = killmail;
    }

    public ZkbInfo getZkb() {
        return zkb;
    }

    public void setZkb(final ZkbInfo zkb) {
        this.zkb = zkb;
    }

    @Override
    public String toString() {
        return "KillmailPackage{" +
               "killmail=" + killmail +
               ", zkb=" + zkb +
               '}';
    }
}
