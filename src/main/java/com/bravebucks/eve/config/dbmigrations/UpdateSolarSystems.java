package com.bravebucks.eve.config.dbmigrations;

import com.bravebucks.eve.domain.SolarSystem;
import com.github.cloudyrock.mongock.ChangeLog;
import com.github.cloudyrock.mongock.ChangeSet;

import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate;

/**
 * Creates the initial database setup
 */
@ChangeLog(order = "002")
public class UpdateSolarSystems {

    @ChangeSet(order = "02", author = "rihan", id = "02-updateSolarSystems")
    public void addAuthorities(MongockTemplate mongockTemplate) {
        mongockTemplate.findAll(SolarSystem.class).forEach(s -> {
            s.setTrackPvp(true);
            s.setTrackRatting(false);
            mongockTemplate.save(s);
        });
    }
}
