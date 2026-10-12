-- V3: click attribution (ambiguous scenario, task T-202; decision in harness/requirements/R-03.md).
-- Adds the referring site's host and the three campaign parameters carried on the short link
-- request. The privacy position of ADR 0006 is unchanged: there is still no column for an IP
-- address, a user agent, a full referrer URL, a location or a device. Every value is nullable and
-- bounded (host 253, campaign fields 64). One ALTER TABLE ... ADD COLUMN per statement because H2
-- and PostgreSQL disagree on the multi-column form.

ALTER TABLE click_events ADD COLUMN referrer_host VARCHAR(253);

ALTER TABLE click_events ADD COLUMN utm_source VARCHAR(64);

ALTER TABLE click_events ADD COLUMN utm_medium VARCHAR(64);

ALTER TABLE click_events ADD COLUMN utm_campaign VARCHAR(64);
