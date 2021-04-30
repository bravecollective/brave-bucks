[![codecov](https://codecov.io/gh/bahrmichael/brave-bucks/branch/master/graph/badge.svg)](https://codecov.io/gh/bahrmichael/brave-bucks)

Create EVE app:
- Callback URL: http://localhost:8080/#/callback
- Scopes: esi-wallet.read_character_wallet.v1

Run Docker dev env
```shell
cd src/main/docker
ln -s dev.yml docker-compose.yml
docker-compose up
```

Import DB dump - replace IP with your host IP:
```shell
mongorestore --uri mongodb://admin:password@192.168.1.2/brave-bucks ./dump-import-docker
```

Start/enter Docker Java container:
```shell
cd src/main/docker
docker-compose run --service-ports brave-bucks-java /bin/bash

# second console (find name with "docker ps")
docker exec -it docker_brave-bucks-java_run_f12850ef5370 /bin/bash
```

Build frontend:
```shell
./mvnw install -Dmaven.test.skip=true
ln -s /opt/brave-bucks/node/yarn/dist/bin/yarn /usr/local/bin/yarn
yarn install
yarn build
```

Run frontend dev server:
```shell
yarn start
```

Run dev - replace *** below and adjust data.mongodb.* values in `src/main/resources/config/application-dev.yml`
```shell
export SSO_URL='https://login.eveonline.com/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Flocalhost:8080%2F%23%2Fcallback&client_id=***&scope=&state=uniquestate123'
export CLIENT_ID=***
export CLIENT_SECRET=***
export WALLET_URL='https://login.eveonline.com/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Flocalhost:8080%2F%23%2Fcallback&client_id=***&scope=esi-wallet.read_character_wallet.v1&state=wallet'
export WALLET_CLIENT_ID=***
export WALLET_CLIENT_SECRET=***

./mvnw
```

Build WAR file and Docker container:
- copy `src/main/resources/config/application-prod.yml.dist` to `application-prod.yml` and 
  adjust jhipster.security.authentication.jwt.secret and data.mongodb.* values in it
- If you want use SSL for the web server: create the bucks_keystore.p12 file and activate the server.ssl configuration 
  in `application-prod.yml`
```shell
# run in the Docker Java dev container
./mvnw clean package -Pprod -DskipTests

# run on your host
docker build --no-cache --file src/main/docker/Dockerfile -t brave-bucks target
```

Run WAR file (prod) - replace *** and redirect_uri with your values
```shell
WALLET_CLIENT_ID=*** \
WALLET_CLIENT_SECRET=*** \
WALLET_URL='https://login.eveonline.com/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Fbucks.bravecollective.com%2F%23%2Fcallback&client_id=***&scope=esi-wallet.read_character_wallet.v1&state=wallet' \
CLIENT_ID=*** \
CLIENT_SECRET=*** \
SSO_URL='https://login.eveonline.com/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Fbucks.bravecollective.com%2F%23%2Fcallback&client_id=***&scope=&state=uniquestate123' \
java -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log -XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/home/bucks/dump.hprof -jar ./target/braveBucks-2.3.11.war &
```

Run Docker container - replace *** and redirect_uri with your values
```shell
docker run \
  --env WALLET_CLIENT_ID=*** \
  --env WALLET_CLIENT_SECRET=*** \
  --env WALLET_URL='https://login.eveonline.com/oauth/authorize/?response_type=code&redirect_uri=https%3A%2F%2Fbucks.bravecollective.com%2F%23%2Fcallback&client_id=***&scope=esi-wallet.read_character_wallet.v1&state=wallet' \
  --env CLIENT_ID=*** \
  --env CLIENT_SECRET=*** \
  --env SSO_URL='https://login.eveonline.com/oauth/authorize/?response_type=code&redirect_uri=https%3A%2F%2Fbucks.bravecollective.com%2F%23%2Fcallback&client_id=***&scope=&state=uniquestate123' \
  --network host \
  --rm brave-bucks
```
