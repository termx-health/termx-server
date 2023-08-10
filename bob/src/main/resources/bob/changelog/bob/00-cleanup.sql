--liquibase formatted sql

--changeset bob:drop-all
drop table if exists bob.object;
drop table if exists bob.object_storage;
--
