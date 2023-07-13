DROP FUNCTION if exists wiki.refresh_page_link_closure();

CREATE OR REPLACE FUNCTION wiki.refresh_page_link_closure()
RETURNS void
SECURITY DEFINER
AS $$
BEGIN
REFRESH MATERIALIZED VIEW wiki.page_link_closure;
RETURN;
END;
$$ LANGUAGE plpgsql;
