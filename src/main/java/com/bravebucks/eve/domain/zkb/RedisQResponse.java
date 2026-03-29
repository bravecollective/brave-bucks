package com.bravebucks.eve.domain.zkb;

import com.fasterxml.jackson.annotation.JsonProperty;

@Deprecated
public class RedisQResponse {

    // https://redisq.zkillboard.com/listen.php?ttw=20
    @JsonProperty("package")
    private KillmailPackage killmailPackage;

    public void setKillmailPackage(final KillmailPackage killmailPackage) {
        this.killmailPackage = killmailPackage;
    }

    public KillmailPackage getKillmailPackage() {
        return killmailPackage;
    }
}
