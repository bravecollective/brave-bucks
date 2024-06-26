package com.bravebucks.eve.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;

import javax.annotation.PostConstruct;

import com.bravebucks.eve.domain.Donation;
import com.bravebucks.eve.domain.EveCharacter;
import com.bravebucks.eve.domain.Killmail;
import com.bravebucks.eve.domain.RattingEntry;
import com.bravebucks.eve.domain.Transaction;
import com.bravebucks.eve.domain.User;
import com.bravebucks.eve.repository.CharacterRepository;
import com.bravebucks.eve.repository.KillmailRepository;
import com.bravebucks.eve.repository.RattingEntryRepository;
import com.bravebucks.eve.repository.TransactionRepository;
import com.bravebucks.eve.repository.UserRepository;
import com.codahale.metrics.annotation.Timed;
import static com.bravebucks.eve.domain.Constants.ALLIANCE_ID;
import static com.bravebucks.eve.domain.enumeration.TransactionType.KILL;
import static com.bravebucks.eve.domain.enumeration.TransactionType.RATTING;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.github.jhipster.config.JHipsterConstants;

@Service
public class PayoutCalculator {
    private static final Logger log = LoggerFactory.getLogger(KillmailParser.class);

    @Value("${KILL_BUDGET}")
    private String envKillBudget;

    @Value("${RATTING_BUDGET}")
    private String envRattingBudget;

    private final KillmailRepository killmailRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final RattingEntryRepository rattingEntryRepository;
    private final CharacterRepository characterRepository;
    private final Environment env;

    @Autowired
    public PayoutCalculator(final KillmailRepository killmailRepository,
                            final UserRepository userRepository,
                            final TransactionRepository transactionRepository,
                            final RattingEntryRepository rattingEntryRepository,
                            final CharacterRepository characterRepository,
                            final Environment env) {
        this.killmailRepository = killmailRepository;
        this.userRepository = userRepository;
        this.rattingEntryRepository = rattingEntryRepository;
        this.transactionRepository = transactionRepository;
        this.characterRepository = characterRepository;
        this.env = env;
    }

    public PayoutCalculator(final KillmailRepository killmailRepository,
                            final UserRepository userRepository,
                            final TransactionRepository transactionRepository,
                            final RattingEntryRepository rattingEntryRepository,
                            final CharacterRepository characterRepository,
                            final Environment env,
                            String envKillBudget,
                            String envRattingBudget) {
        this.killmailRepository = killmailRepository;
        this.userRepository = userRepository;
        this.rattingEntryRepository = rattingEntryRepository;
        this.transactionRepository = transactionRepository;
        this.characterRepository = characterRepository;
        this.env = env;

        // only for unit tests
        this.envKillBudget = envKillBudget;
        this.envRattingBudget = envRattingBudget;
    }

    @PostConstruct
    public void init() {
        // dev only
        if (Arrays.asList(env.getActiveProfiles()).contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
            calculatePayouts();
        }
    }

    @Async
    @Timed
    @Scheduled(cron = "0 0 11 * * *")
    public void calculatePayouts() {
        if (envKillBudget == null || envRattingBudget == null) {
            log.error("Missing environment variables KILL_BUDGET and/or RATTING_BUDGET");
            return;
        }

        final List<User> users = userRepository.findAllByCharacterIdNotNullAndAllianceId(ALLIANCE_ID);
        final List<Integer> characterIds = users.stream().map(u -> u.getCharacterId().intValue()).collect(toList());

        final List<Killmail> pendingKillmails = killmailRepository.findPending();
        final Collection<Transaction> transactions = getKillmailTransactions(users, characterIds, pendingKillmails);
        pendingKillmails.forEach(km -> km.setPayoutCalculated(true));

        final Set<String> rattingUserIds = characterRepository.findByWalletReadRefreshTokenNotNull().stream().map(EveCharacter::getOwningUser).collect(Collectors.toSet());
        final List<User> rattingUsers = users.stream().filter(user -> rattingUserIds.contains(user.getId())).collect(toList());
        final List<RattingEntry> pendingRattingEntries = rattingEntryRepository.findByProcessed(false);
        transactions.addAll(getRattingTransactions(rattingUsers, pendingRattingEntries));
        pendingRattingEntries.forEach(e -> e.setProcessed(true));

        rattingEntryRepository.save(pendingRattingEntries);
        killmailRepository.save(pendingKillmails);
        transactionRepository.save(transactions);
    }

