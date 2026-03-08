--liquibase formatted sql

--changeset termx:taskforge-drop-all-3
drop table if exists taskforge.task_activity;
drop table if exists taskforge.task_attachment;
drop table if exists taskforge.task_execution;
drop table if exists taskforge.task;
drop table if exists taskforge.workflow;
drop table if exists taskforge.project;
drop table if exists taskforge.space;
--
