ALTER TABLE templates
    RENAME COLUMN enabled TO is_enabled;

ALTER TABLE signers
    RENAME COLUMN enabled TO is_enabled;
