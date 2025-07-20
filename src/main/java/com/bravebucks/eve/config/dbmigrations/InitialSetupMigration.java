package com.bravebucks.eve.config.dbmigrations;

import com.bravebucks.eve.domain.Authority;
import com.bravebucks.eve.security.AuthoritiesConstants;

import com.github.cloudyrock.mongock.ChangeLog;
import com.github.cloudyrock.mongock.ChangeSet;
import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate;

/**
 * Creates the initial database setup
 */
@ChangeLog(order = "001")
public class InitialSetupMigration {

    @ChangeSet(order = "01", author = "initiator", id = "01-addAuthorities")
    public void addAuthorities(MongockTemplate mongockTemplate) {
        Authority adminAuthority = new Authority();
        adminAuthority.setName(AuthoritiesConstants.ADMIN);
        Authority userAuthority = new Authority();
        userAuthority.setName(AuthoritiesConstants.USER);
        Authority managerAuthority = new Authority();
        managerAuthority.setName(AuthoritiesConstants.MANAGER);
        mongockTemplate.save(adminAuthority);
        mongockTemplate.save(userAuthority);
        mongockTemplate.save(managerAuthority);
    }
}
