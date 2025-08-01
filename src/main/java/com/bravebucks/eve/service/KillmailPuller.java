package com.bravebucks.eve.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import static java.util.stream.Collectors.toList;

import com.bravebucks.eve.domain.Killmail;
import com.bravebucks.eve.domain.zkb.KillmailPackage;
import com.bravebucks.eve.domain.zkb.RedisQResponse;
import com.bravebucks.eve.repository.KillmailRepository;
import com.bravebucks.eve.repository.SolarSystemRepository;
import com.bravebucks.eve.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class KillmailPuller {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private List<Integer> systems;
    private List<Integer> playerIds;

    private final KillmailRepository killmailRepository;
    private final UserRepository userRepository;
    private final SolarSystemRepository solarSystemRepository;
    private final KillmailParser killmailParser;
    private final RestTemplate restTemplate;
    private final AdmService admService;

    public KillmailPuller(final KillmailRepository killmailRepository,
                          final UserRepository userRepository,
                          final SolarSystemRepository solarSystemRepository,
                          final KillmailParser killmailParser,
                          final RestTemplate restTemplate,
                          final AdmService admService) {
        this.killmailRepository = killmailRepository;
        this.userRepository = userRepository;
        this.solarSystemRepository = solarSystemRepository;
        this.killmailParser = killmailParser;
        this.restTemplate = restTemplate;
        this.admService = admService;
    }

    @Async
    @Scheduled(cron = "0 * * * * *")
    public void cron() {
        final List<KillmailPackage> packages = new ArrayList<>();
        while (true) {
            RedisQResponse response = restTemplate.getForObject("https://zkillredisq.stream/listen.php?queueID=bravebuckaroos&ttw=1",
                RedisQResponse.class, new HashMap<>());

            if (response.getKillmailPackage() == null || packages.size() >= 100) {
                break;
            } else {
                log.info("Received a new killmail: {}", response.getKillmailPackage().getKillmail().getKillmailId());
            }

            packages.add(response.getKillmailPackage());
        }

        log.info("Collected a total of {} killmail packages.", packages.size());

        if (packages.isEmpty()) {
            return;
        }

        // required for isInBraveSystem
        systems = solarSystemRepository.findAllByTrackPvp(true).stream()
                                       .map(s -> s.getSystemId().intValue())
                                       .collect(toList());

        playerIds = userRepository.findAll().stream()
                                  .map(u -> u.getCharacterId().intValue())
                                  .collect(toList());

        final List<Killmail> killmails = packages.stream()
                                                 .peek(p -> log.debug("Processing package: {}", p))
                                                 .map(killmailParser::parseKillmail)
                                                 .filter(Objects::nonNull)
                                                 .filter(this::filterKillmail)
                                                 .peek(kill -> kill.setPoints(getPoints(kill.getPoints())))
                                                 .collect(toList());

        log.info("Saving {} new killmails.", killmails.size());
        if (killmails.isEmpty()) {
            return;
        }

        killmailRepository.save(killmails);
    }

    private long getPoints(final long points) {
        //final double adm = admService.getAdm(solarSystemId);
        return (long) Math.sqrt(points * 3);
    }

    private boolean filterKillmail(final Killmail killmail) {
        return hasBraveAttacker(killmail)
               && isVictimNotBrave(killmail)
               && isInBraveSystem(killmail)
               && isNotInFleet(killmail)
               && isNotAnEmptyPod(killmail)
               && hasNotBeenRetrievedYet(killmail);
    }

    private boolean hasBraveAttacker(final Killmail killmail) {
        for (Integer attackerId : killmail.getAttackerIds()) {
            if (playerIds.contains(attackerId)) {
                return true;
            }
        }
        log.info("{} is discarded as it does not have a Brave attacker.", killmail.getKillId());
        return false;
    }

    private boolean hasNotBeenRetrievedYet(final Killmail killmail) {
        final boolean killmailExists = killmailRepository.findByKillId(killmail.getKillId()).isPresent();
        if (killmailExists) {
            log.info("{} is discarded as it was already retrieved.", killmail.getKillId());
        }
        return !killmailExists;
    }

    private boolean isNotInFleet(final Killmail killmail) {
        final int attackerCount = killmail.getAttackerIds().size();
        final boolean le20 = attackerCount <= 20;
        if (!le20) {
            log.info("{} is discarded as it has {} attackers.", killmail.getKillId(), attackerCount);
        }
        return le20;
    }

    private boolean isNotAnEmptyPod(final Killmail killmail) {
        // Capsules are valued 10k
        final boolean emptyPod = killmail.getTotalValue() == 10_000L;
        if (emptyPod) {
            log.info("{} is discarded as it is an empty pod.", killmail.getKillId());
        }
        return !emptyPod;
    }

    private boolean isInBraveSystem(final Killmail killmail) {
        final boolean isInBraveSystem = systems.contains(killmail.getSolarSystemId());
        if (!isInBraveSystem) {
            log.info("{} is discarded as {} is not an activated system.", killmail.getKillId(), killmail.getSolarSystemId());
        }
        return isInBraveSystem;
    }

    private boolean isVictimNotBrave(final Killmail killmail) {
        final boolean isNotBrave = !killmail.getVictimGroupName().equals("Brave Collective");
        if (!isNotBrave) {
            log.info("{} is discarded as it has a Brave victim.", killmail.getKillId());
        }
        return isNotBrave;
    }
}
