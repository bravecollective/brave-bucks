package com.bravebucks.eve.config.dbmigrations;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.bravebucks.eve.domain.PersistentAuditEvent;
import com.mongodb.MongoNamespace;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.ScriptOperations;
import org.springframework.data.mongodb.core.script.ExecutableMongoScript;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates the initial database setup
 */
@ChangeLog(order = "004")
public class CL004_MigratePersistentAuditEventID {

    private final String TARGET_COLLECTION_NAME = "jhi_persistent_audit_event";
    private final String BACKUP_COLLECTION_NAME = TARGET_COLLECTION_NAME + "_PRE_MIGRATION";
    private final int MAX_COUNT = 5;

    private boolean testSetupSuccessful;

    @ChangeSet(order = "01",
               author = "troyburn",
               id = "004-01-migratePersistentAuditEventID")
    public void renamePersistentAuditEventID(MongoTemplate mongoTemplate) {
        // Due to spring-data-mongodb 2.1.x changes
        // Because _id is special we need to potentially migrate
        // We are trying to rename event_id => _id (as the entity model for 'PersistentAuditEvent' looked and looks now)
        // The production system we have has not ever created this collection (hence this is migration strategy is untested)

        boolean exists = mongoTemplate.collectionExists(TARGET_COLLECTION_NAME);
        if(exists) {
            // This code is not tested well enough for such a critically named object
            //  this is why we throw an exception to prevent startup if we encounter
            //  the collection exists in your environment.

            // Here is code that may help you test this migration, use at your own risk.
            //
            // migrateTest(mongoTemplate);

            // Here is where the live migration is supposed to be:
            //
            // doMigrate(mongoTemplate);

            // If you manage to test and fix this class please push you commit back upstream, thanks.

            throw new RuntimeException("CL004_MigratePersistentAuditEventID: read and test the code on migration");
        }

    }

    private void doMigrate(MongoTemplate mongoTemplate) {
        // Rename existing data
        mongoTemplate
            .getCollection(TARGET_COLLECTION_NAME)
            .renameCollection(new MongoNamespace(
                mongoTemplate.getDb().getName(),
                BACKUP_COLLECTION_NAME
            ));

        mongoTemplate.getDb().createCollection(TARGET_COLLECTION_NAME);

        MongoCollection<Document> coll = mongoTemplate.getCollection(BACKUP_COLLECTION_NAME);
        for(Document dd : coll.find()) {
            BsonDocument d = dd.toBsonDocument(PersistentAuditEvent.class, mongoTemplate.getDb().getCodecRegistry());
            BsonDocument newDoc = processOne(d);
            mongoTemplate.save(newDoc, TARGET_COLLECTION_NAME);
            // Another thing you can do here is insert
            // mongoTemplate.insert(newDoc, TARGET_COLLECTION_NAME);
        }
    }

    private BsonDocument processOne(BsonDocument o) {
        // Was it like this ?
        //  {
        //    "eventId": {"$oid": "613c04d61aeb3b1cad5ae894"}, ...
        //  }
        // or like this ?
        //  {
        //    "_id": {"$oid": "613c04d61aeb3b1cad5ae894"},
        //    "eventId": {"$oid": "613c04d61aeb3b1cad5ae8ff"}, ...
        //  }
        // or maybe _id and eventId were identical IDs ?
        //

        BsonDocument d = o.clone();

        BsonValue eventId = o.getOrDefault("eventId", null);
        BsonValue id = o.getOrDefault("_id", null);

        if(id != null) {
            if(eventId != null) {
                if(id.equals(eventId)) {
                    // NOOP
                }
            }
        } else if(eventId != null) {
            if(eventId.getBsonType() == BsonType.OBJECT_ID) {   // straight rename
                d.put("_id", eventId);
                d.remove("eventId");
            }
        }

        return d;
    }

    /////////  ALTERNATIVE METHODS

    private void scriptOpsMethod(MongoTemplate mongoTemplate) {
        ScriptOperations scriptOps = mongoTemplate.scriptOps();

        // FIXME this script does not work, it has no side effects,
        //  it is an example for MongoDB ninja's to create their own optimized migration script for bulk records
        final String SCRIPT = "" + "function(x) {" +
            "  var count = db.jhi_persistent_audit_event.find().size();" +
            "  var doc = { count: count };" +
            "  return doc;" +
            "}";
        ExecutableMongoScript migrateCs004Script = new ExecutableMongoScript(SCRIPT);
        Object obj = scriptOps.execute(migrateCs004Script, "script args go here");
        assert (obj != null);
        assert (obj instanceof Document);
        Document doc = (Document) obj;
        assert (doc.size() > 0);
        Object dblObj = doc.getOrDefault("count", null);
        assert (dblObj != null);
        assert (dblObj instanceof Number);
        assert (dblObj instanceof Double);
        Double count = (Double) dblObj; //doc.getDouble("count", null);
        assert (count != null);
    }

