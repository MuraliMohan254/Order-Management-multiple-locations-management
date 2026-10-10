ALTER TABLE system_config ADD COLUMN thermal_printing_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE system_config ADD COLUMN thermal_paper_size VARCHAR(20) DEFAULT '80mm';
ALTER TABLE system_config ADD COLUMN printer_name VARCHAR(255);
