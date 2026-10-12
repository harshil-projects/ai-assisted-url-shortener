-- V2: optional link expiry (brownfield, task T-102); one single-column ALTER in the H2/PostgreSQL SQL intersection.
-- Nullable on purpose: rows created before this migration carry no expiry and must never expire (design 3.5).
ALTER TABLE links ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
