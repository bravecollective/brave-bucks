package com.bravebucks.eve.service;

import com.bravebucks.eve.domain.zkb.Killmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class KillmailFetcher {

    @Value("${ESI_DOMAIN}")
    private String esiDomain;

    private final RestTemplate restTemplate;

    public KillmailFetcher(final RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Killmail fetchKillmail(final long killmailId, final String killmailHash) {
        return restTemplate.getForObject(esiDomain + "/killmails/" + killmailId + "/" + killmailHash, Killmail.class);
    }
}