    /////////  DIAGNOSTIC METHODS

    private void dumpCollectionNames(MongoTemplate mongoTemplate) {
        for(String col : mongoTemplate.getCollectionNames())
            System.err.println("mongoTemplate#getCollectionNames:" + col);
        dumpCollection(mongoTemplate, "mongockChangeLog", -1);
    }

    private void dumpCollection(MongoTemplate mongoTemplate, String collectionName, int maxCount) {
        MongoCollection<Document> cldoc = mongoTemplate.getCollection(collectionName);
        if(cldoc == null)
            return;
        System.err.println(collectionName + ".count=" + cldoc.countDocuments());
        int i = 0;
        FindIterable<Document> iterable = (maxCount < 0) ? cldoc.find() : cldoc.find().limit(maxCount);
        for(Document d : iterable) {
            System.err.println("[" + i + "] => " + d.toJson());
            i++;
        }
    }

    /////////  ALL TEST CODE BELOW

    private PersistentAuditEvent buildPersistentAuditEvent(String id, String auditEventType, Instant auditEventDate, Map<String, String> data) {
        PersistentAuditEvent pae = new PersistentAuditEvent();
        pae.setId(id);
        pae.setAuditEventType(auditEventType);
        pae.setAuditEventDate(auditEventDate);
        pae.setData(data);
        return pae;
    }

    private void setupTestSyntheticRecords(MongoTemplate mongoTemplate) {
        boolean exists = mongoTemplate.collectionExists(TARGET_COLLECTION_NAME);
        if(exists)
            throw new RuntimeException("setupTestSyntheticRecords: " + TARGET_COLLECTION_NAME);

        exists = mongoTemplate.collectionExists(BACKUP_COLLECTION_NAME);
        if(exists)
            throw new RuntimeException("setupTestSyntheticRecords: " + BACKUP_COLLECTION_NAME);

        mongoTemplate.getDb().createCollection(TARGET_COLLECTION_NAME);

        for(int i = 0; i < 5; i++) {
            String ii = String.valueOf(i);

            Map<String,String> dataMap = new HashMap<>();
            dataMap.put("key1_" + ii, "value1_" + ii);
            dataMap.put("key2_" + ii, "value2_" + ii);

            String id = (i != 1 && i != 3) ? new ObjectId().toString() : null;
            if(i == 3)
                id = "EVENT_ID_0011223344";

            Map<String,String> data = (i != 2 && i != 3) ? dataMap : null;

            PersistentAuditEvent pae = buildPersistentAuditEvent(id, "004-01-MIGRATE-" + ii, Instant.now(), data);
            mongoTemplate.save(pae);

            // Need to add eventId and both
        }

        testSetupSuccessful = true;
    }

    private void teardownTestSyntheticRecords(MongoTemplate mongoTemplate) {
        if(testSetupSuccessful) {
            mongoTemplate.dropCollection(TARGET_COLLECTION_NAME);
            mongoTemplate.dropCollection(BACKUP_COLLECTION_NAME);
        }
    }

    private void migrateTest(MongoTemplate mongoTemplate) {
        boolean exists = mongoTemplate.collectionExists(TARGET_COLLECTION_NAME);
        System.err.println("mongoTemplate#collectionExists(\"" + TARGET_COLLECTION_NAME + "\"):" + exists);

        if(exists) {
            // Looks like a LIVE system
            doMigrate(mongoTemplate); // LIVE
        } else {
            // Okay we setup test records and perform test migration on those records
            //  this can help you simulate your system in code and verify output
            setupTestSyntheticRecords(mongoTemplate);

            dumpCollection(mongoTemplate, TARGET_COLLECTION_NAME, MAX_COUNT);
            dumpCollection(mongoTemplate, BACKUP_COLLECTION_NAME, MAX_COUNT);

            scriptOpsMethod(mongoTemplate);       // NOOP

            doMigrate(mongoTemplate); // on TEST data

            dumpCollection(mongoTemplate, TARGET_COLLECTION_NAME, MAX_COUNT);
            dumpCollection(mongoTemplate, BACKUP_COLLECTION_NAME, MAX_COUNT);

            teardownTestSyntheticRecords(mongoTemplate);
        }

        dumpCollection(mongoTemplate, TARGET_COLLECTION_NAME, MAX_COUNT);
        dumpCollection(mongoTemplate, BACKUP_COLLECTION_NAME, MAX_COUNT);
    }

}
