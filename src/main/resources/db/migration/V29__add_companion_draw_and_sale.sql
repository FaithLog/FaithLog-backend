CREATE TABLE companion_pool_versions (
 pool_version VARCHAR(80) PRIMARY KEY, name VARCHAR(100) NOT NULL,
 unit_cost BIGINT NOT NULL CHECK(unit_cost>0), active BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE companion_pool_entries (
 pool_version VARCHAR(80) NOT NULL REFERENCES companion_pool_versions(pool_version),
 design_id VARCHAR(100) NOT NULL REFERENCES companion_designs(design_id),weight INTEGER NOT NULL CHECK(weight>0),
 PRIMARY KEY(pool_version,design_id)
);
CREATE INDEX ix_companion_pool_design ON companion_pool_entries(design_id);
CREATE TABLE companion_sale_prices (
 rarity VARCHAR(16) PRIMARY KEY CHECK(rarity IN ('COMMON','UNCOMMON','RARE','EPIC','UNIQUE')),
 unit_price BIGINT NOT NULL CHECK(unit_price>0), policy_version VARCHAR(80) NOT NULL
);
CREATE TABLE companion_operations (
 operation_id UUID PRIMARY KEY,user_id BIGINT NOT NULL REFERENCES users(id),
 kind VARCHAR(8) NOT NULL CHECK(kind IN ('DRAW','SALE')),request_key VARCHAR(80) NOT NULL,
 request_fingerprint VARCHAR(255) NOT NULL,response_json TEXT NOT NULL,
 business_key VARCHAR(160) NOT NULL,pool_version VARCHAR(80) REFERENCES companion_pool_versions(pool_version),
 created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(user_id,kind,request_key),UNIQUE(user_id,business_key),
 FOREIGN KEY(user_id,business_key) REFERENCES point_transactions(user_id,business_key)
);
INSERT INTO companion_pool_versions VALUES('standard-v1','기본 동물 뽑기',100,true);
INSERT INTO companion_pool_entries(pool_version,design_id,weight)
SELECT 'standard-v1',design_id,CASE style_code WHEN 'base' THEN 240 WHEN 'sunshine' THEN 160 WHEN 'scholar' THEN 140 WHEN 'gardener' THEN 120 WHEN 'sailor' THEN 100 WHEN 'explorer' THEN 80 WHEN 'festival' THEN 65 WHEN 'starlight' THEN 45 WHEN 'royal' THEN 40 WHEN 'unique' THEN 10 END FROM companion_designs;
INSERT INTO companion_sale_prices VALUES('COMMON',10,'sale-v1'),('UNCOMMON',15,'sale-v1'),('RARE',25,'sale-v1'),('EPIC',40,'sale-v1'),('UNIQUE',50,'sale-v1');
ALTER TABLE companion_pool_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE companion_pool_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE companion_sale_prices ENABLE ROW LEVEL SECURITY;
ALTER TABLE companion_operations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON companion_pool_versions,companion_pool_entries,companion_sale_prices,companion_operations FROM PUBLIC;
DO $$ DECLARE role_name TEXT; BEGIN
 FOREACH role_name IN ARRAY ARRAY['anon','authenticated','service_role'] LOOP
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=role_name) THEN
 EXECUTE format('REVOKE ALL ON companion_pool_versions,companion_pool_entries,companion_sale_prices,companion_operations FROM %I',role_name);
 END IF; END LOOP;
END $$;
