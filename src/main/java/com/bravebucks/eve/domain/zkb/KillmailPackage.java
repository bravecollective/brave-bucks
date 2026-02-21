package com.bravebucks.eve.domain.zkb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KillmailPackage {
    private long killmail_id;
    private String hash;
    @JsonProperty("esi")
    private Killmail killmail;
    private ZkbInfo zkb;
    private long sequence_id;

    public long getKillmail_id() {
        return killmail_id;
    }

    public void setKillmail_id(long killmail_id) {
        this.killmail_id = killmail_id;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Killmail getKillmail() {
        return killmail;
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

    public long getSequence_id() {
        return sequence_id;
    }

    public void setSequence_id(long sequence_id) {
        this.sequence_id = sequence_id;
    }

    @Override
    public String toString() {
        return "KillmailPackage{" +
               "killmail=" + killmail +
               ", zkb=" + zkb +
               '}';
    }
}
