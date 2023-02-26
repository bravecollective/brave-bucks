## Brave Bucks

### Changelog

#### 2.7.0

- **Breaking Change**: Added KILL_BUDGET and RATTING_BUDGET environment variables.

#### 2.6.1

Added SERVER_PORT environment variable.

#### 2.6.0

Switched to EVE SSOv2

- **Breaking Change**: The callback URL changed to `http://your.domain.tld/api/callback`, adjust on
  developers.eveonline.com and in the environment variables SSO_URL and WALLET_URL.
- **Breaking Change**: The OAuth login URL in the environment variables SSO_URL and WALLET_URL changed to
  `https://login.eveonline.com/v2/oauth/authorize/`.

### Setup

Create EVE app for development:
- Callback URL: http://localhost:8080/api/callback
- Scopes: esi-wallet.read_character_wallet.v1

Run Docker development environment:
```shell
cd src/main/docker
ln -s dev.yml docker-compose.yml
docker-compose up
```

Import DB dump - replace IP with your host IP, if necessary:
```shell
mongorestore --uri mongodb://admin:password@172.17.0.1:27017/brave-bucks?authSource=admin --drop --gzip --archive=brave-bucks.archive --nsInclude brave-bucks.*
```

Start and enter Docker Java container:
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

Run frontend development server:
```shell
yarn start
```

Run backend in development mode - replace *** and your Docker host IP for MongoDB, if necessary:
```shell
export SERVER_PORT=8080
export SSO_URL='https://login.eveonline.com/v2/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Flocalhost:8080%2Fapi%2Fcallback&client_id=***&scope=&state=uniquestate123'
export CLIENT_ID=***
export CLIENT_SECRET=***
export WALLET_URL='https://login.eveonline.com/v2/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Flocalhost:8080%2Fapi%2Fcallback&client_id=***&scope=esi-wallet.read_character_wallet.v1&state=wallet'
export WALLET_CLIENT_ID=***
export WALLET_CLIENT_SECRET=***
export MONGO_URI=mongodb://admin:password@172.17.0.1:27017/brave-bucks?authSource=admin
export MONGO_DB=brave-bucks
export JWT_SECRET=my-secret-token-to-change-in-production
export KILL_BUDGET=9000000000
export RATTING_BUDGET=2000000000

./mvnw
```

Build WAR file and Docker container for production mode:
- Copy `src/main/resources/config/application-prod.yml.dist` to `application-prod.yml`
- If you want use SSL for the web server: create the bucks_keystore.p12 file, change the server.port and activate 
  the server.ssl configuration in `application-prod.yml`
```shell
# run in the Docker Java dev container
./mvnw clean package -Pprod -DskipTests

# run on your host
docker build --no-cache --file src/main/docker/Dockerfile -t brave-bucks target
```

Run WAR file - replace *** and values for MONGO_URI, MONGO_DB, JWT_SECRET and redirect_uri with your values:
```shell
export SERVER_PORT=8080
WALLET_CLIENT_ID=*** \
WALLET_CLIENT_SECRET=*** \
WALLET_URL='https://login.eveonline.com/v2/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Fbucks.bravecollective.com%2Fapi%2Fcallback&client_id=***&scope=esi-wallet.read_character_wallet.v1&state=wallet' \
CLIENT_ID=*** \
CLIENT_SECRET=*** \
SSO_URL='https://login.eveonline.com/v2/oauth/authorize/?response_type=code&redirect_uri=http%3A%2F%2Fbucks.bravecollective.com%2Fapi%2Fcallback&client_id=***&scope=&state=uniquestate123' \
MONGO_URI='mongodb://user:pass@cluster.mongodb.net:27017/bucks?ssl=true&replicaSet=atlas-xyz-shard&authSource=admin&retryWrites=true&w=majority' \
MONGO_DB=bucks \
JWT_SECRET=my-secret-token-to-change-in-production \
KILL_BUDGET=9000000000 \
RATTING_BUDGET=2000000000 \
java -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log -XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/home/bucks/dump.hprof -jar ./target/braveBucks-*.war &
```

Run Docker container - replace *** and values for MONGO_URI, MONGO_DB, JWT_SECRET and redirect_uri with your values:
```shell
docker run \
  --env SERVER_PORT=8080 \
  --env WALLET_CLIENT_ID=*** \
  --env WALLET_CLIENT_SECRET=*** \
  --env WALLET_URL='https://login.eveonline.com/v2/oauth/authorize/?response_type=code&redirect_uri=https%3A%2F%2Fbucks.bravecollective.com%2Fapi%2Fcallback&client_id=***&scope=esi-wallet.read_character_wallet.v1&state=wallet' \
  --env CLIENT_ID=*** \
  --env CLIENT_SECRET=*** \
  --env SSO_URL='https://login.eveonline.com/v2/oauth/authorize/?response_type=code&redirect_uri=https%3A%2F%2Fbucks.bravecollective.com%2Fapi%2Fcallback&client_id=***&scope=&state=uniquestate123' \
  --env MONGO_URI='mongodb://user:pass@cluster.mongodb.net:27017/bucks?ssl=true&replicaSet=atlas-xyz-shard&authSource=admin&retryWrites=true&w=majority' \
  --env MONGO_DB=bucks \
  --env JWT_SECRET=my-secret-token-to-change-in-production \
  --env KILL_BUDGET=9000000000 \
  --env RATTING_BUDGET=2000000000 \
  --network host \
  --rm brave-bucks
```

The Docker container is also available at https://hub.docker.com/r/bravecollective/brave-bucks.
