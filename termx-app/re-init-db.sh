#!/usr/bin/env bash
docker rm -vf tx-pg
docker run -d --restart=unless-stopped --name tx-pg -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:18
sleep 3

docker exec -i tx-pg psql -U postgres <<-EOSQL
CREATE ROLE tx_admin LOGIN PASSWORD 'test' NOSUPERUSER INHERIT NOCREATEDB CREATEROLE NOREPLICATION;
CREATE ROLE tx_app   LOGIN PASSWORD 'test' NOSUPERUSER INHERIT NOCREATEDB CREATEROLE NOREPLICATION;
CREATE ROLE tx_viewer NOLOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE DATABASE termx  WITH OWNER = tx_admin ENCODING = 'UTF8' TABLESPACE = pg_default CONNECTION LIMIT = -1;
grant temp on database termx to tx_app;
grant connect on database termx to tx_app;
CREATE EXTENSION IF NOT EXISTS hstore schema public;
EOSQL
