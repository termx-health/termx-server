DROP FUNCTION if exists thesaurus.refresh_page_link_closure();

CREATE OR REPLACE FUNCTION thesaurus.refresh_page_link_closure()
RETURNS void
SECURITY DEFINER
AS $$
BEGIN
REFRESH MATERIALIZED VIEW thesaurus.page_link_closure;
RETURN;
END;
$$ LANGUAGE plpgsql;
