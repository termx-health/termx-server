--liquibase formatted sql

--changeset kodality:audit.logged_actions
drop table if exists audit.logged_actions; 
CREATE TABLE audit.logged_actions (
    id bigserial primary key,
    schema_name text not null,
    table_name text not null,
    session_user_name text,
    action_tstamp_tx TIMESTAMP WITH TIME ZONE NOT NULL,
    action_tstamp_stm TIMESTAMP WITH TIME ZONE NOT NULL,
    transaction_id bigint,
    application_name text,
    client_addr inet,
    query_hash text,
    action TEXT NOT NULL CHECK (action IN ('I','D','U', 'T')),
    row_pk text,
    changed_fields hstore
);

COMMENT ON TABLE audit.logged_actions IS 'History of auditable actions on audited tables, from audit.if_modified_func()';
COMMENT ON COLUMN audit.logged_actions.id IS 'Unique identifier for each auditable event';
COMMENT ON COLUMN audit.logged_actions.schema_name IS 'Database schema audited table for this event is in';
COMMENT ON COLUMN audit.logged_actions.table_name IS 'Non-schema-qualified table name of table event occured in';
COMMENT ON COLUMN audit.logged_actions.session_user_name IS 'Login / session user whose statement caused the audited event';
COMMENT ON COLUMN audit.logged_actions.action_tstamp_tx IS 'Transaction start timestamp for tx in which audited event occurred';
COMMENT ON COLUMN audit.logged_actions.action_tstamp_stm IS 'Statement start timestamp for tx in which audited event occurred';
COMMENT ON COLUMN audit.logged_actions.transaction_id IS 'Identifier of transaction that made the change. May wrap, but unique paired with action_tstamp_tx.';
COMMENT ON COLUMN audit.logged_actions.client_addr IS 'IP address of client that issued query. Null for unix domain socket.';
COMMENT ON COLUMN audit.logged_actions.query_hash IS 'Top-level query that caused this auditable event. May be more than one statement.';
COMMENT ON COLUMN audit.logged_actions.application_name IS 'Application name set when this audit event occurred. Can be changed in-session by client.';
COMMENT ON COLUMN audit.logged_actions.action IS 'Action type; I = insert, D = delete, U = update, T = truncate';
COMMENT ON COLUMN audit.logged_actions.changed_fields IS 'New values of fields changed by UPDATE. Null except for row-level UPDATE events.';

CREATE INDEX logged_actions_tablename ON audit.logged_actions(table_name);
CREATE INDEX logged_actions_action_tstamp_tx_stm_idx ON audit.logged_actions(action_tstamp_stm);
CREATE INDEX logged_actions_row_pk_idx ON audit.logged_actions(row_pk);
--rollback drop table audit.logged_actions;

--changeset kodality:function-audit.key splitStatements:false
create or replace function audit.key(audit audit.logged_actions) returns text as $body$
begin
  return audit.schema_name || '.' || audit.table_name || ':' || audit.row_pk;
end;
$body$ LANGUAGE plpgsql immutable;
--

--changeset kodality:audit.logged_actions.logged_actions_key_idx
CREATE INDEX logged_actions_key_idx ON audit.logged_actions(audit.key(logged_actions));
--


