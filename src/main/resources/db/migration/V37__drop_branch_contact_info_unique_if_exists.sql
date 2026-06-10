-- Drop any unique constraint on branches.contact_info that may have been created
-- by a previous Hibernate auto-ddl session. The column is not intended to be unique.
DO $$
DECLARE
    c TEXT;
BEGIN
    SELECT tc.constraint_name INTO c
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu
        ON tc.constraint_name = ccu.constraint_name
        AND tc.table_schema = ccu.table_schema
    WHERE tc.table_name = 'branches'
      AND ccu.column_name = 'contact_info'
      AND tc.constraint_type = 'UNIQUE'
    LIMIT 1;

    IF c IS NOT NULL THEN
        EXECUTE 'ALTER TABLE branches DROP CONSTRAINT ' || quote_ident(c);
        RAISE NOTICE 'Dropped unique constraint % on branches.contact_info', c;
    END IF;
END $$;
