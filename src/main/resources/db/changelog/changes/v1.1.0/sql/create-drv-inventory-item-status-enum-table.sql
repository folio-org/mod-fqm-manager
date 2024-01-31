CREATE TABLE IF NOT EXISTS drv_inventory_item_status_enum
(
    item_status   VARCHAR(256),
    CONSTRAINT item_status_unique UNIQUE (item_status)
);
