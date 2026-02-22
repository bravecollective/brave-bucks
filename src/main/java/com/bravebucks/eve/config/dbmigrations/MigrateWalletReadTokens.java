package com.bravebucks.eve.config.dbmigrations;

import com.bravebucks.eve.domain.EveCharacter;
import com.bravebucks.eve.domain.User;
import com.github.cloudyrock.mongock.ChangeLog;
import com.github.cloudyrock.mongock.ChangeSet;

import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate;

/**
 * Creates the initial database setup
 */
@ChangeLog(order = "003")
public class MigrateWalletReadTokens {

    @ChangeSet(order = "01",
               author = "rihan",
               id = "01-migrateWalletReadTokens")
    public void addAuthorities(MongockTemplate mongockTemplate) {
        mongockTemplate.findAll(User.class).stream()
                     .filter(u -> u.getWalletReadRefreshTokens() != null)
                     .forEach(user -> {
                         user.getWalletReadRefreshTokens().forEach((characterId, refreshToken) -> {
                             EveCharacter character = new EveCharacter();
                             character.setId(characterId);
                             character.setOwningUser(user.getId());
                             character.setWalletReadRefreshToken(refreshToken);
                             mongockTemplate.save(character);
                         });
                         user.setWalletReadRefreshTokens(null);
                         mongockTemplate.save(user);
                     });
    }
}
