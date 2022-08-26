## Init postgres docker 
Or you can also use an existing database.

Pull postgres public image.
```bash 
docker pull postgres:14
```  
Run Docker container
```bash 
docker run -d --restart=unless-stopped --name terminology-postgres -p 5432:5432 -e POSTGRES_PASSWORD=postgres -e POSTGRES_USER=postgres -e POSTGRES_DB=postgres postgres:14
``` 
Connect and create database\users using the following command
```bash 
docker exec -i terminology-postgres psql -U postgres <<-EOSQL
CREATE ROLE termserver_admin LOGIN PASSWORD 'test' NOSUPERUSER INHERIT NOCREATEDB CREATEROLE NOREPLICATION;
CREATE ROLE termserver_app   LOGIN PASSWORD 'test' NOSUPERUSER INHERIT NOCREATEDB CREATEROLE NOREPLICATION;
CREATE ROLE termserver_viewer NOLOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE DATABASE termserver WITH OWNER = termserver_admin ENCODING = 'UTF8' TABLESPACE = pg_default CONNECTION LIMIT = -1;
grant temp on database termserver to termserver_app;
grant connect on database termserver to termserver_app;
CREATE EXTENSION IF NOT EXISTS hstore schema public;
EOSQL
```
In case you are using an existing database, run SQL commands between EOSQL via sql console.

## Run application
```bash 
./gradlew run
``` 
### Test
Query `http://localhost:8200/fhir/CapabilityStatement`

## Authentication

### Keyclock
The terminology server requires authenticated users. Any authentication server supporting Open-Id connect should suffice. For our development, 
we are using [Keycloak](https://www.keycloak.org/). Check official [docs](https://www.keycloak.org/guides#getting-started) for setup. 
Check the [example](https://wiki.kodality.dev/terminology-server/guide/authentication#keycloak) of the configuration.

### Yupi authentication
Add `-Pdev` when running.
```bash 
./gradlew run -Pdev
```
Pass **Bearer token** `yupi` in request Authorization header.

## Snowstorm 
Snowstorm server serves SNOMED terminology and may be installed if you need SNOMED. 
Check Snowstorm installation and configuration [documentation](https://wiki.kodality.dev/terminology-server/snowstorm).

After installation add properties `snowstorm.url`, `snowstorm.user`, `snowstorm.password` to `application.yml` file.
