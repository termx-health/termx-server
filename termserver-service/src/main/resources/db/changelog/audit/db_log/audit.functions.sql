
CREATE OR REPLACE FUNCTION audit.if_modified_func() RETURNS TRIGGER AS $body$
DECLARE
    audit_row audit.logged_actions;
    include_values boolean;
    log_diffs boolean;
    h_old hstore;
    h_new hstore;
    excluded_cols text[] = ARRAY[]::text[];
    pk_attr text;
    query_md5_hash text;
BEGIN
    IF TG_WHEN <> 'AFTER' THEN
        RAISE EXCEPTION 'audit.if_modified_func() may only run as an AFTER trigger';
    END IF;
    query_md5_hash = md5(current_query());
    IF query_md5_hash IS NOT NULL AND NOT EXISTS (SELECT 1 FROM audit.query_hashes WHERE hash = query_md5_hash) THEN
        INSERT INTO audit.query_hashes (hash, query) VALUES (query_md5_hash, current_query());
    END IF;

    audit_row = ROW(
        nextval('audit.logged_actions_id_seq'),       -- id
        TG_TABLE_SCHEMA::text,                        -- schema_name
        TG_TABLE_NAME::text,                          -- table_name
        coalesce(core.session_user(),session_user)::text,   -- session_user_name
        current_timestamp,                            -- action_tstamp_tx
        statement_timestamp(),                        -- action_tstamp_stm
        txid_current(),                               -- transaction ID
        current_setting('application_name'),          -- client application
        inet_client_addr(),                           -- client_addr
        query_md5_hash,                               -- md5 hash of current query
        substring(TG_OP,1,1),                         -- action
        NULL, NULL                                   -- row_pk, changed_fields
        );

    IF NOT TG_ARGV[0]::boolean IS DISTINCT FROM 'f'::boolean THEN
        audit_row.query_hash = NULL;
    END IF;

    IF TG_ARGV[1] IS NOT NULL THEN
        excluded_cols = TG_ARGV[1]::text[];
    END IF;
    
    IF TG_ARGV[2] IS NOT NULL THEN
        pk_attr = TG_ARGV[2]::text;
    END IF;
    
    IF (TG_OP = 'UPDATE' AND TG_LEVEL = 'ROW') THEN
        audit_row.changed_fields =  (hstore(NEW.*) - hstore(OLD.*)) - excluded_cols;
        IF audit_row.changed_fields = hstore('') THEN
            RETURN NULL; -- All changed fields are ignored. Skip this update.
        END IF;
        IF pk_attr IS NOT NULL THEN
          audit_row.row_pk = hstore(NEW.*) -> pk_attr;
        END IF;
    ELSIF (TG_OP = 'DELETE' AND TG_LEVEL = 'ROW') THEN
        audit_row.changed_fields =  hstore(OLD.*) - excluded_cols;
        IF pk_attr IS NOT NULL THEN
          audit_row.row_pk = hstore(OLD.*) -> pk_attr;
        END IF;
    ELSIF (TG_OP = 'INSERT' AND TG_LEVEL = 'ROW') THEN
        audit_row.changed_fields =  hstore(NEW.*) - excluded_cols;
        IF pk_attr IS NOT NULL THEN
          audit_row.row_pk = hstore(NEW.*) -> pk_attr;
        END IF; 
    ELSIF (TG_LEVEL = 'STATEMENT' AND TG_OP IN ('INSERT','UPDATE','DELETE','TRUNCATE')) THEN
        --audit_row.statement_only = 't';
    ELSE
        RAISE EXCEPTION '[audit.if_modified_func] - Trigger func added as trigger for unhandled case: %, %',TG_OP, TG_LEVEL;
        RETURN NULL;
    END IF;
    INSERT INTO audit.logged_actions VALUES (audit_row.*);
    RETURN NULL;
END;
$body$
LANGUAGE plpgsql
SECURITY DEFINER;

COMMENT ON FUNCTION audit.if_modified_func() IS $body$
Track changes to a table at the statement and/or row level.

