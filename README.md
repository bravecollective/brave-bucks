[![codecov](https://codecov.io/gh/bahrmichael/brave-bucks/branch/master/graph/badge.svg)](https://codecov.io/gh/bahrmichael/brave-bucks)

Create EVE app:
- Callback URL: http://localhost:8080/#/callback
- Scopes: esi-wallet.read_character_wallet.v1

Run Docker dev env
```
cd src/main/docker
ln -s dev.yml docker-compose.yml
docker-compose up
```

Import DB dump and add permissions - replace IP with your host IP:
```
mongorestore --uri mongodb://admin:password@192.168.1.2/brave-bucks ./dump-import-docker

mongo "mongodb://admin:password@192.168.1.2/admin?authSource=admin"
db.createRole({role : "readWriteSystem", privileges: [{resource: { db: "brave-bucks", collection: "system.indexes" }, actions: [ "changeStream", "collStats", "convertToCapped", "createCollection", "createIndex", "dbHash", "dbStats", "dropCollection", "dropIndex", "emptycapped", "find", "insert", "killCursors", "listCollections", "listIndexes", "planCacheRead", "remove", "renameCollectionSameDB", "update" ]}], roles:[]})
db.grantRolesToUser('admin', ['readWriteSystem'])
quit()
```

Start/enter Docker Java container:
```
cd src/main/docker
docker-compose run --service-ports brave-bucks-java /bin/bash
```

Build app:
```
./mvnw install -Dmaven.test.skip=true
ln -s /opt/brave-bucks/node/yarn/dist/bin/yarn /usr/local/bin/yarn
yarn install
yarn build
```

Run dev - replace *** below and adjust data.mongodb.* values in `src/main/resources/config/application-dev.yml`
```
export SSO_URL='https://login.eveonline.com/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Flocalhost:8080%2F%23%2Fcallback&client_id=***&scope=&state=uniquestate123'
export CLIENT_ID=***
export CLIENT_SECRET=***
export WALLET_URL='https://login.eveonline.com/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Flocalhost:8080%2F%23%2Fcallback&client_id=***&scope=esi-wallet.read_character_wallet.v1&state=wallet'
export WALLET_CLIENT_ID=***
export WALLET_CLIENT_SECRET=***

./mvnw
```

Create WAR file and run prod:
- replace *** below
- copy `src/main/resources/config/application-prod.yml.dist` to 
`application-prod.yml` and adjust jhipster.security.authentication.jwt.secret and data.mongodb.* values in it
- create bucks_keystore.p12 for the HTTPS certificate, see server.ssl in `application-prod.yml.dist`
```
./mvnw clean package -Pprod -DskipTests

WALLET_CLIENT_ID=*** \
WALLET_CLIENT_SECRET=*** \
WALLET_URL='https://login.eveonline.com/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Fbucks.bravecollective.com%2F%23%2Fcallback&client_id=***&scope=esi-wallet.read_character_wallet.v1&state=wallet' \
CLIENT_ID=*** \
CLIENT_SECRET=*** \
SSO_URL='https://login.eveonline.com/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Fbucks.bravecollective.com%2F%23%2Fcallback&client_id=***&scope=&state=uniquestate123' \
java -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log -XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/home/bucks/dump.hprof -jar ./target/braveBucks-2.3.9.war &
```
