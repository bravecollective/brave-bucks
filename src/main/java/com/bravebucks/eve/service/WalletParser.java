package com.bravebucks.eve.service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.bravebucks.eve.DelayService;
import com.bravebucks.eve.domain.EveCharacter;
import com.bravebucks.eve.domain.RattingEntry;
import com.bravebucks.eve.domain.esi.AccessTokenResponse;
import com.bravebucks.eve.domain.esi.WalletResponse;
import com.bravebucks.eve.repository.CharacterRepository;
import com.bravebucks.eve.repository.RattingEntryRepository;
import com.bravebucks.eve.repository.SolarSystemRepository;
import static com.bravebucks.eve.web.rest.UserJWTController.getBasicAuth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class WalletParser {

    private static final Logger log = LoggerFactory.getLogger(WalletParser.class);

    @Value("${WALLET_CLIENT_ID}")
    private String walletClientId;

    @Value("${WALLET_CLIENT_SECRET}")
    private String walletClientSecret;

    @Value("${OAUTH_TOKEN_URL}")
    private String oauthTokenUrl;

    @Value("${ESI_DOMAIN}")
    private String esiDomain;

    private final RestTemplate restTemplate;
    private final AdmService admService;
    private final RattingEntryRepository rattingEntryRepository;
    private final DelayService delayService;
    private final SolarSystemRepository solarSystemRepository;
    private final CharacterRepository characterRepository;

    public WalletParser(final RestTemplate restTemplate,
                        final AdmService admService,
                        final RattingEntryRepository rattingEntryRepository,
                        final DelayService delayService,
                        final SolarSystemRepository solarSystemRepository,
                        final CharacterRepository characterRepository) {
        this.restTemplate = restTemplate;
        this.admService = admService;
        this.rattingEntryRepository = rattingEntryRepository;
        this.delayService = delayService;
        this.solarSystemRepository = solarSystemRepository;
        this.characterRepository = characterRepository;
    }

    @SuppressWarnings("unused")
    @Async
    @Scheduled(cron = "0 */20 * * * *")
    public void collectNewJournalEntries() {
        final long start = System.currentTimeMillis();
        final Set<Integer> solarSystemIds = solarSystemRepository.findAllByTrackRatting(true).stream()
                                                                 .map(s -> s.getSystemId().intValue())
                                                                 .collect(Collectors.toSet());

        final List<EveCharacter> characters = characterRepository.findByWalletReadRefreshTokenNotNull();

        for (EveCharacter character : characters) {
            final int characterId = character.getId();

            if (delayService.shouldIChill()) {
                continue;
            }

            try {

                final String eTag = character.getWalletJournalEtag();
                final ResponseEntity<WalletResponse[]> walletResponse = getWalletResponse(character, eTag);

                if (walletResponse.getStatusCode() != HttpStatus.OK || walletResponse.getBody() == null) {
                    log.info("No new transactions for {} (wallet response is {}).", characterId,
                             walletResponse.getStatusCode());
                    continue;
                }

                updateEtag(character, eTag, walletResponse);

                final List<RattingEntry> characterRattingEntries = new ArrayList<>();
                for (WalletResponse walletEntry : walletResponse.getBody()) {
                    if ("bounty_prizes".equals(walletEntry.getRefType())
                        && rattingEntryRepository.countByJournalId(walletEntry.getId()) == 0) {

                        final Integer systemId = walletEntry.getContextId().intValue();
                        if (!solarSystemIds.contains(systemId)) {
                            continue;
                        }
                        final double adm = admService.getAdm(systemId);

                        final String[] killSplit = walletEntry.getReason().split(",");
                        int killCount = 0;
                        for (String killCounter : killSplit) {
                            killCount += Integer.parseInt(killCounter.split(": ")[1]);
                        }
                        final Instant instant = Instant.parse(walletEntry.getDate());

                        final RattingEntry rattingEntry = new RattingEntry(walletEntry.getId(),
                                                                           character.getOwningUser(),
                                                                           characterId, killCount, systemId,
                                                                           instant, adm);

                        characterRattingEntries.add(rattingEntry);
                    }
                }
                rattingEntryRepository.save(characterRattingEntries);
            } catch (final HttpServerErrorException | HttpClientErrorException exception) {
                log.info("No new transactions for {} (TQ status is {}): {}", characterId, exception.getStatusCode(),
                         exception.getMessage());
                if ("invalid_token".equals(exception.getStatusText())) {
                    character.setWalletReadRefreshToken(null);
                    characterRepository.save(character);
                    log.info("Deactivated tracking for {} due to invalid refresh token.", character.getId());
                }
            }
        }
        final long end = System.currentTimeMillis();
        log.info("Collecting transactions took {} seconds.", (end - start) / 1000);
    }

    private ResponseEntity<WalletResponse[]> getWalletResponse(final EveCharacter character,
                                                               final String eTag) {
        final AccessTokenResponse token = getAccessTokenWithRefreshToken(character, walletClientId, walletClientSecret);
        updateRefreshToken(character, token);

        final String walletUri = esiDomain + "/v6/characters/" + character.getId() + "/wallet/journal/";

        return restTemplate.exchange(walletUri, HttpMethod.GET, authorizedRequest(token.getAccessToken(),
                                                                                  eTag), WalletResponse[].class);
    }

    private void updateEtag(final EveCharacter character, final String eTag,
                            final ResponseEntity<WalletResponse[]> walletResponse) {
        final String responseETag = walletResponse.getHeaders().getFirst("ETag");
        if (!Objects.equals(responseETag, eTag)) {
            character.setWalletJournalEtag(responseETag);
            characterRepository.save(character);
        }
    }

    private void updateRefreshToken(final EveCharacter character, final AccessTokenResponse token) {
        final String refreshToken = token.getRefreshToken();
        if (!Objects.equals(character.getWalletReadRefreshToken(), refreshToken)) {
            character.setWalletReadRefreshToken(refreshToken);
            characterRepository.save(character);
        }
    }

    private AccessTokenResponse getAccessTokenWithRefreshToken(final EveCharacter character, final String clientId,
                                                  final String clientSecret) {
        final String refreshToken = character.getWalletReadRefreshToken();
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        final MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "refresh_token");
        map.add("refresh_token", refreshToken);
        final String authHeader = getBasicAuth(clientId, clientSecret);
        headers.add("Authorization", authHeader);

        final HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<AccessTokenResponse> response;
        try {
            response = restTemplate.postForEntity(oauthTokenUrl, request, AccessTokenResponse.class);
        } catch (final HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == 400) {
                String body = exception.getResponseBodyAsString();
                if (body.contains("error") && body.contains("invalid_grant")) {
                    // Invalid refresh token. Token missing/expired.
                    character.setWalletReadRefreshToken(null);
                    characterRepository.save(character);
                }
            }
            throw exception;
        }

        return response.getBody();
    }

    private static HttpEntity<Object> authorizedRequest(final String accessToken, final String etag) {
        final HttpHeaders headers = buildAuthHeader(accessToken);
        if (null != etag) {
            headers.add("If-None-Match", etag);
        }
        return new HttpEntity<>(null, headers);
    }

    private static HttpHeaders buildAuthHeader(final String accessToken) {
        final HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        return headers;
    }
}