Optional parameters to trigger in CREATE TRIGGER call:

param 0: boolean, whether to log the query text. Default 't'.

param 1: text[], columns to ignore in updates. Default [].
         
param 2: name of pk attr

There is no parameter to disable logging of values. Add this trigger as
a 'FOR EACH STATEMENT' rather than 'FOR EACH ROW' trigger if you do not
want to log row values.

Note that the user name logged is the login role for the session. The audit trigger
cannot obtain the active role because it is reset by the SECURITY DEFINER invocation
of the audit trigger its self.
$body$;



CREATE OR REPLACE FUNCTION audit.add_log(target_table regclass, audit_rows boolean, audit_query_text boolean, ignored_cols text[]) RETURNS void AS $body$
DECLARE
  stm_targets text = 'INSERT OR UPDATE OR DELETE OR TRUNCATE';
  _q_txt text;
  _ignored_cols_snip text = '';
  _pk_attr text;
BEGIN
    EXECUTE 'DROP TRIGGER IF EXISTS audit_trigger_row ON ' || quote_ident(target_table::TEXT);
    EXECUTE 'DROP TRIGGER IF EXISTS audit_trigger_stm ON ' || quote_ident(target_table::TEXT);
    
    SELECT ',' || a.attname into _pk_attr
    FROM   pg_index i
    JOIN   pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
    WHERE  i.indrelid = target_table
    AND    i.indisprimary;

    IF audit_rows THEN
        _ignored_cols_snip = ', ' || quote_literal(ignored_cols);
        _q_txt = 'CREATE TRIGGER audit_trigger_row AFTER INSERT OR UPDATE OR DELETE ON ' || 
                 quote_ident(target_table::TEXT) || 
                 ' FOR EACH ROW EXECUTE PROCEDURE audit.if_modified_func(' ||
                 quote_literal(audit_query_text) || _ignored_cols_snip || _pk_attr || ');';
        RAISE NOTICE '%',_q_txt;
        EXECUTE _q_txt;
        stm_targets = 'TRUNCATE';
    ELSE
    END IF;

    _q_txt = 'CREATE TRIGGER audit_trigger_stm AFTER ' || stm_targets || ' ON ' ||
             target_table ||
             ' FOR EACH STATEMENT EXECUTE PROCEDURE audit.if_modified_func('||
             quote_literal(audit_query_text) || ');';
    RAISE NOTICE '%',_q_txt;
    EXECUTE _q_txt;

END;
$body$
language 'plpgsql';

COMMENT ON FUNCTION audit.add_log(regclass, boolean, boolean, text[]) IS $body$
Add auditing support to a table.

Arguments:
   target_table:     Table name, schema qualified if not on search_path
   audit_rows:       Record each row change, or only audit at a statement level
   audit_query_text: Record the text of the client query that triggered the audit event?
   ignored_cols:     Columns to exclude from update diffs, ignore updates that change only ignored cols.
$body$;

CREATE OR REPLACE FUNCTION audit.add_log(target_table regclass, audit_rows boolean, audit_query_text boolean) RETURNS void AS $body$
SELECT audit.add_log($1, $2, $3, ARRAY[]::text[]);
$body$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION audit.add_log(target_table regclass) RETURNS void AS $body$
SELECT audit.add_log($1, BOOLEAN 't', BOOLEAN 't');
$body$ LANGUAGE 'sql';


CREATE OR REPLACE FUNCTION audit.drop_log(target_table regclass) RETURNS void AS $body$
BEGIN
	if EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = lower(quote_ident(target_table::TEXT))) THEN
      EXECUTE 'DROP TRIGGER IF EXISTS audit_trigger_row ON ' || quote_ident(target_table::TEXT);
      EXECUTE 'DROP TRIGGER IF EXISTS audit_trigger_stm ON ' || quote_ident(target_table::TEXT);
    end if;
END;
$body$ LANGUAGE 'plpgsql';

