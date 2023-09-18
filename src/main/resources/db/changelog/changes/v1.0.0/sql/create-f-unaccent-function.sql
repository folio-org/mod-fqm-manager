CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
CREATE OR REPLACE FUNCTION f_unaccent(
	text)
    RETURNS text
    LANGUAGE 'sql'
    COST 100
    IMMUTABLE STRICT PARALLEL SAFE
AS $BODY$
        SELECT public.unaccent('public.unaccent', $1)  -- schema-qualify function and dictionary

$BODY$;
