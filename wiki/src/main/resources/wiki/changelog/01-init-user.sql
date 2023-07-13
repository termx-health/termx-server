--liquibase formatted sql

--changeset wiki:init-session-user runAlways:true
select core.set_user('liquibase');
--rollback select 1 from dual;

