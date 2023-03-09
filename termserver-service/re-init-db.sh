#!/usr/bin/env bash
docker rm -vf terminology-postgres
docker run -d --restart=unless-stopped --name terminology-postgres -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:14
sleep 3

docker exec -i terminology-postgres psql -U postgres <<-EOSQL
CREATE ROLE termserver_admin LOGIN PASSWORD 'test' NOSUPERUSER INHERIT NOCREATEDB CREATEROLE NOREPLICATION;
CREATE ROLE termserver_app   LOGIN PASSWORD 'test' NOSUPERUSER INHERIT NOCREATEDB CREATEROLE NOREPLICATION;
CREATE ROLE termserver_viewer NOLOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE DATABASE termserver WITH OWNER = termserver_admin ENCODING = 'UTF8' TABLESPACE = pg_default CONNECTION LIMIT = -1;
grant temp on database termserver to termserver_app;
grant connect on database termserver to termserver_app;
CREATE EXTENSION IF NOT EXISTS hstore schema public;
EOSQL