    private List<Transaction> getRattingTransactions(final List<User> rattingUsers,
                                                     final List<RattingEntry> pendingRattingEntries) {
        final List<Transaction> transactions = new ArrayList<>();
        final long totalPoints = getTotalRattingPoints(pendingRattingEntries, rattingUsers);
        final long todayBudget = Long.parseLong(envRattingBudget) / LocalDate.now().getMonth().maxLength();

        for (User user : rattingUsers) {
            final long pointsForUser = getRattingPointsForUser(pendingRattingEntries, user);
            if (pointsForUser == 0 || totalPoints == 0) {
                continue;
            }

            final double factor = (double) pointsForUser / totalPoints;
            final double userPayable = todayBudget * factor;
            final String userName = getUserName(rattingUsers, user.getCharacterId().intValue());
            transactions.add(new Transaction(userName, userPayable, RATTING));
        }

        return transactions;
    }

    private Collection<Transaction> getKillmailTransactions(final List<User> users, final List<Integer> characterIds,
                                                            final List<Killmail> pendingKillmails) {
        final long totalPoints = getTotalPoints(pendingKillmails, characterIds);
        final long todayBudget = Long.parseLong(envKillBudget) / LocalDate.now().getMonth().maxLength();

        final Collection<Transaction> transactions = new ArrayList<>();

        for (final Integer characterId : characterIds) {
            final long pointsForUser = getPointsForUser(pendingKillmails, characterId);
            if (pointsForUser == 0 || totalPoints == 0) {
                continue;
            }
            final double factor = (double) pointsForUser / totalPoints;
            final double userPayable = todayBudget * factor;
            final String user = getUserName(users, characterId);
            transactions.add(new Transaction(user, userPayable, KILL));
        }
        return transactions;
    }

    private long getTotalRattingPoints(final List<RattingEntry> pendingRattingEntries, final List<User> rattingUsers) {
        return rattingUsers.stream().mapToLong(user -> getRattingPointsForUser(pendingRattingEntries, user)).sum();
    }

    private long getTotalPoints(final Iterable<Killmail> killmails, final Collection<Integer> characterIds) {
        return characterIds.stream().mapToLong(id -> getPointsForUser(killmails, id)).sum();
    }

    private long getRattingPointsForUser(final List<RattingEntry> pendingRattingEntries,
                                         final User user) {
        List<Integer> characterIds = characterRepository.findByOwningUser(user.getId()).stream().map(EveCharacter::getId).collect(toList());

        long sum = 0;
        for (RattingEntry pendingRattingEntry : pendingRattingEntries) {
            if (characterIds.contains(pendingRattingEntry.getCharacterId())) {
                final double admWeight = 4 / (1 + pendingRattingEntry.getAdm());
                sum += pendingRattingEntry.getKillCount() * admWeight;
            }
        }
        return sum;
    }

    private long getPointsForUser(final Iterable<Killmail> killmails, final Integer characterId) {
        long sum = 0;
        for (final Killmail killmail : killmails) {
            final long points = killmail.getPoints();
            for (final Integer attackerId : killmail.getAttackerIds()) {
                if (Objects.equals(characterId, attackerId)) {
                    sum += points;
                }
            }

        }

        return sum;
    }

    private String getUserName(final Iterable<User> users, final Integer characterId) {
        // fallback with userId
        String user = String.valueOf(characterId);
        for (final User u : users) {
            if (Objects.equals(u.getCharacterId().intValue(), characterId)) {
                user = u.getLogin();
                break;
            }
        }
        return user;
    }

    double getRemainingWorth(final Donation donation, final LocalDate date) {
        final Instant monthBorder = getMonthBorder();
        final int monthLength = date.getMonth().maxLength();
        if (monthBorder.isAfter(donation.getCreated())) {
            return donation.getAmount() / monthLength;
        } else {
            // add 1, so we don't div by 0 at the end of the month
            return donation.getAmount() / (1 + monthLength -
                                           LocalDateTime.ofInstant(donation.getCreated(), ZoneId.systemDefault())
                                                        .getDayOfMonth());
        }
    }

    private Instant getMonthBorder() {
        final LocalDate now = LocalDate.now();
        final LocalDate of = LocalDate.of(now.getYear(), now.getMonth(), 1);
        return of.atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
