DROP FUNCTION if exists thesaurus.refresh_page_relation_closure();

CREATE OR REPLACE FUNCTION thesaurus.refresh_page_relation_closure()
RETURNS void
SECURITY DEFINER
AS $$
BEGIN
REFRESH MATERIALIZED VIEW thesaurus.page_relation_closure;
RETURN;
END;
$$ LANGUAGE plpgsql;
